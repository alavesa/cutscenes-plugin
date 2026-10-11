package fi.alavesa.cutscenes.camera;

/** Time-remapping curves for camera moves. */
public enum Easing {
    LINEAR, SMOOTH, EASE_IN, EASE_OUT;

    public double apply(double t) {
        t = Math.max(0, Math.min(1, t));
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH -> t * t * (3 - 2 * t);
            case EASE_IN -> t * t;
            case EASE_OUT -> 1 - (1 - t) * (1 - t);
        };
    }

    public static Easing parse(String s, Easing def) {
        if (s == null) return def;
        return switch (s.toLowerCase().replace('-', '_')) {
            case "linear" -> LINEAR;
            case "smooth", "ease", "ease_in_out", "smoothstep" -> SMOOTH;
            case "ease_in", "in" -> EASE_IN;
            case "ease_out", "out" -> EASE_OUT;
            default -> def;
        };
    }

    public String key() { return name().toLowerCase().replace('_', '-'); }
}
