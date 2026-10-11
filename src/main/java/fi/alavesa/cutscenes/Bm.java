package fi.alavesa.cutscenes;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.platform.PlatformPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Thin helpers over the BetterModel API. Models are the ones BetterModel itself loaded from
 * plugins/BetterModel/models (the same names {@code /bettermodel spawn} accepts); BetterModel serves the
 * resource pack, this plugin only drives trackers and animations.
 */
public final class Bm {

    private Bm() { }

    public static ModelRenderer model(String name) {
        if (name == null) return null;
        ModelRenderer r = BetterModel.modelOrNull(name);
        if (r != null) return r;
        for (String k : BetterModel.modelKeys()) {
            if (k.equalsIgnoreCase(name)) return BetterModel.modelOrNull(k);
        }
        return null;
    }

    public static Set<String> modelNames() {
        return new TreeSet<>(BetterModel.modelKeys());
    }

    public static Set<String> animationNames(String model) {
        ModelRenderer r = model(model);
        if (r == null) return Collections.emptySet();
        return new TreeSet<>(r.animations().keySet());
    }

    /** Real animation name (case-insensitive lookup), or null if the model has no such animation. */
    public static String animation(String model, String animation) {
        ModelRenderer r = model(model);
        if (r == null || animation == null) return null;
        if (r.animations().containsKey(animation)) return animation;
        for (String k : r.animations().keySet()) if (k.equalsIgnoreCase(animation)) return k;
        return null;
    }

    /** Length of an animation in ticks (20/s), or -1 when unknown. */
    public static int animationTicks(String model, String animation) {
        ModelRenderer r = model(model);
        if (r == null) return -1;
        var a = r.animations().get(animation);
        return a == null ? -1 : Math.round(a.length() * 20f);
    }

    public static AnimationIterator.Type loop(String s, AnimationIterator.Type def) {
        if (s == null) return def;
        return switch (s.toLowerCase()) {
            case "loop", "repeat" -> AnimationIterator.Type.LOOP;
            case "once", "play_once", "play-once" -> AnimationIterator.Type.PLAY_ONCE;
            case "hold", "hold_on_last", "hold-on-last" -> AnimationIterator.Type.HOLD_ON_LAST;
            default -> def;
        };
    }

    public static String loopName(AnimationIterator.Type t) {
        return switch (t) {
            case LOOP -> "loop";
            case PLAY_ONCE -> "once";
            case HOLD_ON_LAST -> "hold";
        };
    }

    public static PlatformLocation loc(Location l) { return BukkitAdapter.adapt(l); }

    public static PlatformPlayer player(Player p) { return BukkitAdapter.adapt(p); }
}
