package fi.alavesa.cutscenes;

import org.bukkit.configuration.file.FileConfiguration;
import org.joml.Vector3f;

/** Values from config.yml. */
public final class Settings {
    public final String cameraBone;
    public final String targetBone;
    public final Vector3f forwardAxis;
    public final int teleportDuration;
    public final double spawnRadius;
    public final boolean actorsVisibleToAll;
    public final int cameraLockDelay;
    public final int defaultTail;

    Settings(FileConfiguration c) {
        cameraBone = c.getString("camera.bone-name", "camera");
        targetBone = c.getString("camera.target-bone", "camera_target");
        forwardAxis = axis(c.getString("camera.forward-axis", "+z"));
        teleportDuration = Math.max(0, c.getInt("camera.teleport-duration", 1));
        spawnRadius = Math.max(8, c.getDouble("models.spawn-radius", 64));
        actorsVisibleToAll = c.getBoolean("cutscenes.actors-visible-to-all", false);
        cameraLockDelay = Math.max(1, c.getInt("cutscenes.camera-lock-delay", 2));
        defaultTail = Math.max(0, c.getInt("cutscenes.default-tail", 20));
    }

    static Vector3f axis(String s) {
        if (s == null) return new Vector3f(0, 0, 1);
        s = s.trim().toLowerCase();
        float sign = s.startsWith("-") ? -1f : 1f;
        char ax = s.charAt(s.length() - 1);
        return switch (ax) {
            case 'x' -> new Vector3f(sign, 0, 0);
            case 'y' -> new Vector3f(0, sign, 0);
            default -> new Vector3f(0, 0, sign);
        };
    }
}
