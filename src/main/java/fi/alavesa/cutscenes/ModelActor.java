package fi.alavesa.cutscenes;

import ai.resourcepack.engine.api.ContentId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * One Blockbench model standing in the world: an invisible armor stand ("host") that WEARS an RP Engine model
 * (Models.bind), moved and turned by teleporting the host. RP Engine draws it for every player in range (there is
 * no per-viewer visibility, so showTo/hideFrom are no-ops). Animations play through Models.animate - loop/once
 * comes from the clip's own Blockbench setting and speed is always 1; our loop/speed arguments only drive the
 * {@code onEnd} timing. Bone positions for the camera are sampled from our own copy of the .bbmodel.
 */
public final class ModelActor {

    private final Plugin plugin;
    public final String id;
    public final String model;
    private Location location;
    private float scale;
    private ArmorStand host;
    private String modelId;

    private String currentAnimation;
    private Rpe.Loop currentLoop = Rpe.Loop.LOOP;
    private float currentSpeed = 1f;
    private long animStartTick;
    private int generation;

    public ModelActor(Plugin plugin, String id, String model, Location location, float scale) {
        this.plugin = plugin;
        this.id = id;
        this.model = model;
        this.location = location.clone();
        this.scale = scale <= 0 ? 1f : scale;
    }

    /** Spawn the host and bind the model. False when RP Engine has no such model. */
    public boolean spawn() {
        modelId = Rpe.model(model);
        if (modelId == null) return false;
        close();
        host = location.getWorld().spawn(hostLocation(), ArmorStand.class, a -> {
            a.setInvisible(true); a.setSilent(true); a.setGravity(false); a.setInvulnerable(true); a.setMarker(false);
            a.setBasePlate(false); a.setArms(false); a.setPersistent(false); a.setCollidable(false);
            a.addScoreboardTag("cutscenes_actor");
        });
        var cid = ContentId.parse(modelId);
        if (cid.isEmpty() || !Rpe.engine().models().bind(host, cid.get(), scale)) { host.remove(); host = null; return false; }
        return true;
    }

    private Location hostLocation() { Location l = location.clone(); l.setPitch(0); return l; }

    public boolean isSpawned() { return host != null && host.isValid(); }
    public Location location() { return location.clone(); }
    public float scale() { return scale; }
    public void scale(float s) { scale = s <= 0 ? 1f : s; if (isSpawned()) { var cid = ContentId.parse(modelId); cid.ifPresent(c -> Rpe.engine().models().bind(host, c, scale)); } }
    public String currentAnimation() { return currentAnimation; }

    public void showTo(Player p) { }
    public void hideFrom(Player p) { }
    public boolean isShownTo(Player p) { return isSpawned(); }

    /** Move (and turn) the model. The yaw turns the whole model; pitch cannot be shown on a worn model. */
    public void moveTo(Location l) {
        location = l.clone();
        if (isSpawned()) host.teleport(hostLocation());
    }

    /** Play an animation now. onEnd runs on the main thread when a ONCE clip reaches its end (by our clock). */
    public boolean animate(String animation, Rpe.Loop loop, float speed, Runnable onEnd) {
        if (!isSpawned()) return false;
        String real = Rpe.animation(model, animation);
        if (real == null) return false;
        stopAnimation();
        final int gen = ++generation;
        if (!Rpe.engine().models().animate(host, real)) return false;
        currentAnimation = real; currentLoop = loop; currentSpeed = speed <= 0 ? 1f : speed; animStartTick = Bukkit.getCurrentTick();
        if (onEnd != null && loop == Rpe.Loop.ONCE) {
            int ticks = Rpe.animationTicks(model, real);
            if (ticks > 0) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (gen != generation) return;
                if (real.equals(currentAnimation)) currentAnimation = null;
                onEnd.run();
            }, Math.max(1, Math.round(ticks / currentSpeed)));
        }
        return true;
    }

    public void stopAnimation() {
        generation++;
        if (isSpawned() && currentAnimation != null) Rpe.engine().models().stopAnimating(host);
        currentAnimation = null;
    }

    public void respawn(List<Player> viewers) {
        String anim = currentAnimation; Rpe.Loop loop = currentLoop; float speed = currentSpeed;
        if (!spawn()) return;
        if (anim != null) animate(anim, loop, speed, null);
    }

    public List<String> boneNames() { BbModel m = Rpe.bb(model); return m == null ? List.of() : m.boneNames(); }

    /** Model height in blocks (from our .bbmodel), scaled. */
    public double height() { BbModel m = Rpe.bb(model); return (m == null ? 2.0 : m.height()) * scale; }

    /** Seconds into the current clip, honouring loop / once / hold. */
    private double clipTime(BbModel m) {
        if (currentAnimation == null) return -1;
        BbModel.Clip c = m.clip(currentAnimation);
        if (c == null || c.length <= 0) return -1;
        double t = (Bukkit.getCurrentTick() - animStartTick) / 20.0 * currentSpeed;
        boolean loops = currentLoop == Rpe.Loop.LOOP || (currentLoop != Rpe.Loop.ONCE && currentLoop != Rpe.Loop.HOLD && "loop".equalsIgnoreCase(c.loop));
        return loops ? t % c.length : Math.min(t, c.length);
    }

    /** Model-space (blocks, Blockbench axes) -> world: scale, then the location's pitch/yaw, then the position. */
    private Vector3d toWorld(double[] p) {
        Vector3f v = new Vector3f((float) (p[0] / 16.0), (float) (p[1] / 16.0), (float) (p[2] / 16.0)).mul(scale);
        v.rotateX((float) Math.toRadians(location.getPitch())).rotateY((float) Math.toRadians(-location.getYaw()));
        return new Vector3d(location.getX() + v.x, location.getY() + v.y, location.getZ() + v.z);
    }

    /** Absolute world position of a bone's pivot (animated by our own sampling of the clip), or null. */
    public Vector3d bonePosition(String bone) {
        BbModel m = Rpe.bb(model);
        if (m == null) return null;
        BbModel.Bone b = m.bone(bone);
        if (b == null) return null;
        double t = clipTime(m);
        return toWorld(m.pivotAt(b, t < 0 ? null : m.clip(currentAnimation), Math.max(0, t)));
    }

    /** Unit vector of a bone's local axis in world space, or null. */
    public Vector3d boneAxis(String bone, Vector3f localAxis) {
        BbModel m = Rpe.bb(model);
        if (m == null) return null;
        BbModel.Bone b = m.bone(bone);
        if (b == null) return null;
        double t = clipTime(m);
        double[] a = m.axisAt(b, t < 0 ? null : m.clip(currentAnimation), Math.max(0, t), new double[]{localAxis.x * 16, localAxis.y * 16, localAxis.z * 16});
        if (a == null) return null;
        Vector3f v = new Vector3f((float) a[0], (float) a[1], (float) a[2]);
        v.rotateX((float) Math.toRadians(location.getPitch())).rotateY((float) Math.toRadians(-location.getYaw()));
        Vector3d d = new Vector3d(v.x, v.y, v.z);
        return d.lengthSquared() < 1e-9 ? null : d.normalize();
    }

    public void close() {
        if (host != null) {
            try { if (host.isValid()) { Rpe.engine().models().unbind(host); host.remove(); } } catch (Throwable ignored) { }
            host = null;
        }
        currentAnimation = null;
    }
}
