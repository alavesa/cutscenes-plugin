package fi.alavesa.cutscenes;

import fi.alavesa.cutscenes.camera.BoneShot;
import fi.alavesa.cutscenes.camera.CameraShot;
import fi.alavesa.cutscenes.camera.Easing;
import fi.alavesa.cutscenes.camera.FollowShot;
import fi.alavesa.cutscenes.camera.OrbitShot;
import fi.alavesa.cutscenes.camera.PathShot;
import fi.alavesa.cutscenes.camera.Pose;
import fi.alavesa.cutscenes.camera.StaticShot;
import kr.toxicity.model.api.animation.AnimationIterator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3d;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A playing cutscene: its actors (BetterModel dummy trackers), its camera entity (an invisible
 * ItemDisplay that every viewer spectates - Display entities interpolate position AND rotation
 * client-side, so a 20 Hz teleport stream reads as smooth motion) and the timeline cursor.
 */
public final class CutsceneRun {

    private final CutscenesPlugin plugin;
    public final CutsceneDef def;
    private final Set<UUID> viewers = new LinkedHashSet<>();
    private final Map<String, ModelActor> actors = new LinkedHashMap<>();
    private final List<EventDef> events;
    private final int length;
    private final boolean visibleToAll;

    private ItemDisplay camera;
    private int tick = 0;
    private int nextEvent = 0;
    private CameraShot shot;
    private int shotStart = 0;
    private int transition = 0;
    private Pose prevPose;
    private Pose lastPose;
    private final List<Move> moves = new ArrayList<>();
    private boolean ended;
    private final Set<String> warned = new LinkedHashSet<>();

    private record Move(ModelActor actor, Location from, Location to, int start, int duration) { }

    public CutsceneRun(CutscenesPlugin plugin, CutsceneDef def, Collection<Player> players) {
        this.plugin = plugin;
        this.def = def;
        this.events = def.sortedEvents();
        this.length = def.computedLength(plugin.settings().defaultTail);
        this.visibleToAll = def.actorsVisibleToAll != null ? def.actorsVisibleToAll : plugin.settings().actorsVisibleToAll;
        for (Player p : players) viewers.add(p.getUniqueId());
    }

    public CutscenesPlugin plugin() { return plugin; }

    public int tick() { return tick; }

    public int length() { return length; }

    public boolean ended() { return ended; }

    public Set<UUID> viewers() { return viewers; }

    public ModelActor actor(String id) {
        if (id == null) return null;
        ModelActor a = actors.get(id);
        if (a != null) return a;
        for (Map.Entry<String, ModelActor> e : actors.entrySet()) if (e.getKey().equalsIgnoreCase(id)) return e.getValue();
        return null;
    }

    /** World point of an actor bone, or the actor's mid-height when bone is null/missing. */
    public Vector3d pointOf(String actorId, String bone) {
        ModelActor a = actor(actorId);
        if (a == null) { warn("unknown actor '" + actorId + "'"); return null; }
        if (bone != null) {
            Vector3d p = a.bonePosition(bone);
            if (p != null) return p;
            warn("actor '" + actorId + "' has no bone '" + bone + "' (bones: " + a.boneNames() + ")");
        }
        Location l = a.location();
        double h = 1.0;
        try { if (a.isSpawned()) h = Math.max(0.5, a.tracker().height()) / 2.0; } catch (Throwable ignored) { }
        return new Vector3d(l.getX(), l.getY() + h, l.getZ());
    }

    public void warn(String msg) {
        if (warned.add(msg)) plugin.getLogger().warning("[" + def.name + "] " + msg);
    }

    // ---------------------------------------------------------------- lifecycle

