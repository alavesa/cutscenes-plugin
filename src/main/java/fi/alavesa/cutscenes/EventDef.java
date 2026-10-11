package fi.alavesa.cutscenes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One timeline entry of a cutscene: {@code at} (tick), {@code type} and free-form keys, kept as the
 * same map that is written to the YAML file so the in-game editor and hand-written files are equivalent.
 *
 * Types: camera, animate, stop, move, sound, title, command.
 */
public final class EventDef {

    public int at;
    public String type;
    public final Map<String, Object> data = new LinkedHashMap<>();

    public EventDef(int at, String type) {
        this.at = Math.max(0, at);
        this.type = type;
    }

    public static EventDef fromMap(Map<?, ?> m) {
        Object at = m.get("at");
        Object type = m.get("type");
        EventDef e = new EventDef(at instanceof Number n ? n.intValue() : 0, type == null ? "?" : type.toString());
        for (Map.Entry<?, ?> en : m.entrySet()) {
            String k = String.valueOf(en.getKey());
            if (k.equals("at") || k.equals("type")) continue;
            e.data.put(k, en.getValue());
        }
        return e;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", at);
        m.put("type", type);
        m.putAll(data);
        return m;
    }

    public EventDef set(String key, Object value) {
        if (value == null) data.remove(key); else data.put(key, value);
        return this;
    }

    public String s(String key, String def) {
        Object o = data.get(key);
        return o == null ? def : o.toString();
    }

    public double d(String key, double def) {
        Object o = data.get(key);
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        return def;
    }

    public int i(String key, int def) {
        Object o = data.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        return def;
    }

    public boolean b(String key, boolean def) {
        Object o = data.get(key);
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return s.equalsIgnoreCase("true");
        return def;
    }

    @SuppressWarnings("unchecked")
    public List<String> list(String key) {
        Object o = data.get(key);
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        else if (o != null) out.add(o.toString());
        return out;
    }

    public void setList(String key, List<String> values) {
        data.put(key, new ArrayList<>(values));
    }

    /** Ticks this event keeps "doing something" after {@code at} (used to compute a cutscene's length). */
    public int duration(CutsceneDef def) {
        return switch (type) {
            case "camera" -> switch (s("camera", "static")) {
                case "path", "orbit" -> i("duration", 0);
                default -> 0;
            };
            case "move" -> i("duration", 0);
            case "animate" -> {
                ActorDef a = def.actors.get(s("actor", ""));
                if (a == null || !s("loop", "loop").equals("once")) yield 0;
                int t = Bm.animationTicks(a.model, Bm.animation(a.model, s("animation", "")));
                yield t < 0 ? 0 : (int) (t / Math.max(0.01, d("speed", 1)));
            }
            case "title" -> i("fade-in", 10) + i("stay", 40) + i("fade-out", 10);
            default -> 0;
        };
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(type);
        for (Map.Entry<String, Object> e : data.entrySet()) {
            if (e.getValue() instanceof List<?> l) sb.append(' ').append(e.getKey()).append('=').append(l.size()).append(" pts");
            else sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }
}
