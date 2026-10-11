package fi.alavesa.cutscenes;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.UUID;

/**
 * What a viewer was doing before a cutscene grabbed them, persisted to restore.yml so that a crash or
 * a /stop mid-cutscene never strands anyone in spectator mode: the state is re-applied on their next join.
 */
public final class ViewerState {

    private final File file;
    private final YamlConfiguration yaml;

    public ViewerState(File dataFolder) {
        file = new File(dataFolder, "restore.yml");
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save(Player p) {
        String k = p.getUniqueId().toString();
        if (yaml.contains(k)) return;        // already inside a cutscene; keep the original state
        Location l = p.getLocation();
        ConfigurationSection s = yaml.createSection(k);
        s.set("world", l.getWorld().getName());
        s.set("x", l.getX()); s.set("y", l.getY()); s.set("z", l.getZ());
        s.set("yaw", (double) l.getYaw()); s.set("pitch", (double) l.getPitch());
        s.set("gamemode", p.getGameMode().name());
        s.set("allow-flight", p.getAllowFlight());
        s.set("flying", p.isFlying());
        write();
    }

    public boolean has(UUID id) { return yaml.contains(id.toString()); }

    /** Put the player back where they were. True if there was a saved state. */
    public boolean restore(Player p) {
        String k = p.getUniqueId().toString();
        ConfigurationSection s = yaml.getConfigurationSection(k);
        if (s == null) return false;
        try { p.setSpectatorTarget(null); } catch (Throwable ignored) { }
        GameMode gm;
        try { gm = GameMode.valueOf(s.getString("gamemode", "SURVIVAL")); } catch (IllegalArgumentException e) { gm = GameMode.SURVIVAL; }
        p.setGameMode(gm);
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w != null) {
            p.teleport(new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
        boolean allow = s.getBoolean("allow-flight", gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR);
        p.setAllowFlight(allow);
        if (allow) p.setFlying(s.getBoolean("flying", false));
        yaml.set(k, null);
        write();
        return true;
    }

    private void write() {
        try { yaml.save(file); } catch (Exception ignored) { }
    }
}