    /** Spawn actors, create the camera, grab the viewers. */
    public boolean start() {
        for (ActorDef ad : def.actors.values()) {
            ModelActor a = new ModelActor(plugin, ad.id, ad.model, ad.location(), ad.scale);
            if (!a.spawn()) {
                warn("actor '" + ad.id + "': BetterModel has no model '" + ad.model + "'");
            } else if (ad.animation != null) {
                a.animate(ad.animation, Bm.loop(ad.loop, AnimationIterator.Type.LOOP), ad.speed, null);
            }
            actors.put(ad.id, a);
        }
        // first camera event decides where the camera is born; else the first actor; else the first viewer
        Pose first = null;
        for (EventDef e : events) if (e.type.equals("camera")) { CameraShot s = buildShot(e); if (s != null) first = s.pose(this, 0); break; }
        if (first == null && !actors.isEmpty()) {
            Location l = actors.values().iterator().next().location();
            first = new Pose(l.getWorld(), l.getX(), l.getY() + 1.6, l.getZ() - 3, 0f, 10f);
        }
        if (first == null) {
            Player any = firstOnlineViewer();
            if (any == null) { close(); return false; }
            first = Pose.of(any.getEyeLocation());
        }
        lastPose = first;
        camera = spawnCamera(first.toLocation());
        for (UUID id : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) { viewers.remove(id); continue; }
            grab(p, first.toLocation());
        }
        refreshActorVisibility();
        return true;
    }

    private Player firstOnlineViewer() {
        for (UUID id : viewers) { Player p = Bukkit.getPlayer(id); if (p != null) return p; }
        return null;
    }

    private ItemDisplay spawnCamera(Location at) {
        World w = at.getWorld();
        return w.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setTeleportDuration(plugin.settings().teleportDuration);
            d.setInterpolationDuration(0);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(1f);
            d.getPersistentDataContainer().set(plugin.cameraKey(), PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void grab(Player p, Location at) {
        plugin.viewerState().save(p);
        p.setGameMode(GameMode.SPECTATOR);
        try { p.setSpectatorTarget(null); } catch (Throwable ignored) { }
        p.teleport(at);
        plugin.markViewer(p.getUniqueId(), this);
    }

    private void refreshActorVisibility() {
        for (ModelActor a : actors.values()) {
            if (!a.isSpawned()) continue;
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean see = visibleToAll || viewers.contains(p.getUniqueId());
                if (see) a.showTo(p); else if (a.isShownTo(p)) a.hideFrom(p);
            }
        }
    }

    /** Add a player to a cutscene that is already running. */
    public void join(Player p) {
        if (ended) return;
        viewers.add(p.getUniqueId());
        grab(p, lastPose != null ? lastPose.toLocation() : camera.getLocation());
        refreshActorVisibility();
    }

    /** Remove one viewer (quit, /cutscene stop by a player, kicked out). */
    public void release(Player p) {
        viewers.remove(p.getUniqueId());
        plugin.unmarkViewer(p.getUniqueId());
        plugin.viewerState().restore(p);
        if (!visibleToAll) for (ModelActor a : actors.values()) a.hideFrom(p);
    }

    public void end() {
        if (ended) return;
        ended = true;
        List<Player> ps = new ArrayList<>();
        for (UUID id : new ArrayList<>(viewers)) {
            Player p = Bukkit.getPlayer(id);
            plugin.unmarkViewer(id);
            if (p != null) { plugin.viewerState().restore(p); ps.add(p); }
        }
        viewers.clear();
        close();
        for (String cmd : def.onEnd) runCommand(cmd, ps);
    }

    private void close() {
        for (ModelActor a : actors.values()) a.close();
        actors.clear();
        if (camera != null) { camera.remove(); camera = null; }
    }

    /** After /bettermodel reload: rebuild the actor trackers in place. */
    public void respawnActors() {
        List<Player> ps = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (visibleToAll || viewers.contains(p.getUniqueId())) ps.add(p);
        for (ModelActor a : actors.values()) a.respawn(ps);
    }

    // ---------------------------------------------------------------- per tick

    public void tickOnce() {
        if (ended) return;
        // drop viewers that vanished
        for (UUID id : new ArrayList<>(viewers)) if (Bukkit.getPlayer(id) == null) { viewers.remove(id); plugin.unmarkViewer(id); }
        if (viewers.isEmpty()) { end(); return; }

        while (nextEvent < events.size() && events.get(nextEvent).at <= tick) fire(events.get(nextEvent++));
        tickMoves();

        Pose p = shot == null ? null : shot.pose(this, tick - shotStart);
        if (p == null) p = lastPose;
        if (p != null && transition > 0 && prevPose != null && tick - shotStart < transition) {
            p = Pose.lerp(prevPose, p, Easing.SMOOTH.apply((tick - shotStart) / (double) transition));
        }
        if (p != null) {
            lastPose = p;
            if (camera != null && camera.isValid()) {
                if (!camera.getWorld().equals(p.world())) { camera.remove(); camera = spawnCamera(p.toLocation()); }
                else camera.teleport(p.toLocation());
            }
        }
        lockViewers();
        tick++;
        if (tick > length) end();
    }

    private void lockViewers() {
        if (camera == null || !camera.isValid()) return;
        boolean ready = tick >= plugin.settings().cameraLockDelay;
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.SPECTATOR);
            if (!ready) continue;
            if (p.getSpectatorTarget() != camera) {
                if (!p.getWorld().equals(camera.getWorld()) || p.getLocation().distanceSquared(camera.getLocation()) > 64 * 64) {
                    p.teleport(camera.getLocation());
                }
                try { p.setSpectatorTarget(camera); } catch (Throwable t) { warn("could not lock camera for " + p.getName() + ": " + t); }
            }
        }
    }

    private void tickMoves() {
        for (int i = moves.size() - 1; i >= 0; i--) {
            Move m = moves.get(i);
            double t = m.duration <= 0 ? 1 : Math.min(1.0, (tick - m.start) / (double) m.duration);
            double s = Easing.SMOOTH.apply(t);
            Location l = new Location(m.to.getWorld(),
                m.from.getX() + (m.to.getX() - m.from.getX()) * s,
                m.from.getY() + (m.to.getY() - m.from.getY()) * s,
                m.from.getZ() + (m.to.getZ() - m.from.getZ()) * s,
                Pose.wrap((float) (m.from.getYaw() + Pose.wrap(m.to.getYaw() - m.from.getYaw()) * s)),
                (float) (m.from.getPitch() + (m.to.getPitch() - m.from.getPitch()) * s));
            m.actor.moveTo(l);
            if (t >= 1) moves.remove(i);
        }
    }

    // ---------------------------------------------------------------- events

    private void fire(EventDef e) {
        switch (e.type) {
            case "camera" -> {
                CameraShot s = buildShot(e);
                if (s == null) { warn("bad camera event at " + e.at + ": " + e.describe()); return; }
                prevPose = lastPose;
                shot = s;
                shotStart = tick;
                transition = e.i("transition", 0);
            }
            case "animate" -> animate(e);
            case "stop" -> { ModelActor a = actor(e.s("actor", null)); if (a != null) a.stopAnimation(); else warn("stop: unknown actor " + e.s("actor", "?")); }
            case "move" -> {
                ModelActor a = actor(e.s("actor", null));
                if (a == null) { warn("move: unknown actor " + e.s("actor", "?")); return; }
                Location to = locationOf(e, a.location().getWorld());
                moves.removeIf(m -> m.actor == a);
                moves.add(new Move(a, a.location(), to, tick, e.i("duration", 0)));
            }
            case "sound" -> {
                String sound = e.s("sound", null);
                if (sound == null) return;
                Location at = null;
                if (e.data.containsKey("actor")) { Vector3d v = pointOf(e.s("actor", null), e.s("bone", null)); ModelActor a = actor(e.s("actor", null)); if (v != null && a != null) at = new Location(a.location().getWorld(), v.x, v.y, v.z); }
                for (UUID id : viewers) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    Location l = at != null ? at : (camera != null ? camera.getLocation() : p.getLocation());
                    p.playSound(l, sound, (float) e.d("volume", 1), (float) e.d("pitch", 1));
                }
            }
            case "title" -> {
                Component t = legacy(e.s("title", ""));
                Component st = legacy(e.s("subtitle", ""));
                Title title = Title.title(t, st, Title.Times.times(ticks(e.i("fade-in", 10)), ticks(e.i("stay", 40)), ticks(e.i("fade-out", 10))));
                for (UUID id : viewers) { Player p = Bukkit.getPlayer(id); if (p != null) p.showTitle(title); }
            }
            case "command" -> {
                List<Player> ps = new ArrayList<>();
                for (UUID id : viewers) { Player p = Bukkit.getPlayer(id); if (p != null) ps.add(p); }
                runCommand(e.s("command", ""), ps);
            }
            default -> warn("unknown event type '" + e.type + "' at " + e.at);
        }
    }

    private void animate(EventDef e) {
        ModelActor a = actor(e.s("actor", null));
        if (a == null) { warn("animate: unknown actor " + e.s("actor", "?")); return; }
        String anim = e.s("animation", null);
        if (anim == null) return;
        String then = e.s("then", null);
        AnimationIterator.Type loop = Bm.loop(e.s("loop", then != null ? "once" : "loop"), AnimationIterator.Type.LOOP);
        float speed = (float) e.d("speed", 1);
        Runnable onEnd = null;
        if (then != null) {
            AnimationIterator.Type thenLoop = Bm.loop(e.s("then-loop", "loop"), AnimationIterator.Type.LOOP);
            float thenSpeed = (float) e.d("then-speed", speed);
            onEnd = () -> { if (!ended && !a.animate(then, thenLoop, thenSpeed, null)) warn("then: model '" + a.model + "' has no animation '" + then + "'"); };
            if (loop != AnimationIterator.Type.PLAY_ONCE) warn("animate at " + e.at + ": 'then' only fires after a loop: once clip (clip '" + anim + "' is " + Bm.loopName(loop) + ")");
        }
        if (!a.animate(anim, loop, speed, onEnd)) warn("model '" + a.model + "' has no animation '" + anim + "' (has: " + Bm.animationNames(a.model) + ")");
    }

    private void runCommand(String cmd, List<Player> ps) {
        if (cmd == null || cmd.isBlank()) return;
        if (cmd.contains("%player%")) {
            for (Player p : ps) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private static Duration ticks(int t) { return Duration.ofMillis(Math.max(0, t) * 50L); }

    private static Component legacy(String s) { return LegacyComponentSerializer.legacyAmpersand().deserialize(s == null ? "" : s); }

    // ---------------------------------------------------------------- shot factory

    private World worldOf(EventDef e, World fallback) {
        String w = e.s("world", null);
        World world = w == null ? null : Bukkit.getWorld(w);
        if (world == null) world = Bukkit.getWorld(def.world);
        if (world == null) world = fallback;
        if (world == null && !actors.isEmpty()) world = actors.values().iterator().next().location().getWorld();
        if (world == null) world = Bukkit.getWorlds().get(0);
        return world;
    }

    private Location locationOf(EventDef e, World fallback) {
        return new Location(worldOf(e, fallback), e.d("x", 0), e.d("y", 0), e.d("z", 0), (float) e.d("yaw", 0), (float) e.d("pitch", 0));
    }

    /** Build the shot a camera event describes; null when it cannot be understood. */
    public CameraShot buildShot(EventDef e) {
        String kind = e.s("camera", "static").toLowerCase();
        String actor = e.s("actor", null), bone = e.s("bone", null);
        String lookActor = e.s("look-actor", null), lookBone = e.s("look-bone", null);
        Easing easing = Easing.parse(e.s("ease", "smooth"), Easing.SMOOTH);
        switch (kind) {
            case "static": return new StaticShot(Pose.of(locationOf(e, null)));
            case "bone": if (actor == null) return null; return new BoneShot(actor, bone, lookActor, lookBone);
            case "follow": if (actor == null) return null; return new FollowShot(actor, bone, e.d("back", 4), e.d("up", 2), e.d("right", 0));
            case "orbit": {
                World w = worldOf(e, null);
                Vector3d point = actor == null ? new Vector3d(e.d("x", 0), e.d("y", 0), e.d("z", 0)) : null;
                return new OrbitShot(actor, bone, point, w, e.d("radius", 5), e.d("height", 2), e.d("start-angle", 0), e.d("degrees", 360), e.i("duration", 100), easing);
            }
            case "path": {
                World w = worldOf(e, null);
                List<Pose> pts = new ArrayList<>();
                for (String s : e.list("points")) { Pose p = parsePoint(w, s); if (p != null) pts.add(p); }
                if (pts.isEmpty()) return null;
                return new PathShot(pts, e.i("duration", 100), easing, !e.s("spline", "smooth").equalsIgnoreCase("linear"), lookActor, lookBone);
            }
            default: return null;
        }
    }

    /** "x,y,z[,yaw,pitch]" or "world,x,y,z,yaw,pitch". */
    public static Pose parsePoint(World def, String s) {
        try {
            String[] p = s.trim().split("[,\\s]+");
            World w = def; int i = 0;
            if (p.length == 6 || p.length == 4) { World ww = Bukkit.getWorld(p[0]); if (ww != null) { w = ww; i = 1; } }
            double x = Double.parseDouble(p[i]), y = Double.parseDouble(p[i + 1]), z = Double.parseDouble(p[i + 2]);
            float yaw = p.length > i + 3 ? Float.parseFloat(p[i + 3]) : 0f;
            float pitch = p.length > i + 4 ? Float.parseFloat(p[i + 4]) : 0f;
            return new Pose(w, x, y, z, yaw, pitch);
        } catch (Exception ex) {
            return null;
        }
    }

    public static String formatPoint(Location l) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f", l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }
}
