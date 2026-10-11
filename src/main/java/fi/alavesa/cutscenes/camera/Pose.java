package fi.alavesa.cutscenes.camera;

import org.bukkit.Location;
import org.bukkit.World;
import org.joml.Vector3d;

/** A camera position + orientation. Yaw/pitch follow Minecraft conventions (yaw 0 = south, pitch -90 = up). */
public record Pose(World world, double x, double y, double z, float yaw, float pitch) {

    public static Pose of(Location l) {
        return new Pose(l.getWorld(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation() {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public Vector3d position() { return new Vector3d(x, y, z); }

    public Pose withPosition(Vector3d p) { return new Pose(world, p.x, p.y, p.z, yaw, pitch); }

    public Pose withRotation(float yaw, float pitch) { return new Pose(world, x, y, z, yaw, pitch); }

    /** Pose at {@code from} looking towards {@code to}. */
    public static Pose lookingAt(World w, Vector3d from, Vector3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float[] r = rotationOf(dx, dy, dz);
        return new Pose(w, from.x, from.y, from.z, r[0], r[1]);
    }

    public Pose lookAt(Vector3d target) {
        return lookingAt(world, position(), target);
    }

    /** {yaw, pitch} for a direction vector. */
    public static float[] rotationOf(double dx, double dy, double dz) {
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1e-6 && Math.abs(dy) < 1e-6) return new float[] {0f, 0f};
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[] {wrap(yaw), Math.max(-90f, Math.min(90f, pitch))};
    }

    public static float wrap(float yaw) {
        yaw %= 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        return yaw;
    }

    /** Shortest-arc interpolation of angles and linear interpolation of position. */
    public static Pose lerp(Pose a, Pose b, double t) {
        if (a == null) return b;
        if (b == null) return a;
        t = Math.max(0, Math.min(1, t));
        double x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t, z = a.z + (b.z - a.z) * t;
        float dyaw = wrap(b.yaw - a.yaw);
        float yaw = wrap((float) (a.yaw + dyaw * t));
        float pitch = (float) (a.pitch + (b.pitch - a.pitch) * t);
        return new Pose(b.world, x, y, z, yaw, pitch);
    }
}
