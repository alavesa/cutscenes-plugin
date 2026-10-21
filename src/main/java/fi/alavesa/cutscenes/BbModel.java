package fi.alavesa.cutscenes;

import com.google.gson.*;

import java.util.*;

/**
 * Just enough of a Blockbench file to know where a bone is at a moment of an animation: the group tree with pivots
 * and rest rotations, each animation's rotation/position keyframes per bone (linear), and the model's extents.
 * Bones rotate ZYX about their pivot (Blockbench); 4.x files store animation keyframes with rotation (-x,-y,z) and
 * position (-x,y,z) relative to the rest pose, 5.x files as-is. 16 model units = 1 block.
 */
public final class BbModel {
    public static final class Bone { String uuid, name; double[] origin = {0, 0, 0}, rotation = {0, 0, 0}; Bone parent; }
    public static final class Clip { String name; double length; String loop = "once"; Map<String, List<JsonObject>> rot = new HashMap<>(), pos = new HashMap<>(); }

    final Map<String, Bone> bones = new LinkedHashMap<>();   // uuid -> bone
    final Map<String, Clip> clips = new LinkedHashMap<>();   // name -> clip
    double minY = 0, maxY = 16;
    private double[] rotSign = {1, 1, 1}, posSign = {1, 1, 1};

    public static BbModel parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        BbModel m = new BbModel();
        String fv = root.has("meta") && root.getAsJsonObject("meta").has("format_version") ? root.getAsJsonObject("meta").get("format_version").getAsString() : "4";
        int major = 4; try { major = Integer.parseInt(fv.split("\\.")[0]); } catch (Exception ignored) { }
        if (major < 5) { m.rotSign = new double[]{-1, -1, 1}; m.posSign = new double[]{-1, 1, 1}; }
        if (root.has("outliner")) for (JsonElement e : root.getAsJsonArray("outliner")) m.readGroup(e, null);
        if (root.has("elements")) { boolean any = false; for (JsonElement e : root.getAsJsonArray("elements")) { JsonObject c = e.getAsJsonObject(); if (!c.has("from") || !c.has("to")) continue; double a = c.getAsJsonArray("from").get(1).getAsDouble(), b = c.getAsJsonArray("to").get(1).getAsDouble(); if (!any) { m.minY = Math.min(a, b); m.maxY = Math.max(a, b); any = true; } else { m.minY = Math.min(m.minY, Math.min(a, b)); m.maxY = Math.max(m.maxY, Math.max(a, b)); } } }
        if (root.has("animations")) for (JsonElement ae : root.getAsJsonArray("animations")) {
            JsonObject a = ae.getAsJsonObject();
            Clip clip = new Clip();
            clip.name = a.has("name") ? a.get("name").getAsString() : "anim";
            clip.length = a.has("length") ? a.get("length").getAsDouble() : 0;
            clip.loop = a.has("loop") ? a.get("loop").getAsString() : "once";
            if (a.has("animators")) for (var en : a.getAsJsonObject("animators").entrySet()) {
                if (!en.getValue().isJsonObject() || !en.getValue().getAsJsonObject().has("keyframes")) continue;
                for (JsonElement ke : en.getValue().getAsJsonObject().getAsJsonArray("keyframes")) {
                    JsonObject k = ke.getAsJsonObject();
                    String ch = k.has("channel") ? k.get("channel").getAsString() : "";
                    if (ch.equals("rotation")) clip.rot.computeIfAbsent(en.getKey(), x -> new ArrayList<>()).add(k);
                    else if (ch.equals("position")) clip.pos.computeIfAbsent(en.getKey(), x -> new ArrayList<>()).add(k);
                }
            }
            m.clips.put(clip.name, clip);
        }
        return m;
    }

    private void readGroup(JsonElement e, Bone parent) {
        if (!e.isJsonObject()) return;
        JsonObject g = e.getAsJsonObject();
        Bone b = new Bone();
        b.uuid = g.has("uuid") ? g.get("uuid").getAsString() : UUID.randomUUID().toString();
        b.name = g.has("name") ? g.get("name").getAsString() : b.uuid;
        b.origin = vec(g.get("origin"), new double[]{0, 0, 0});
        b.rotation = vec(g.get("rotation"), new double[]{0, 0, 0});
        b.parent = parent;
        bones.put(b.uuid, b);
        if (g.has("children")) for (JsonElement c : g.getAsJsonArray("children")) readGroup(c, b);
    }

    public Clip clip(String name) {
        if (name == null) return null;
        Clip c = clips.get(name);
        if (c != null) return c;
        for (Clip k : clips.values()) if (k.name.equalsIgnoreCase(name)) return k;
        return null;
    }

    public Bone bone(String name) {
        for (Bone b : bones.values()) if (b.name.equals(name)) return b;
        for (Bone b : bones.values()) if (b.name.equalsIgnoreCase(name)) return b;
        return null;
    }

    public List<String> boneNames() { List<String> out = new ArrayList<>(); for (Bone b : bones.values()) out.add(b.name); return out; }

    /** Model height in blocks. */
    public double height() { return Math.max(0.5, (maxY - minY) / 16.0); }

    /** A bone's pivot in model space (blocks, Blockbench axes) at time t of a clip (rest pose when clip is null). */
    public double[] pivotAt(Bone b, Clip clip, double t) {
        double[][] w = world(b, clip, t, new HashMap<>());
        return apply(w, b.origin);
    }

    /** A unit axis of a bone in model space at time t. */
    public double[] axisAt(Bone b, Clip clip, double t, double[] local) {
        double[][] w = world(b, clip, t, new HashMap<>());
        double[] o = apply(w, b.origin), p = apply(w, new double[]{b.origin[0] + local[0], b.origin[1] + local[1], b.origin[2] + local[2]});
        double[] d = {p[0] - o[0], p[1] - o[1], p[2] - o[2]};
        double n = Math.sqrt(d[0]*d[0] + d[1]*d[1] + d[2]*d[2]);
        return n < 1e-9 ? null : new double[]{d[0]/n, d[1]/n, d[2]/n};
    }

    private double[][] world(Bone b, Clip clip, double t, Map<Bone, double[][]> cache) {
        if (b == null) return identity();
        double[][] w = cache.get(b);
        if (w != null) return w;
        double[] r = b.rotation.clone(), p = {0, 0, 0};
        if (clip != null) {
            double[] ar = sample(clip.rot.get(b.uuid), t), ap = sample(clip.pos.get(b.uuid), t);
            for (int i = 0; i < 3; i++) { r[i] += ar[i] * rotSign[i]; p[i] += ap[i] * posSign[i]; }
        }
        double[][] local = mul(mul(translate(b.origin[0] + p[0], b.origin[1] + p[1], b.origin[2] + p[2]), rotZYX(r)), translate(-b.origin[0], -b.origin[1], -b.origin[2]));
        w = b.parent == null ? local : mul(world(b.parent, clip, t, cache), local);
        cache.put(b, w);
        return w;
    }

    private static double[] sample(List<JsonObject> keys, double t) {
        if (keys == null || keys.isEmpty()) return new double[]{0, 0, 0};
        keys.sort(Comparator.comparingDouble(a -> num(a.get("time"))));
        JsonObject prev = null, next = null;
        for (JsonObject k : keys) { double kt = num(k.get("time")); if (kt <= t) prev = k; if (kt >= t) { next = k; break; } }
        if (prev == null) return point(next);
        if (next == null || prev == next) return point(prev);
        double t0 = num(prev.get("time")), t1 = num(next.get("time"));
        double f = t1 - t0 < 1e-6 ? 0 : (t - t0) / (t1 - t0);
        double[] a = point(prev), b = point(next);
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }
    private static double[] point(JsonObject k) {
        if (k == null || !k.has("data_points") || !k.get("data_points").isJsonArray() || k.getAsJsonArray("data_points").isEmpty()) return new double[]{0, 0, 0};
        JsonObject p = k.getAsJsonArray("data_points").get(0).getAsJsonObject();
        return new double[]{num(p.get("x")), num(p.get("y")), num(p.get("z"))};
    }
    private static double num(JsonElement e) {
        if (e == null || e.isJsonNull()) return 0;
        try { return e.getAsDouble(); } catch (Exception ex) { try { return Double.parseDouble(e.getAsString().trim()); } catch (Exception ex2) { return 0; } }   // Molang = 0
    }
    private static double[] vec(JsonElement e, double[] def) {
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() < 3) return def;
        JsonArray a = e.getAsJsonArray();
        return new double[]{num(a.get(0)), num(a.get(1)), num(a.get(2))};
    }
    private static double[][] identity() { return new double[][]{{1,0,0,0},{0,1,0,0},{0,0,1,0},{0,0,0,1}}; }
    private static double[][] translate(double x, double y, double z) { double[][] m = identity(); m[0][3] = x; m[1][3] = y; m[2][3] = z; return m; }
    private static double[][] rotZYX(double[] deg) {
        double x = Math.toRadians(deg[0]), y = Math.toRadians(deg[1]), z = Math.toRadians(deg[2]);
        double[][] rx = {{1,0,0,0},{0,Math.cos(x),-Math.sin(x),0},{0,Math.sin(x),Math.cos(x),0},{0,0,0,1}};
        double[][] ry = {{Math.cos(y),0,Math.sin(y),0},{0,1,0,0},{-Math.sin(y),0,Math.cos(y),0},{0,0,0,1}};
        double[][] rz = {{Math.cos(z),-Math.sin(z),0,0},{Math.sin(z),Math.cos(z),0,0},{0,0,1,0},{0,0,0,1}};
        return mul(mul(rz, ry), rx);
    }
    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[4][4];
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) { double s = 0; for (int k = 0; k < 4; k++) s += a[i][k] * b[k][j]; r[i][j] = s; }
        return r;
    }
    private static double[] apply(double[][] m, double[] v) {
        return new double[]{m[0][0]*v[0]+m[0][1]*v[1]+m[0][2]*v[2]+m[0][3], m[1][0]*v[0]+m[1][1]*v[1]+m[1][2]*v[2]+m[1][3], m[2][0]*v[0]+m[2][1]*v[1]+m[2][2]*v[2]+m[2][3]};
    }
}
