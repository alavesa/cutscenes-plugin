# Cutscenes — Blockbench actors and camera cutscenes on BetterModel (Paper 26.x)

Spawn `.bbmodel` models into the world, animate them, and play cutscenes: viewers are put into
spectator mode and their camera is driven by shots you define — a camera group inside the
Blockbench model, a fixed position, a waypoint path, an orbit, or a chase cam. Animations can be
switched mid-clip or chained so the next one starts when the current one ends.

Rendering, resource pack and model loading are **BetterModel's**: this plugin talks to the
BetterModel API directly, so the models are the same ones `/bettermodel spawn` and
`/bettermodel play` use. No second pack, no `.bbmodel` conversion of its own.

## Install

1. Server: Paper 26.3 (26.2 also fine), Java 25+. Install **BetterModel 3.5.0 or newer**
   (3.5.0 is the first release with 26.3 support) — <https://github.com/toxicity188/BetterModel/releases>.
2. Drop `Cutscenes-<version>.jar` into `plugins/`. Remove any older `Cutscenes-*.jar` first —
   Paper loads only the first jar with the same plugin name.
3. Put your models into `plugins/BetterModel/models/` (`scp173.bbmodel` → model `scp173`),
   run `/bettermodel reload`. BetterModel builds and serves the resource pack exactly as before.
4. Check the console for `Enabling Cutscenes v<version>` and
   `BetterModel models available: [...]`.

Commands: `/cutscene` or `/cs` (permission `cutscenes.admin`, default op). `/cs help`.

## Models in the world

| Command | What it does |
|---|---|
| `/cs model spawn <model> [animation] [loop\|once\|hold] [scale]` | spawn a model where you stand, facing your way |
| `/cs model animate <id> <animation> [loop\|once\|hold] [speed]` | play / switch an animation |
| `/cs model stop <id>` | stop the animation |
| `/cs model move <id> [scale]` | move it to you |
| `/cs model bones <id>` | list its bones with their current world positions (find your camera group) |
| `/cs model list`, `/cs model remove <id\|nearest\|all>` | |

World models persist in `plugins/Cutscenes/models.yml`, are shown to everyone within
`models.spawn-radius` and survive `/bettermodel reload`.

## Cutscenes

A cutscene = **actors** (models placed at fixed positions, with an optional starting animation)
+ a **timeline** of events at ticks (20 ticks = 1 s). Build one in game:

```
/cs create intro
/cs actor add intro scp scp173 idle loop        (stand where the model should be, facing its direction)
/cs add intro 0   camera bone scp               (camera rides the model's "camera" group)
/cs add intro 0   animate scp walk once then idle
/cs add intro 60  animate scp attack once       (switch mid-animation: hard cut to the new clip)
/cs add intro 100 camera orbit scp 5 2 360 100 10
/cs add intro 200 camera path 80 smooth 10      (first waypoint = your eye position)
   ... walk somewhere ... /cs point intro 4      (append the next waypoint to event #4)
/cs lookat intro 4 scp head                     (the path keeps looking at the scp's head bone)
/cs add intro 150 title &6SCP-173|&7Containment breach
/cs add intro 150 sound minecraft:entity.wither.spawn 1 0.6
/cs add intro 280 command say %player% has seen it
/cs info intro
/cs play intro            (yourself)   /cs play intro all   /cs play intro radius:20   /cs play intro Steve
/cs stop                  (leave it)    /cs stop all
```

Everything lands in `plugins/Cutscenes/cutscenes/intro.yml`, which you can also write by hand
(then `/cs reload`). `/cs info <name>` numbers the events; `/cs remove|at|set <name> <event#> …`
edit them.

### Camera shots

Each `camera` event replaces the previous shot. `transition: <ticks>` (last argument of every
camera sub-command) blends from where the previous shot left off.

| Shot | Arguments | Behaviour |
|---|---|---|
| `static` | `[transition]` | fixed; taken from your eye position and view direction |
| `bone` | `<actor> [bone\|-] [transition]` | rides a bone of the model, by default the **camera group** (config `camera.bone-name`, default `camera`). Points at the model's `camera_target` group if it has one, or at a `/cs lookat` target, otherwise along the camera group's forward axis (`camera.forward-axis`, default `+z` = Blockbench north; set `-z` if it looks backwards) |
| `follow` | `<actor> [back up right] [transition]` | chase cam behind/above the actor, looking at it |
| `orbit` | `<actor> [radius height degrees duration] [transition]` | circles the actor (negative degrees = other way) looking at it |
| `path` | `<duration> [smooth\|linear] [transition]` + `/cs point` | travels through waypoints (Catmull-Rom or straight lines), yaw/pitch interpolated between the waypoints unless a `/cs lookat` target is set |

