package fi.alavesa.cutscenes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** A model placed in a cutscene: where it stands when the cutscene starts and what it plays first. */
public final class ActorDef {
    public final String id;
    public String model;
    public String world;
    public double x, y, z;
    public float yaw, pitch;
    public String animation;        // may be null
    public String loop = "loop";
    public float speed = 1f;
    public float scale = 1f;

    public ActorDef(String id, String model, Location l) {
        this.id = id;
        this.model = model;
        setLocation(l);
    }

    public void setLocation(Location l) {
        world = l.getWorld().getName();
        x = l.getX(); y = l.getY(); z = l.getZ(); yaw = l.getYaw(); pitch = l.getPitch();
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    public static ActorDef load(String id, ConfigurationSection s) {
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) w = Bukkit.getWorlds().get(0);
        ActorDef a = new ActorDef(id, s.getString("model", id),
            new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        a.world = s.getString("world", w.getName());
        a.animation = s.getString("animation", null);
        a.loop = s.getString("loop", "loop");
        a.speed = (float) s.getDouble("speed", 1.0);
        a.scale = (float) s.getDouble("scale", 1.0);
        return a;
    }

    public void save(ConfigurationSection s) {
        s.set("model", model);
        s.set("world", world);
        s.set("x", x); s.set("y", y); s.set("z", z);
        s.set("yaw", (double) yaw); s.set("pitch", (double) pitch);
        s.set("animation", animation);
        s.set("loop", loop);
        s.set("speed", speed == 1f ? null : (double) speed);
        s.set("scale", scale == 1f ? null : (double) scale);
    }
}
