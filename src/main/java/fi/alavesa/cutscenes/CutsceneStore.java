package fi.alavesa.cutscenes;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/** plugins/Cutscenes/cutscenes/<name>.yml <-> {@link CutsceneDef}. */
public final class CutsceneStore {

    private final File dir;
    private final Logger log;
    private final Map<String, CutsceneDef> defs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public CutsceneStore(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "cutscenes");
        this.log = log;
        dir.mkdirs();
    }

    public void loadAll() {
        defs.clear();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                CutsceneDef def = load(f);
                defs.put(def.name, def);
            } catch (Exception e) {
                log.warning("Could not load cutscene " + f.getName() + ": " + e);
            }
        }
        log.info("Loaded " + defs.size() + " cutscene(s).");
    }

    private CutsceneDef load(File f) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String name = f.getName().substring(0, f.getName().length() - 4);
        CutsceneDef def = new CutsceneDef(name, y.getString("world", "world"));
        def.length = y.getInt("length", -1);
        if (y.contains("actors-visible-to-all")) def.actorsVisibleToAll = y.getBoolean("actors-visible-to-all");
        ConfigurationSection actors = y.getConfigurationSection("actors");
        if (actors != null) for (String id : actors.getKeys(false)) {
            ConfigurationSection s = actors.getConfigurationSection(id);
            if (s != null) def.actors.put(id, ActorDef.load(id, s));
        }
        for (Map<?, ?> m : y.getMapList("timeline")) def.events.add(EventDef.fromMap(m));
        def.onEnd.addAll(y.getStringList("on-end"));
        return def;
    }

    public void save(CutsceneDef def) {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
            "Cutscene '" + def.name + "'. Edit by hand or with /cutscene ... (reload with /cutscene reload).",
            "timeline: list of events, each with at: <tick> (20 ticks = 1 s) and type:",
            "  camera   camera: static|bone|path|orbit|follow  (+ transition: <ticks> to blend from the previous shot)",
            "  animate  actor, animation, loop: loop|once|hold, speed, then: <animation> [then-loop]",
            "  stop     actor",
            "  move     actor, x y z yaw pitch, duration",
            "  sound    sound, volume, pitch, [actor]",
            "  title    title, subtitle, fade-in, stay, fade-out",
            "  command  command (console; %player% = each viewer)"));
        y.set("world", def.world);
        y.set("length", def.length);
        y.set("actors-visible-to-all", def.actorsVisibleToAll);
        for (ActorDef a : def.actors.values()) a.save(y.createSection("actors." + a.id));
        List<Map<String, Object>> tl = new ArrayList<>();
        for (EventDef e : def.events) tl.add(e.toMap());
        y.set("timeline", tl);
        y.set("on-end", def.onEnd.isEmpty() ? null : def.onEnd);
        try {
            y.save(new File(dir, def.name + ".yml"));
        } catch (Exception e) {
            log.warning("Could not save cutscene " + def.name + ": " + e);
        }
        defs.put(def.name, def);
    }

    public boolean delete(String name) {
        CutsceneDef def = defs.remove(name);
        if (def == null) return false;
        new File(dir, def.name + ".yml").delete();
        return true;
    }

    public CutsceneDef get(String name) { return name == null ? null : defs.get(name); }

    public Map<String, CutsceneDef> all() { return defs; }
}
