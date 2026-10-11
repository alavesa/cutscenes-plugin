package fi.alavesa.cutscenes;

import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bone.BonePosition;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.DummyTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * One Blockbench model standing in the world: a BetterModel {@link DummyTracker} (no base entity) plus
 * our own bookkeeping of where it is, which animation it plays and who can see it.
 *
 * BetterModel does not auto-show dummy trackers - every player has to be spawned in explicitly with
 * {@link #showTo(Player)}; the owner (world model registry or a cutscene run) decides who sees what.
 */
public final class ModelActor {

    private final Plugin plugin;
    public final String id;
    public final String model;
    private Location location;
    private float scale;
    private DummyTracker tracker;

    private String currentAnimation;
    private AnimationIterator.Type currentLoop = AnimationIterator.Type.LOOP;
    private float currentSpeed = 1f;
    private int generation;

    public ModelActor(Plugin plugin, String id, String model, Location location, float scale) {
        this.plugin = plugin;
        this.id = id;
        this.model = model;
        this.location = location.clone();
        this.scale = scale <= 0 ? 1f : scale;
    }

    /** Create the BetterModel tracker. False when the model is unknown to BetterModel. */
    public boolean spawn() {
        ModelRenderer renderer = Bm.model(model);
        if (renderer == null) return false;
        close();
        tracker = renderer.create(Bm.loc(location));
        if (scale != 1f) tracker.scaler(ModelScaler.value(scale));
        return true;
    }

    public boolean isSpawned() { return tracker != null && !tracker.isClosed(); }

    public DummyTracker tracker() { return tracker; }

    public Location location() { return location.clone(); }

    public float scale() { return scale; }

    public void scale(float s) {
        scale = s <= 0 ? 1f : s;
        if (isSpawned()) tracker.scaler(scale == 1f ? ModelScaler.defaultScaler() : ModelScaler.value(scale));
    }

    public String currentAnimation() { return currentAnimation; }

    public void showTo(Player p) {
        if (!isSpawned() || p == null || !p.isOnline()) return;
        var pp = Bm.player(p);
        if (!tracker.isSpawned(pp)) tracker.spawn(pp);
    }

    public void hideFrom(Player p) {
        if (!isSpawned() || p == null) return;
        var pp = Bm.player(p);
        if (tracker.isSpawned(pp)) tracker.remove(pp);
    }

    public boolean isShownTo(Player p) {
        return isSpawned() && tracker.isSpawned(Bm.player(p));
    }

    /** Move (and turn) the model. yaw/pitch of the location rotate the whole model. */
    public void moveTo(Location l) {
        location = l.clone();
        if (isSpawned()) tracker.location(Bm.loc(location));
    }

    /**
     * Play an animation now, replacing whatever was playing (so a change mid-animation is a hard switch
     * to the new clip). {@code onEnd} runs on the main thread when the clip finishes on its own -
     * only PLAY_ONCE clips finish; it is NOT called when the clip is replaced or stopped by us.
     */
    public boolean animate(String animation, AnimationIterator.Type loop, float speed, Runnable onEnd) {
        if (!isSpawned()) return false;
        String real = Bm.animation(model, animation);
        if (real == null) return false;
        stopAnimation();
        final int gen = ++generation;
        AnimationModifier mod = AnimationModifier.DEFAULT.toBuilder()
            .type(loop)
            .speed(speed <= 0 ? 1f : speed)
            .build();
        Runnable cb = onEnd == null ? null : () -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (gen != generation) return;      // we replaced/stopped it ourselves
            if (real.equals(currentAnimation)) currentAnimation = null;
            onEnd.run();
        });
        boolean ok = cb == null ? tracker.animate(real, mod) : tracker.animate(real, mod, cb);
        if (ok) {
            currentAnimation = real;
            currentLoop = loop;
            currentSpeed = speed <= 0 ? 1f : speed;
        }
        return ok;
    }

    public void stopAnimation() {
        generation++;
        if (isSpawned() && currentAnimation != null) tracker.stopAnimation(currentAnimation);
        currentAnimation = null;
    }

    /** Re-create the tracker (after /bettermodel reload) and resume the current animation. */
    public void respawn(List<Player> viewers) {
        String anim = currentAnimation;
        AnimationIterator.Type loop = currentLoop;
        float speed = currentSpeed;
        if (!spawn()) return;
        for (Player p : viewers) showTo(p);
        if (anim != null) animate(anim, loop, speed, null);
    }

    public RenderedBone bone(String name) {
        if (!isSpawned() || name == null) return null;
        RenderedBone b = tracker.bone(name);
        if (b != null) return b;
        return tracker.bone(rb -> rb.name().name().equalsIgnoreCase(name));
    }

    public List<String> boneNames() {
        List<String> out = new ArrayList<>();
        if (!isSpawned()) return out;
        for (RenderedBone b : tracker.bones()) out.add(b.name().name());
        return out;
    }

    /**
     * Absolute world position of a bone's pivot (animated), or null.
     * BetterModel only computes bone transforms while the tracker ticks for at least one viewer; before
     * anybody sees the model the rest pose is used instead (the group's pivot, scaled and turned by the
     * model's yaw/pitch exactly the way BetterModel does it).
     */
    public Vector3d bonePosition(String bone) {
        RenderedBone b = bone(bone);
        if (b == null) return null;
        Vector3f v = ticking() ? b.worldPosition() : restPosition(b);
        return new Vector3d(location.getX() + v.x, location.getY() + v.y, location.getZ() + v.z);
    }

    private boolean ticking() {
        try { return tracker.playerCount() > 0; } catch (Throwable t) { return true; }
    }

    private Vector3f restPosition(RenderedBone b) {
        Vector3f v = new Vector3f(b.getGroup().getPosition()).mul(scale);
        return v.rotateX((float) Math.toRadians(location.getPitch())).rotateY((float) Math.toRadians(location.getYaw()));
    }

    /** Unit vector of a bone's local axis in world space (animated), or null (also null before the model ticks). */
    public Vector3d boneAxis(String bone, Vector3f localAxis) {
        RenderedBone b = bone(bone);
        if (b == null || !ticking()) return null;
        Vector3f origin = b.worldPosition();
        Vector3f tip = b.worldPosition(new BonePosition(new Vector3f(), new Vector3f(localAxis), null));
        Vector3d d = new Vector3d(tip.x - origin.x, tip.y - origin.y, tip.z - origin.z);
        if (d.lengthSquared() < 1e-9) return null;
        return d.normalize();
    }

    public void close() {
        if (tracker != null) {
            try { if (!tracker.isClosed()) tracker.close(); } catch (Throwable ignored) { }
            tracker = null;
        }
    }
}
