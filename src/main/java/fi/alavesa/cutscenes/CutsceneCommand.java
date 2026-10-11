package fi.alavesa.cutscenes;

import fi.alavesa.cutscenes.camera.CameraShot;
import kr.toxicity.model.api.animation.AnimationIterator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** /cutscene ... - the in-game editor, the player, and the world-model commands. */
public final class CutsceneCommand implements TabExecutor {

    private final CutscenesPlugin plugin;

    CutsceneCommand(CutscenesPlugin plugin) { this.plugin = plugin; }

    private static final String P = "§6[Cutscenes] §r";

    private static void ok(CommandSender s, String m) { s.sendMessage(P + "§a" + m); }
    private static void err(CommandSender s, String m) { s.sendMessage(P + "§c" + m); }
    private static void info(CommandSender s, String m) { s.sendMessage(P + "§7" + m); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender, args.length > 1 ? args[1] : ""); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "model" -> model(sender, tail(args, 1));
                case "create" -> create(sender, tail(args, 1));
                case "delete" -> { if (need(sender, args, 2, "delete <name>")) return true; if (plugin.store().delete(args[1])) ok(sender, "deleted " + args[1]); else err(sender, "no cutscene " + args[1]); }
                case "list" -> list(sender);
                case "info" -> { if (need(sender, args, 2, "info <name>")) return true; infoCutscene(sender, args[1]); }
                case "reload" -> { plugin.reloadAll(); ok(sender, "reloaded config, " + plugin.store().all().size() + " cutscene(s), " + plugin.worldModels().all().size() + " world model(s)."); }
                case "play" -> play(sender, tail(args, 1));
                case "stop" -> stop(sender, tail(args, 1));
                case "length" -> { if (need(sender, args, 3, "length <name> <ticks|auto>")) return true; CutsceneDef d = def(sender, args[1]); if (d == null) return true; d.length = args[2].equalsIgnoreCase("auto") ? -1 : Integer.parseInt(args[2]); plugin.store().save(d); ok(sender, "length = " + (d.length < 0 ? "auto (" + d.computedLength(plugin.settings().defaultTail) + "t)" : d.length + "t")); }
                case "visible" -> { if (need(sender, args, 3, "visible <name> <all|viewers|default>")) return true; CutsceneDef d = def(sender, args[1]); if (d == null) return true; d.actorsVisibleToAll = switch (args[2].toLowerCase()) { case "all" -> true; case "viewers" -> false; default -> null; }; plugin.store().save(d); ok(sender, "actors visible to " + (d.actorsVisibleToAll == null ? "(config default)" : d.actorsVisibleToAll ? "everyone" : "viewers only")); }
                case "onend" -> { if (need(sender, args, 3, "onend <name> <add <command…>|clear>")) return true; CutsceneDef d = def(sender, args[1]); if (d == null) return true; if (args[2].equalsIgnoreCase("clear")) d.onEnd.clear(); else d.onEnd.add(String.join(" ", tail(args, 3))); plugin.store().save(d); ok(sender, "on-end commands: " + d.onEnd); }
                case "actor" -> actor(sender, tail(args, 1));
                case "add" -> add(sender, tail(args, 1));
                case "point" -> point(sender, tail(args, 1));
                case "lookat" -> lookat(sender, tail(args, 1));
                case "remove" -> { if (need(sender, args, 3, "remove <name> <event#>")) return true; CutsceneDef d = def(sender, args[1]); if (d == null) return true; int i = Integer.parseInt(args[2]); if (i < 0 || i >= d.events.size()) { err(sender, "no event #" + i + " (0.." + (d.events.size() - 1) + ")"); return true; } EventDef e = d.events.remove(i); plugin.store().save(d); ok(sender, "removed #" + i + ": @" + e.at + " " + e.describe()); }
                case "at" -> { if (need(sender, args, 4, "at <name> <event#> <tick>")) return true; CutsceneDef d = def(sender, args[1]); if (d == null) return true; EventDef e = event(sender, d, args[2]); if (e == null) return true; e.at = Integer.parseInt(args[3]); plugin.store().save(d); ok(sender, "event now at tick " + e.at); }
                case "set" -> { if (need(sender, args, 5, "set <name> <event#> <key> <value>")) return true; CutsceneDef d = def(sender, args[1]); if (d == null) return true; EventDef e = event(sender, d, args[2]); if (e == null) return true; e.set(args[3], parseValue(String.join(" ", tail(args, 4)))); plugin.store().save(d); ok(sender, "#" + args[2] + " = " + e.describe()); }
                default -> help(sender, "");
            }
        } catch (NumberFormatException ex) {
            err(sender, "not a number: " + ex.getMessage());
        }
        return true;
    }

    // ---------------------------------------------------------------- help

    private void help(CommandSender s, String topic) {
        info(s, "§6Cutscenes " + plugin.getDescription().getVersion() + " §7— models come from BetterModel (plugins/BetterModel/models, /bettermodel reload)");
        if (topic.equalsIgnoreCase("model") || topic.isEmpty()) {
            info(s, "§eWorld models");
            info(s, "/cs model spawn <model> [animation] [loop|once|hold] [scale]");
            info(s, "/cs model list | info <id> | bones <id> | remove <id|nearest|all>");
            info(s, "/cs model animate <id> <animation> [loop|once|hold] [speed]   /cs model stop <id>");
            info(s, "/cs model move <id> [scale]   (to where you stand, facing your way)");
        }
        if (topic.equalsIgnoreCase("edit") || topic.isEmpty()) {
            info(s, "§eCutscene editing");
            info(s, "/cs create <name> | delete <name> | list | info <name> | reload | length <name> <ticks|auto>");
            info(s, "/cs actor add <cs> <id> <model> [animation] [loop|once|hold] [scale]   actor move|remove <cs> <id>");
            info(s, "/cs add <cs> <tick> animate <actor> <animation> [loop|once|hold] [speed] [then <animation> [loop|once|hold]]");
            info(s, "/cs add <cs> <tick> stop <actor>   |   move <actor> <duration>   (to where you stand)");
            info(s, "/cs add <cs> <tick> camera static [transition]            (your eye position + view)");
            info(s, "/cs add <cs> <tick> camera bone <actor> [bone|-] [transition]   (rides the model's camera group)");
            info(s, "/cs add <cs> <tick> camera follow <actor> [back up right] [transition]");
            info(s, "/cs add <cs> <tick> camera orbit <actor> [radius height degrees duration] [transition]");
            info(s, "/cs add <cs> <tick> camera path <duration> [smooth|linear] [transition]  then /cs point <cs> <event#> …");
            info(s, "/cs lookat <cs> <event#> <actor> [bone]   (path/bone shots look at that actor)");
            info(s, "/cs add <cs> <tick> sound <sound> [volume] [pitch]   |   title <title>|<subtitle>   |   command <cmd…>");
            info(s, "/cs remove <cs> <event#> | at <cs> <event#> <tick> | set <cs> <event#> <key> <value> | onend <cs> add <cmd>|clear");
        }
        if (topic.equalsIgnoreCase("play") || topic.isEmpty()) {
            info(s, "§ePlaying");
            info(s, "/cs play <name> [player|all|radius:<blocks>]   /cs stop [all|<player>]   /cs visible <cs> all|viewers|default");
        }
    }

    // ---------------------------------------------------------------- world models

    private void model(CommandSender s, String[] a) {
        if (a.length == 0) { help(s, "model"); return; }
        WorldModels wm = plugin.worldModels();
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                Player p = player(s); if (p == null) return;
                if (a.length < 2) { err(s, "model spawn <model> [animation] [loop|once|hold] [scale]"); return; }
                if (Bm.model(a[1]) == null) { err(s, "BetterModel has no model '" + a[1] + "'. Known: " + Bm.modelNames()); return; }
                float scale = a.length > 4 ? Float.parseFloat(a[4]) : 1f;
                ModelActor m = wm.spawn(wm.freeId(a[1]), a[1], p.getLocation(), scale);
                if (m == null) { err(s, "could not create the model"); return; }
                if (a.length > 2) {
                    if (!wm.animate(m.id, a[2], a.length > 3 ? a[3] : "loop", 1f)) err(s, "no animation '" + a[2] + "' (has: " + Bm.animationNames(m.model) + ")");
                }
                ok(s, "spawned " + m.model + " as §f" + m.id + "§a at your position" + (m.currentAnimation() != null ? ", playing " + m.currentAnimation() : ""));
            }
            case "list" -> {
                if (wm.all().isEmpty()) { info(s, "no world models. /cs model spawn <model>"); return; }
                for (ModelActor m : wm.all()) { Location l = m.location(); info(s, String.format("§f%s§7 = %s @ %s %.1f %.1f %.1f%s%s", m.id, m.model, l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), m.currentAnimation() == null ? "" : " playing " + m.currentAnimation(), m.isSpawned() ? "" : " §c(model missing)")); }
            }
            case "info", "bones" -> {
                if (a.length < 2) { err(s, "model " + a[0] + " <id>"); return; }
                ModelActor m = wm.get(a[1]);
                if (m == null) { err(s, "no world model '" + a[1] + "'"); return; }
                info(s, "§f" + m.id + "§7 model " + m.model + ", animations: " + Bm.animationNames(m.model));
                info(s, "bones (world position now, use these names for camera shots):");
                for (String b : m.boneNames()) { Vector3d v = m.bonePosition(b); info(s, String.format("  %s%s §8%.2f %.2f %.2f", b.equalsIgnoreCase(plugin.settings().cameraBone) || b.equalsIgnoreCase(plugin.settings().targetBone) ? "§e" : "§f", b, v == null ? 0 : v.x, v == null ? 0 : v.y, v == null ? 0 : v.z)); }
            }
            case "remove" -> {
                if (a.length < 2) { err(s, "model remove <id|nearest|all>"); return; }
                if (a[1].equalsIgnoreCase("all")) { ok(s, "removed " + wm.removeAll() + " model(s)"); return; }
                String id = a[1];
                if (id.equalsIgnoreCase("nearest")) { Player p = player(s); if (p == null) return; ModelActor m = wm.nearest(p.getLocation(), 32); if (m == null) { err(s, "no model within 32 blocks"); return; } id = m.id; }
                if (wm.remove(id)) ok(s, "removed " + id); else err(s, "no world model '" + id + "'");
            }
            case "animate" -> {
                if (a.length < 3) { err(s, "model animate <id> <animation> [loop|once|hold] [speed]"); return; }
                ModelActor m = wm.get(a[1]);
                if (m == null) { err(s, "no world model '" + a[1] + "'"); return; }
                if (wm.animate(a[1], a[2], a.length > 3 ? a[3] : "loop", a.length > 4 ? Float.parseFloat(a[4]) : 1f)) ok(s, m.id + " plays " + m.currentAnimation());
                else err(s, "no animation '" + a[2] + "' (has: " + Bm.animationNames(m.model) + ")");
            }
            case "stop" -> { if (a.length < 2) { err(s, "model stop <id>"); return; } if (wm.animate(a[1], null, null, 1f)) ok(s, "stopped"); else err(s, "no world model '" + a[1] + "'"); }
            case "move" -> {
                Player p = player(s); if (p == null) return;
                if (a.length < 2) { err(s, "model move <id> [scale]"); return; }
                ModelActor m = wm.get(a[1]);
                if (m == null) { err(s, "no world model '" + a[1] + "'"); return; }
                m.moveTo(p.getLocation());
                if (a.length > 2) m.scale(Float.parseFloat(a[2]));
                wm.save();
                ok(s, "moved " + m.id);
            }
            default -> help(s, "model");
        }
    }

    // ---------------------------------------------------------------- cutscene editing

    private void create(CommandSender s, String[] a) {
        if (a.length < 1) { err(s, "create <name>"); return; }
        String name = a[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (name.isEmpty()) { err(s, "use letters, digits, _ or -"); return; }
        if (plugin.store().get(name) != null) { err(s, "cutscene '" + name + "' exists"); return; }
        String world = s instanceof Player p ? p.getWorld().getName() : Bukkit.getWorlds().get(0).getName();
        plugin.store().save(new CutsceneDef(name, world));
        ok(s, "created '" + name + "'. Next: /cs actor add " + name + " <id> <model>, then /cs add " + name + " 0 camera …");
    }

    private void list(CommandSender s) {
        if (plugin.store().all().isEmpty()) { info(s, "no cutscenes. /cs create <name>"); return; }
        for (CutsceneDef d : plugin.store().all().values()) info(s, "§f" + d.name + "§7: " + d.actors.size() + " actor(s), " + d.events.size() + " event(s), " + d.computedLength(plugin.settings().defaultTail) + " ticks");
        if (!plugin.runs().isEmpty()) for (CutsceneRun r : plugin.runs()) info(s, "§aplaying§7 " + r.def.name + " tick " + r.tick() + "/" + r.length() + " for " + r.viewers().size() + " viewer(s)");
    }

    private void infoCutscene(CommandSender s, String name) {
        CutsceneDef d = def(s, name); if (d == null) return;
        info(s, "§f" + d.name + "§7 world " + d.world + ", length " + (d.length < 0 ? "auto " : "") + d.computedLength(plugin.settings().defaultTail) + "t, actors visible to " + (d.actorsVisibleToAll == null ? "(default)" : d.actorsVisibleToAll ? "all" : "viewers"));
        for (ActorDef ad : d.actors.values()) info(s, String.format("  actor §f%s§7 = %s @ %.1f %.1f %.1f yaw %.0f%s%s", ad.id, ad.model, ad.x, ad.y, ad.z, ad.yaw, ad.animation == null ? "" : " plays " + ad.animation + " (" + ad.loop + ")", Bm.model(ad.model) == null ? " §c(model missing)" : ""));
        for (int i = 0; i < d.events.size(); i++) {
            EventDef e = d.events.get(i);
            String desc = e.describe();
            if (e.type.equals("camera")) { CameraShot sh = new CutsceneRun(plugin, d, List.of()).buildShot(e); desc = "camera " + (sh == null ? "§c(invalid) " + e.describe() : sh.describe()) + (e.i("transition", 0) > 0 ? " blend " + e.i("transition", 0) + "t" : ""); }
            info(s, "  #" + i + " @" + e.at + " " + desc);
        }
        if (!d.onEnd.isEmpty()) info(s, "  on-end: " + d.onEnd);
    }

    private void actor(CommandSender s, String[] a) {
        if (a.length < 3) { err(s, "actor add <cs> <id> <model> [animation] [loop|once|hold] [scale] | actor move|remove <cs> <id>"); return; }
        CutsceneDef d = def(s, a[1]); if (d == null) return;
        String id = a[2];
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                Player p = player(s); if (p == null) return;
                if (a.length < 4) { err(s, "actor add <cs> <id> <model> [animation] [loop|once|hold] [scale]"); return; }
                if (Bm.model(a[3]) == null) { err(s, "BetterModel has no model '" + a[3] + "'. Known: " + Bm.modelNames()); return; }
                ActorDef ad = new ActorDef(id, a[3], p.getLocation());
                if (a.length > 4) { ad.animation = Bm.animation(a[3], a[4]); if (ad.animation == null) err(s, "no animation '" + a[4] + "' - actor added without one (has: " + Bm.animationNames(a[3]) + ")"); }
                if (a.length > 5) ad.loop = Bm.loopName(Bm.loop(a[5], AnimationIterator.Type.LOOP));
                if (a.length > 6) ad.scale = Float.parseFloat(a[6]);
                d.actors.put(id, ad);
                plugin.store().save(d);
                ok(s, "actor " + id + " = " + a[3] + " at your position" + (ad.animation != null ? ", starts with " + ad.animation : ""));
            }
            case "move" -> { Player p = player(s); if (p == null) return; ActorDef ad = d.actors.get(id); if (ad == null) { err(s, "no actor " + id); return; } ad.setLocation(p.getLocation()); plugin.store().save(d); ok(s, "moved " + id); }
            case "remove" -> { if (d.actors.remove(id) == null) err(s, "no actor " + id); else { plugin.store().save(d); ok(s, "removed actor " + id); } }
            default -> err(s, "actor add|move|remove");
        }
    }

    private void add(CommandSender s, String[] a) {
        if (a.length < 3) { help(s, "edit"); return; }
        CutsceneDef d = def(s, a[0]); if (d == null) return;
        int at = Integer.parseInt(a[1]);
        String type = a[2].toLowerCase(Locale.ROOT);
        String[] r = tail(a, 3);
        EventDef e = new EventDef(at, type);
        switch (type) {
            case "animate" -> {
                if (r.length < 2) { err(s, "add <cs> <tick> animate <actor> <animation> [loop|once|hold] [speed] [then <animation> [loop]]"); return; }
                ActorDef ad = d.actors.get(r[0]); if (ad == null) { err(s, "no actor " + r[0] + " (" + d.actors.keySet() + ")"); return; }
                String anim = Bm.animation(ad.model, r[1]); if (anim == null) { err(s, "no animation '" + r[1] + "' (has: " + Bm.animationNames(ad.model) + ")"); return; }
                e.set("actor", ad.id).set("animation", anim);
                int i = 2;
                if (i < r.length && !r[i].equalsIgnoreCase("then")) { e.set("loop", Bm.loopName(Bm.loop(r[i], AnimationIterator.Type.LOOP))); i++; }
                if (i < r.length && !r[i].equalsIgnoreCase("then")) { e.set("speed", Double.parseDouble(r[i])); i++; }
                if (i < r.length && r[i].equalsIgnoreCase("then")) {
                    if (i + 1 >= r.length) { err(s, "then <animation> [loop|once|hold]"); return; }
                    String then = Bm.animation(ad.model, r[i + 1]); if (then == null) { err(s, "no animation '" + r[i + 1] + "'"); return; }
                    e.set("then", then);
                    if (!e.data.containsKey("loop")) e.set("loop", "once");
                    if (i + 2 < r.length) e.set("then-loop", Bm.loopName(Bm.loop(r[i + 2], AnimationIterator.Type.LOOP)));
                }
            }
            case "stop" -> { if (r.length < 1) { err(s, "add <cs> <tick> stop <actor>"); return; } e.set("actor", r[0]); }
            case "move" -> {
                Player p = player(s); if (p == null) return;
                if (r.length < 1) { err(s, "add <cs> <tick> move <actor> [duration]"); return; }
                Location l = p.getLocation();
                e.set("actor", r[0]).set("world", l.getWorld().getName()).set("x", round(l.getX())).set("y", round(l.getY())).set("z", round(l.getZ())).set("yaw", round(l.getYaw())).set("pitch", round(l.getPitch())).set("duration", r.length > 1 ? Integer.parseInt(r[1]) : 20);
            }
            case "camera" -> { if (!camera(s, d, e, r)) return; }
            case "sound" -> { if (r.length < 1) { err(s, "add <cs> <tick> sound <sound> [volume] [pitch]"); return; } e.set("sound", r[0]); if (r.length > 1) e.set("volume", Double.parseDouble(r[1])); if (r.length > 2) e.set("pitch", Double.parseDouble(r[2])); }
            case "title" -> { String all = String.join(" ", r); String[] parts = all.split("\\|", 2); e.set("title", parts[0].trim()); if (parts.length > 1) e.set("subtitle", parts[1].trim()); e.set("fade-in", 10).set("stay", 40).set("fade-out", 10); }
            case "command" -> { if (r.length < 1) { err(s, "add <cs> <tick> command <command…>"); return; } e.set("command", String.join(" ", r)); }
            default -> { err(s, "event types: animate stop move camera sound title command"); return; }
        }
        d.events.add(e);
        plugin.store().save(d);
        ok(s, "added #" + (d.events.size() - 1) + " @" + at + ": " + e.describe());
    }

    private boolean camera(CommandSender s, CutsceneDef d, EventDef e, String[] r) {
        if (r.length < 1) { err(s, "camera static|bone|follow|orbit|path …"); return false; }
        String kind = r[0].toLowerCase(Locale.ROOT);
        e.set("camera", kind);
        switch (kind) {
            case "static" -> {
                Player p = player(s); if (p == null) return false;
                Location l = p.getEyeLocation();
                e.set("world", l.getWorld().getName()).set("x", round(l.getX())).set("y", round(l.getY())).set("z", round(l.getZ())).set("yaw", round(l.getYaw())).set("pitch", round(l.getPitch()));
                if (r.length > 1) e.set("transition", Integer.parseInt(r[1]));
            }
            case "bone" -> {
                if (r.length < 2 || !d.actors.containsKey(r[1])) { err(s, "camera bone <actor> [bone|-] [transition]  actors: " + d.actors.keySet()); return false; }
                e.set("actor", r[1]);
                if (r.length > 2 && !r[2].equals("-")) e.set("bone", r[2]);
                if (r.length > 3) e.set("transition", Integer.parseInt(r[3]));
            }
            case "follow" -> {
                if (r.length < 2 || !d.actors.containsKey(r[1])) { err(s, "camera follow <actor> [back up right] [transition]"); return false; }
                e.set("actor", r[1]);
                if (r.length > 4) { e.set("back", Double.parseDouble(r[2])).set("up", Double.parseDouble(r[3])).set("right", Double.parseDouble(r[4])); }
                if (r.length > 5) e.set("transition", Integer.parseInt(r[5]));
            }
            case "orbit" -> {
                if (r.length < 2 || !d.actors.containsKey(r[1])) { err(s, "camera orbit <actor> [radius height degrees duration] [transition]"); return false; }
                e.set("actor", r[1]);
                e.set("radius", r.length > 2 ? Double.parseDouble(r[2]) : 5.0).set("height", r.length > 3 ? Double.parseDouble(r[3]) : 2.0).set("degrees", r.length > 4 ? Double.parseDouble(r[4]) : 360.0).set("duration", r.length > 5 ? Integer.parseInt(r[5]) : 100);
                if (r.length > 6) e.set("transition", Integer.parseInt(r[6]));
            }
            case "path" -> {
                Player p = player(s); if (p == null) return false;
                e.set("world", p.getWorld().getName()).set("duration", r.length > 1 ? Integer.parseInt(r[1]) : 100);
                e.set("spline", r.length > 2 ? r[2].toLowerCase(Locale.ROOT) : "smooth");
                if (r.length > 3) e.set("transition", Integer.parseInt(r[3]));
                e.setList("points", List.of(CutsceneRun.formatPoint(p.getEyeLocation())));
                info(s, "first point = your eye position. Walk on and /cs point " + d.name + " " + d.events.size() + " for each next point.");
            }
            default -> { err(s, "camera static|bone|follow|orbit|path"); return false; }
        }
        return true;
    }

    private void point(CommandSender s, String[] a) {
        Player p = player(s); if (p == null) return;
        if (a.length < 2) { err(s, "point <cs> <event#> [clear]"); return; }
        CutsceneDef d = def(s, a[0]); if (d == null) return;
        EventDef e = event(s, d, a[1]); if (e == null) return;
        if (!e.type.equals("camera") || !e.s("camera", "").equals("path")) { err(s, "#" + a[1] + " is not a path camera"); return; }
        List<String> pts = e.list("points");
        if (a.length > 2 && a[2].equalsIgnoreCase("clear")) pts.clear(); else pts.add(CutsceneRun.formatPoint(p.getEyeLocation()));
        e.setList("points", pts);
        plugin.store().save(d);
        ok(s, "path #" + a[1] + " now has " + pts.size() + " point(s)");
    }

    private void lookat(CommandSender s, String[] a) {
        if (a.length < 3) { err(s, "lookat <cs> <event#> <actor|-> [bone]"); return; }
        CutsceneDef d = def(s, a[0]); if (d == null) return;
        EventDef e = event(s, d, a[1]); if (e == null) return;
        if (a[2].equals("-")) { e.set("look-actor", null).set("look-bone", null); }
        else { if (!d.actors.containsKey(a[2])) { err(s, "no actor " + a[2]); return; } e.set("look-actor", a[2]).set("look-bone", a.length > 3 ? a[3] : null); }
        plugin.store().save(d);
        ok(s, "#" + a[1] + " looks at " + (a[2].equals("-") ? "its own direction" : a[2] + (a.length > 3 ? "/" + a[3] : "")));
    }

    // ---------------------------------------------------------------- play / stop

    private void play(CommandSender s, String[] a) {
        if (a.length < 1) { err(s, "play <name> [player|all|radius:<blocks>]"); return; }
        CutsceneDef d = def(s, a[0]); if (d == null) return;
        List<Player> targets = new ArrayList<>();
        if (a.length < 2) { Player p = player(s); if (p == null) return; targets.add(p); }
        else if (a[1].equalsIgnoreCase("all") || a[1].equals("@a")) targets.addAll(Bukkit.getOnlinePlayers());
        else if (a[1].toLowerCase(Locale.ROOT).startsWith("radius:")) {
            Player p = player(s); if (p == null) return;
            double r = Double.parseDouble(a[1].substring(7));
            for (Player o : p.getWorld().getPlayers()) if (o.getLocation().distance(p.getLocation()) <= r) targets.add(o);
        } else {
            for (int i = 1; i < a.length; i++) { Player p = Bukkit.getPlayerExact(a[i]); if (p == null) err(s, "player " + a[i] + " is not online"); else targets.add(p); }
        }
        if (targets.isEmpty()) { err(s, "nobody to play it to"); return; }
        CutsceneRun run = plugin.play(d, targets);
        if (run == null) { err(s, "could not start (see console)"); return; }
        ok(s, "playing '" + d.name + "' (" + run.length() + " ticks) for " + targets.size() + " player(s)");
    }

    private void stop(CommandSender s, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("all")) { ok(s, "stopped " + plugin.stopAll() + " cutscene(s)"); return; }
        Player p = a.length > 0 ? Bukkit.getPlayerExact(a[0]) : (s instanceof Player pl ? pl : null);
        if (p == null) { err(s, "stop [all|<player>]"); return; }
        CutsceneRun run = plugin.runOf(p);
        if (run == null) { err(s, p.getName() + " is not in a cutscene"); return; }
        run.release(p);
        ok(s, "released " + p.getName() + " from '" + run.def.name + "'");
    }

    // ---------------------------------------------------------------- helpers

    private CutsceneDef def(CommandSender s, String name) {
        CutsceneDef d = plugin.store().get(name);
        if (d == null) err(s, "no cutscene '" + name + "' (" + plugin.store().all().keySet() + ")");
        return d;
    }

    private EventDef event(CommandSender s, CutsceneDef d, String idx) {
        int i = Integer.parseInt(idx);
        if (i < 0 || i >= d.events.size()) { err(s, "no event #" + i + " (0.." + (d.events.size() - 1) + ")"); return null; }
        return d.events.get(i);
    }

    private static Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        s.sendMessage(P + "§cin-game only");
        return null;
    }

    private static boolean need(CommandSender s, String[] a, int n, String usage) {
        if (a.length >= n) return false;
        err(s, "/cs " + usage);
        return true;
    }

    private static String[] tail(String[] a, int from) {
        return from >= a.length ? new String[0] : Arrays.copyOfRange(a, from, a.length);
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private static Object parseValue(String v) {
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) return Boolean.parseBoolean(v);
        if (v.equalsIgnoreCase("null") || v.equals("-")) return null;
        try { if (v.matches("-?\\d+")) return Integer.parseInt(v); return Double.parseDouble(v); } catch (NumberFormatException e) { return v; }
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) return filter(List.of("model", "create", "delete", "list", "info", "reload", "play", "stop", "length", "visible", "onend", "actor", "add", "point", "lookat", "remove", "at", "set", "help"), a[0]);
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("model")) {
            if (a.length == 2) return filter(List.of("spawn", "list", "info", "bones", "remove", "animate", "stop", "move"), a[1]);
            String ms = a[1].toLowerCase(Locale.ROOT);
            if (a.length == 3) {
                if (ms.equals("spawn")) return filter(Bm.modelNames(), a[2]);
                List<String> ids = new ArrayList<>(); for (ModelActor m : plugin.worldModels().all()) ids.add(m.id);
                if (ms.equals("remove")) { ids.add("nearest"); ids.add("all"); }
                return filter(ids, a[2]);
            }
            if (a.length == 4) {
                if (ms.equals("spawn")) return filter(Bm.animationNames(a[2]), a[3]);
                if (ms.equals("animate")) { ModelActor m = plugin.worldModels().get(a[2]); return m == null ? out : filter(Bm.animationNames(m.model), a[3]); }
            }
            if (a.length == 5 && (ms.equals("spawn") || ms.equals("animate"))) return filter(List.of("loop", "once", "hold"), a[4]);
            return out;
        }
        if (a.length == 2 && !sub.equals("create") && !sub.equals("list") && !sub.equals("reload") && !sub.equals("stop")) return filter(plugin.store().all().keySet(), a[1]);
        if (a.length == 2 && sub.equals("stop")) { out.add("all"); for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName()); return filter(out, a[1]); }
        CutsceneDef d = plugin.store().get(a.length > 1 ? a[1] : "");
        switch (sub) {
            case "play" -> { if (a.length == 3) { out.add("all"); out.add("radius:20"); for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName()); return filter(out, a[2]); } }
            case "visible" -> { if (a.length == 3) return filter(List.of("all", "viewers", "default"), a[2]); }
            case "onend" -> { if (a.length == 3) return filter(List.of("add", "clear"), a[2]); }
            case "actor" -> {
                if (a.length == 2) return filter(List.of("add", "move", "remove"), a[1]);
                // actor <op> <cs> <id> <model> [anim] [loop]
                if (a.length == 3) return filter(plugin.store().all().keySet(), a[2]);
                CutsceneDef dd = plugin.store().get(a[2]);
                if (a.length == 4 && dd != null && !a[1].equalsIgnoreCase("add")) return filter(dd.actors.keySet(), a[3]);
                if (a.length == 5 && a[1].equalsIgnoreCase("add")) return filter(Bm.modelNames(), a[4]);
                if (a.length == 6 && a[1].equalsIgnoreCase("add")) return filter(Bm.animationNames(a[4]), a[5]);
                if (a.length == 7 && a[1].equalsIgnoreCase("add")) return filter(List.of("loop", "once", "hold"), a[6]);
            }
            case "add" -> {
                if (a.length == 3) return filter(List.of("0", "20", "40", "100"), a[2]);
                if (a.length == 4) return filter(List.of("animate", "stop", "move", "camera", "sound", "title", "command"), a[3]);
                if (d == null) return out;
                String t = a[3].toLowerCase(Locale.ROOT);
                if (t.equals("camera")) {
                    if (a.length == 5) return filter(List.of("static", "bone", "follow", "orbit", "path"), a[4]);
                    if (a.length == 6 && !a[4].equalsIgnoreCase("static") && !a[4].equalsIgnoreCase("path")) return filter(d.actors.keySet(), a[5]);
                    if (a.length == 7 && a[4].equalsIgnoreCase("bone")) { out.add("-"); out.add(plugin.settings().cameraBone); ActorDef ad = d.actors.get(a[5]); if (ad != null) { ModelActor tmp = new ModelActor(plugin, "tmp", ad.model, ad.location(), 1f); if (tmp.spawn()) { out.addAll(tmp.boneNames()); tmp.close(); } } return filter(out, a[6]); }
                    if (a.length == 6 && a[4].equalsIgnoreCase("path")) return filter(List.of("60", "100", "200"), a[5]);
                    if (a.length == 7 && a[4].equalsIgnoreCase("path")) return filter(List.of("smooth", "linear"), a[6]);
                } else if (t.equals("animate") || t.equals("stop") || t.equals("move")) {
                    if (a.length == 5) return filter(d.actors.keySet(), a[4]);
                    ActorDef ad = d.actors.get(a[4]);
                    if (t.equals("animate") && ad != null) {
                        if (a.length == 6) return filter(Bm.animationNames(ad.model), a[5]);
                        if (a.length == 7) return filter(List.of("loop", "once", "hold", "then"), a[6]);
                        if (a.length == 8) return filter(List.of("1.0", "then"), a[7]);
                        if (a[a.length - 2].equalsIgnoreCase("then")) return filter(Bm.animationNames(ad.model), a[a.length - 1]);
                        if (a.length > 3 && a[a.length - 3].equalsIgnoreCase("then")) return filter(List.of("loop", "once", "hold"), a[a.length - 1]);
                    }
                }
            }
            case "lookat" -> { if (d != null && a.length == 4) { out.add("-"); out.addAll(d.actors.keySet()); return filter(out, a[3]); } }
            case "set" -> { if (a.length == 4) return filter(List.of("transition", "duration", "ease", "spline", "speed", "loop", "then", "then-loop", "radius", "height", "degrees", "start-angle", "back", "up", "right", "volume", "pitch", "subtitle", "stay"), a[3]); }
            default -> { }
        }
        if (d != null && a.length == 3 && (sub.equals("remove") || sub.equals("at") || sub.equals("set") || sub.equals("point") || sub.equals("lookat"))) { for (int i = 0; i < d.events.size(); i++) out.add(String.valueOf(i)); return filter(out, a[2]); }
        return out;
    }

    private static List<String> filter(Collection<String> opts, String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