Easing of `orbit`/`path`: `ease: linear | smooth | ease-in | ease-out` (default smooth) via
`/cs set <cs> <event#> ease smooth`.

### Making a camera group in Blockbench

Add an empty group named **`camera`** (any position/rotation, animate it like any bone).
Optionally add an empty group **`camera_target`**; when present the camera always looks at it,
which is the most robust way to aim (no axis conventions involved). Both names are configurable.

### Animation events

* `animate <actor> <animation> [loop|once|hold] [speed] [then <animation> [loop|once|hold]]` —
  starts the clip now, replacing whatever runs (that is how you change an animation mid-clip).
  `then` starts the next clip the moment this one ends; it only fires for `once` clips
  (`loop` never ends, `hold` freezes on the last frame), so `then` sets `once` automatically.
* `stop <actor>` — stop the running animation.
* `move <actor> [duration]` — glide the whole model to where you stand (eased).

### Other events

`sound <sound> [volume] [pitch]` (at the camera, or at an actor with `set … actor <id>`),
`title <title>|<subtitle>` (`&` colour codes, `fade-in`/`stay`/`fade-out` ticks via `set`),
`command <console command…>` (`%player%` runs it once per viewer),
`/cs onend <cs> add <command…>` for commands when the cutscene finishes.

### Viewers

Viewers are saved (game mode, position, flight), switched to spectator, locked to an invisible
camera entity (a display entity, so the client interpolates both position and rotation between
server ticks), and restored when the cutscene ends, when they are released with `/cs stop`, or
on their next join if the server crashed meanwhile (`restore.yml`). Game-mode changes by other
plugins are blocked while a cutscene runs. By default only viewers see the actors
(`cutscenes.actors-visible-to-all`, or per cutscene `/cs visible <cs> all|viewers`).

## File format

```yaml
world: world
length: -1            # ticks; -1 = computed from the last event + cutscenes.default-tail
actors:
  scp: {model: scp173, world: world, x: 10.5, y: 64, z: -3.5, yaw: 90, pitch: 0, animation: idle, loop: loop, scale: 1.0}
timeline:
  - {at: 0,   type: camera, camera: bone, actor: scp}                       # bone: head, transition: 10
  - {at: 0,   type: animate, actor: scp, animation: walk, loop: once, speed: 1.0, then: idle, then-loop: loop}
  - {at: 60,  type: camera, camera: static, world: world, x: 1, y: 66, z: 2, yaw: -90, pitch: 10, transition: 20}
  - {at: 100, type: camera, camera: orbit, actor: scp, radius: 5, height: 2, start-angle: 0, degrees: 360, duration: 100, ease: smooth}
  - {at: 200, type: camera, camera: path, duration: 80, spline: smooth, points: ["1,66,2,-90,10", "5,67,2,-120,15"], look-actor: scp, look-bone: head}
  - {at: 200, type: camera, camera: follow, actor: scp, back: 4, up: 2, right: 0}
  - {at: 150, type: title, title: "&6SCP-173", subtitle: "&7...", fade-in: 10, stay: 40, fade-out: 10}
  - {at: 150, type: sound, sound: minecraft:entity.wither.spawn, volume: 1, pitch: 0.6}
  - {at: 160, type: move, actor: scp, x: 12, y: 64, z: -3.5, yaw: 90, pitch: 0, duration: 40}
  - {at: 280, type: command, command: "say %player% saw it"}
on-end: ["give %player% minecraft:paper"]
```

## Config (`config.yml`)

`camera.bone-name` (camera), `camera.target-bone` (camera_target), `camera.forward-axis` (+z),
`camera.teleport-duration` (1), `models.spawn-radius` (64), `cutscenes.actors-visible-to-all`
(false), `cutscenes.camera-lock-delay` (2), `cutscenes.default-tail` (20).

## Build

```
mvn install:install-file -Dfile=bettermodel-3.5.0-paper.jar -DgroupId=kr.toxicity.model -DartifactId=bettermodel -Dversion=3.5.0 -Dpackaging=jar -DgeneratePom=true
mvn package        # target/Cutscenes-<version>.jar
```
