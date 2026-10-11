package fi.alavesa.cutscenes.camera;

import fi.alavesa.cutscenes.CutsceneRun;
import fi.alavesa.cutscenes.ModelActor;
import org.joml.Vector3d;

/**
 * Chase camera: sits {@code back} blocks behind, {@code up} above and {@code right} beside an actor
 * (relative to the actor's yaw) and looks at it (or one of its bones).
 */
public record FollowShot(String actor, String bone, double back, double up, double right) implements CameraShot {

    @Override
    public Pose pose(CutsceneRun run, int localTick) {
        ModelActor a = run.actor(actor);
        if (a == null) return null;
        Vector3d target = run.pointOf(actor, bone);
        if (target == null) return null;
        var l = a.location();
        double yaw = Math.toRadians(l.getYaw());
        // facing vector for a yaw: (-sin, 0, cos); right-hand vector: (cos, 0, sin)
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = Math.cos(yaw), rz = Math.sin(yaw);
        Vector3d pos = new Vector3d(
            target.x - fx * back + rx * right,
            target.y + up,
            target.z - fz * back + rz * right);
        return Pose.lookingAt(l.getWorld(), pos, target);
    }

    @Override public String describe() {
        return String.format("follow %s%s back=%.1f up=%.1f right=%.1f", actor, bone == null ? "" : "/" + bone, back, up, right);
    }
}
