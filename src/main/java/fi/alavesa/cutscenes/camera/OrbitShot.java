package fi.alavesa.cutscenes.camera;

import fi.alavesa.cutscenes.CutsceneRun;
import org.bukkit.World;
import org.joml.Vector3d;

/**
 * The camera circles a centre (an actor bone, or a fixed point) at {@code radius} and {@code height},
 * sweeping {@code degrees} over {@code duration} ticks starting at {@code startAngle}, always looking at
 * the centre. Negative degrees orbit the other way.
 */
public record OrbitShot(String actor, String bone, Vector3d point, World world, double radius, double height,
                        double startAngle, double degrees, int duration, Easing easing) implements CameraShot {

    @Override
    public Pose pose(CutsceneRun run, int localTick) {
        Vector3d c = actor != null ? run.pointOf(actor, bone) : point;
        if (c == null) return null;
        World w = actor != null ? run.actor(actor).location().getWorld() : world;
        double t = easing.apply(Math.min(1.0, localTick / (double) Math.max(1, duration)));
        double ang = Math.toRadians(startAngle + degrees * t);
        Vector3d pos = new Vector3d(c.x - Math.sin(ang) * radius, c.y + height, c.z + Math.cos(ang) * radius);
        return Pose.lookingAt(w, pos, c);
    }

    @Override public String describe() {
        return String.format("orbit %s r=%.1f h=%.1f %.0f°→%.0f° over %dt %s", actor != null ? actor + (bone == null ? "" : "/" + bone) : "point",
            radius, height, startAngle, startAngle + degrees, duration, easing.key());
    }
}
