package fi.alavesa.cutscenes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Models spawned "for real" into the world with /cutscene model spawn: persistent (models.yml), shown
 * to every player within models.spawn-radius, re-created after /bettermodel reload.
 */
public final class WorldModels {

    private final CutscenesPlugin plugin;
    private final File file;
    private final Map<String, ModelActor> actors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String[]> anims = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // id -> {animation, loop, speed}

    public WorldModels(CutscenesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "models.yml");
    }

    public void load() {
        closeAll();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) { plugin.getLogger().warning("models.yml: world of '" + id + "' not loaded, skipped"); continue; }
            Location l = new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            ModelActor a = new ModelActor(plugin, id, s.getString("model", id), l, (float) s.getDouble("scale", 1.0));
            if (!a.spawn()) { plugin.getLogger().warning("models.yml: RP Engine has no model '" + a.model + "' for '" + id + "' (plugins/Cutscenes/models/, /cutscene reload)"); }
            actors.put(id, a);
            String anim = s.getString("animation", null);
            if (anim != null) {
                anims.put(id, new String[] {anim, s.getString("loop", "loop"), String.valueOf(s.getDouble("speed", 1.0))});
                a.animate(anim, Rpe.loop(s.getString("loop", "loop"), Rpe.Loop.LOOP), (float) s.getDouble("speed", 1.0), null);
            }
        }
        plugin.getLogger().info("Loaded " + actors.size() + " world model(s).");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (ModelActor a : actors.values()) {
            ConfigurationSection s = y.createSection(a.id);
            Location l = a.location();
            s.set("model", a.model);
            s.set("world", l.getWorld().getName());
            s.set("x", l.getX()); s.set("y", l.getY()); s.set("z", l.getZ());
            s.set("yaw", (double) l.getYaw()); s.set("pitch", (double) l.getPitch());
            s.set("scale", a.scale() == 1f ? null : (double) a.scale());
            String[] an = anims.get(a.id);
            if (an != null) { s.set("animation", an[0]); s.set("loop", an[1]); s.set("speed", Double.parseDouble(an[2])); }
        }
        try { y.save(file); } catch (Exception e) { plugin.getLogger().warning("Could not save models.yml: " + e); }
    }

    public String freeId(String model) {
        String base = model.toLowerCase().replaceAll("[^a-z0-9_]", "");
        for (int i = 1; ; i++) {
            String id = base + "_" + i;
            if (!actors.containsKey(id)) return id;
        }
    }

    public ModelActor spawn(String id, String model, Location l, float scale) {
        ModelActor a = new ModelActor(plugin, id, model, l, scale);
        if (!a.spawn()) return null;
        actors.put(id, a);
        save();
        return a;
    }

    public boolean remove(String id) {
        ModelActor a = actors.remove(id);
        anims.remove(id);
        if (a == null) return false;
        a.close();
        save();
        return true;
    }

    public int removeAll() {
        int n = actors.size();
        closeAll();
        save();
        return n;
    }

    public boolean animate(String id, String animation, String loop, float speed) {
        ModelActor a = actors.get(id);
        if (a == null) return false;
        if (animation == null) { a.stopAnimation(); anims.remove(id); save(); return true; }
        boolean ok = a.animate(animation, Rpe.loop(loop, Rpe.Loop.LOOP), speed, null);
        if (ok) { anims.put(id, new String[] {a.currentAnimation(), Rpe.loopName(Rpe.loop(loop, Rpe.Loop.LOOP)), String.valueOf(speed)}); save(); }
        return ok;
    }

    public ModelActor get(String id) { return actors.get(id); }

    public Collection<ModelActor> all() { return actors.values(); }

    public ModelActor nearest(Location l, double max) {
        ModelActor best = null; double bd = max * max;
        for (ModelActor a : actors.values()) {
            Location al = a.location();
            if (!al.getWorld().equals(l.getWorld())) continue;
            double d = al.distanceSquared(l);
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    /** Show/hide every world model per player by distance. */
    public void tick() {
        if (actors.isEmpty()) return;
        double r2 = plugin.settings().spawnRadius * plugin.settings().spawnRadius;
        for (ModelActor a : actors.values()) {
            if (!a.isSpawned()) continue;
            Location al = a.location();
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean near = p.getWorld().equals(al.getWorld()) && p.getLocation().distanceSquared(al) <= r2;
                if (near) a.showTo(p); else if (a.isShownTo(p)) a.hideFrom(p);
            }
        }
    }

    public void playerQuit(Player p) {
        for (ModelActor a : actors.values()) a.hideFrom(p);
    }

    /** After /bettermodel reload every renderer is new: rebuild all trackers. */
    public void respawnAll() {
        List<Player> none = new ArrayList<>();
        for (ModelActor a : actors.values()) a.respawn(none);
    }

    public void closeAll() {
        for (ModelActor a : actors.values()) a.close();
        actors.clear();
        anims.clear();
    }
}
