package fi.alavesa.cutscenes.camera;

import org.joml.Vector3d;

import java.util.List;

/** Position curves through a list of waypoints, parameterised 0..1 over the whole path. */
public final class Spline {

    private Spline() { }

    /** Piecewise-linear, each segment taking time proportional to its length. */
    public static Vector3d linear(List<Vector3d> pts, double t) {
        if (pts.size() == 1) return new Vector3d(pts.get(0));
        double total = 0;
        double[] seg = new double[pts.size() - 1];
        for (int i = 0; i < seg.length; i++) { seg[i] = pts.get(i).distance(pts.get(i + 1)); total += seg[i]; }
        if (total < 1e-9) return new Vector3d(pts.get(0));
        double d = t * total;
        for (int i = 0; i < seg.length; i++) {
            if (d <= seg[i] || i == seg.length - 1) {
                double u = seg[i] < 1e-9 ? 0 : Math.max(0, Math.min(1, d / seg[i]));
                return new Vector3d(pts.get(i)).lerp(pts.get(i + 1), u);
            }
            d -= seg[i];
        }
        return new Vector3d(pts.get(pts.size() - 1));
    }

    /** Catmull-Rom through every waypoint (uniform parameterisation, clamped end tangents). */
    public static Vector3d catmullRom(List<Vector3d> pts, double t) {
        int n = pts.size();
        if (n == 1) return new Vector3d(pts.get(0));
        if (n == 2) return new Vector3d(pts.get(0)).lerp(pts.get(1), t);
        double scaled = Math.max(0, Math.min(1, t)) * (n - 1);
        int i = Math.min((int) Math.floor(scaled), n - 2);
        double u = scaled - i;
        Vector3d p0 = pts.get(Math.max(i - 1, 0)), p1 = pts.get(i), p2 = pts.get(i + 1), p3 = pts.get(Math.min(i + 2, n - 1));
        double u2 = u * u, u3 = u2 * u;
        return new Vector3d(
            cr(p0.x, p1.x, p2.x, p3.x, u, u2, u3),
            cr(p0.y, p1.y, p2.y, p3.y, u, u2, u3),
            cr(p0.z, p1.z, p2.z, p3.z, u, u2, u3));
    }

    private static double cr(double a, double b, double c, double d, double u, double u2, double u3) {
        return 0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
    }
}
