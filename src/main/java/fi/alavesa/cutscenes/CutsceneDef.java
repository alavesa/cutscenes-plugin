package fi.alavesa.cutscenes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A cutscene on disk: actors + a timeline of events. */
public final class CutsceneDef {
    public final String name;
    public String world;                 // default world for path points / static cameras written by hand
    public int length = -1;              // ticks; -1 = computed from the timeline
    public Boolean actorsVisibleToAll;   // null = config default
    public final Map<String, ActorDef> actors = new LinkedHashMap<>();
    public final List<EventDef> events = new ArrayList<>();
    public final List<String> onEnd = new ArrayList<>();   // console commands, %player% per viewer

    public CutsceneDef(String name, String world) {
        this.name = name;
        this.world = world;
    }

    /** Events sorted by tick, stable for equal ticks (file order). */
    public List<EventDef> sortedEvents() {
        List<EventDef> l = new ArrayList<>(events);
        l.sort((a, b) -> Integer.compare(a.at, b.at));
        return l;
    }

    public int computedLength(int tail) {
        if (length >= 0) return length;
        int end = 0;
        for (EventDef e : events) end = Math.max(end, e.at + e.duration(this));
        return end + tail;
    }
}
