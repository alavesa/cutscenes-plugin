package fi.alavesa.cutscenes;

import ai.resourcepack.engine.RPEnginePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Thin helpers over RP Engine. Our models are .bbmodel files in plugins/Cutscenes/models, mirrored into
 * plugins/RPEngine/content/cutscenes/models (RP Engine reads that layout: each file is the item "cutscenes:<name>").
 * Any other RP Engine model id ("mypack:golem") can be used by its full id as well.
 */
public final class Rpe {
    public static final String NAMESPACE = "cutscenes";
    private static File modelsDir;
    private static final Map<String, BbModel> parsed = new HashMap<>();

    private Rpe() { }

    public enum Loop { LOOP, ONCE, HOLD }

    public static boolean installed() {
        try { Class.forName("ai.resourcepack.engine.api.Models"); } catch (Throwable t) { return false; }
        return Bukkit.getPluginManager().getPlugin("RPEngine") != null;
    }

    static RPEnginePlugin engine() { return (RPEnginePlugin) Bukkit.getPluginManager().getPlugin("RPEngine"); }

    /** Mirror plugins/Cutscenes/models/*.bbmodel into RP Engine's content folder; reload RP Engine when anything changed. */
    public static int sync(File dir, java.util.logging.Logger log) {
        modelsDir = dir;
        parsed.clear();
        File rm = new File(new File(new File(engine().getDataFolder(), "content"), NAMESPACE), "models");
        rm.mkdirs();
        int changed = 0;
        Set<String> wanted = new HashSet<>();
        File[] src = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
        if (src != null) for (File f : src) {
            File target = new File(rm, key(f.getName().substring(0, f.getName().length() - 8)) + ".bbmodel");
            wanted.add(target.getName());
            try {
                byte[] data = Files.readAllBytes(f.toPath());
                if (!target.exists() || !Arrays.equals(data, Files.readAllBytes(target.toPath()))) { Files.write(target.toPath(), data); changed++; }
            } catch (Exception e) { log.warning("Could not copy " + f.getName() + " to RP Engine: " + e); }
        }
        File[] stale = rm.listFiles((d, n) -> n.endsWith(".bbmodel") && !wanted.contains(n));
        if (stale != null) for (File s : stale) if (s.delete()) changed++;
        if (changed > 0) {
            log.info("RP Engine: " + changed + " model file(s) changed in content/" + NAMESPACE + "/models - reloading RP Engine.");
            try { engine().reload(); } catch (Throwable t) { log.warning("RP Engine reload failed: " + t); }
        }
        return changed;
    }

    public static String key(String name) { return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }

    /** The RP Engine model id for a name typed by a player: "cutscenes:<name>" for our files, any full id as-is. */
    public static String model(String name) {
        if (name == null) return null;
        List<String> ids;
        try { ids = engine().models().ids(); } catch (Throwable t) { return null; }
        if (name.contains(":")) { for (String i : ids) if (i.equalsIgnoreCase(name)) return i; return null; }
        String ours = NAMESPACE + ":" + key(name);
        for (String i : ids) if (i.equalsIgnoreCase(ours)) return i;
        for (String i : ids) if (i.equalsIgnoreCase(name) || i.toLowerCase(Locale.ROOT).endsWith(":" + key(name))) return i;
        return null;
    }

    public static Set<String> modelNames() {
        Set<String> out = new TreeSet<>();
        try { for (String i : engine().models().ids()) out.add(i.startsWith(NAMESPACE + ":") ? i.substring(NAMESPACE.length() + 1) : i); } catch (Throwable ignored) { }
        return out;
    }

    public static Set<String> animationNames(String model) {
        String id = model(model);
        if (id == null) return Collections.emptySet();
        try { return new TreeSet<>(engine().models().animationsOf(id)); } catch (Throwable t) { return Collections.emptySet(); }
    }

    public static String animation(String model, String animation) {
        if (animation == null) return null;
        for (String k : animationNames(model)) if (k.equals(animation)) return k;
        for (String k : animationNames(model)) if (k.equalsIgnoreCase(animation) || k.replace(' ', '_').equalsIgnoreCase(animation)) return k;
        return null;
    }

    /** Clip length in ticks from our .bbmodel file, or -1 when unknown (a model from another pack). */
    public static int animationTicks(String model, String animation) {
        BbModel m = bb(model);
        if (m == null) return -1;
        BbModel.Clip c = m.clip(animation);
        return c == null ? -1 : (int) Math.max(1, Math.round(c.length * 20.0));
    }

    public static Loop loop(String s, Loop def) {
        if (s == null) return def;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "loop", "repeat" -> Loop.LOOP;
            case "once", "play_once", "play-once" -> Loop.ONCE;
            case "hold", "hold_on_last", "hold-on-last" -> Loop.HOLD;
            default -> def;
        };
    }

    public static String loopName(Loop t) {
        return switch (t) { case LOOP -> "loop"; case ONCE -> "once"; case HOLD -> "hold"; };
    }

    /** Our parsed .bbmodel for a model name (bone pivots, keyframes), or null when it is not one of our files. */
    public static BbModel bb(String model) {
        if (model == null || modelsDir == null) return null;
        String name = model.contains(":") ? (model.startsWith(NAMESPACE + ":") ? model.substring(NAMESPACE.length() + 1) : null) : key(model);
        if (name == null) return null;
        if (parsed.containsKey(name)) return parsed.get(name);
        BbModel m = null;
        File[] files = modelsDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel") && key(n.substring(0, n.length() - 8)).equals(name));
        if (files != null && files.length > 0) try { m = BbModel.parse(Files.readString(files[0].toPath())); } catch (Exception ignored) { }
        parsed.put(name, m);
        return m;
    }

    public static Location loc(Location l) { return l; }
}
