package fi.alavesa.cutscenes.camera;

import fi.alavesa.cutscenes.CutsceneRun;
import org.bukkit.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The camera travels through waypoints over {@code duration} ticks, then holds the last one.
 * Orientation: the look-at target when set, otherwise yaw/pitch interpolated between the waypoints.
 */
public final class PathShot implements CameraShot {

    public final List<Pose> points;
    public final int duration;
    public final Easing easing;
    public final boolean smooth;        // catmull-rom vs linear
    public final String lookActor, lookBone;

    public PathShot(List<Pose> points, int duration, Easing easing, boolean smooth, String lookActor, String lookBone) {
        this.points = new ArrayList<>(points);
        this.duration = Math.max(1, duration);
        this.easing = easing;
        this.smooth = smooth;
        this.lookActor = lookActor;
        this.lookBone = lookBone;
    }

    @Override
    public Pose pose(CutsceneRun run, int localTick) {
        if (points.isEmpty()) return null;
        double t = easing.apply(Math.min(1.0, localTick / (double) duration));
        World w = points.get(0).world();
        List<Vector3d> pts = new ArrayList<>(points.size());
        for (Pose p : points) pts.add(p.position());
        Vector3d pos = smooth ? Spline.catmullRom(pts, t) : Spline.linear(pts, t);
        Vector3d target = lookActor == null ? null : run.pointOf(lookActor, lookBone);
        if (target != null) return Pose.lookingAt(w, pos, target);
        // interpolate rotation between the surrounding waypoints
        if (points.size() == 1) return points.get(0).withPosition(pos);
        double scaled = t * (points.size() - 1);
        int i = Math.min((int) Math.floor(scaled), points.size() - 2);
        Pose r = Pose.lerp(points.get(i), points.get(i + 1), scaled - i);
        return new Pose(w, pos.x, pos.y, pos.z, r.yaw(), r.pitch());
    }

    @Override public String describe() {
        return "path " + points.size() + " pts, " + duration + "t, " + easing.key() + (smooth ? ", smooth" : ", linear")
            + (lookActor == null ? "" : ", look-at " + lookActor + (lookBone == null ? "" : "/" + lookBone));
    }
}
