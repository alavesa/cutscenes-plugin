package fi.alavesa.cutscenes;

import kr.toxicity.model.api.BetterModel;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cutscenes - Blockbench models (rendered by BetterModel) standing in the world or acting in cutscenes
 * with chained animations and camera shots driven by the model's own camera group.
 */
public final class CutscenesPlugin extends JavaPlugin implements Listener {

    private Settings settings;
    private CutsceneStore store;
    private WorldModels worldModels;
    private ViewerState viewerState;
    private NamespacedKey cameraKey;
    private final List<CutsceneRun> runs = new ArrayList<>();
    private final Map<UUID, CutsceneRun> viewerRuns = new HashMap<>();
    private boolean reloadHooked;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        cameraKey = new NamespacedKey(this, "camera");
        viewerState = new ViewerState(getDataFolder());
        store = new CutsceneStore(getDataFolder(), getLogger());
        store.loadAll();
        worldModels = new WorldModels(this);

        CutsceneCommand cmd = new CutsceneCommand(this);
        getCommand("cutscene").setExecutor(cmd);
        getCommand("cutscene").setTabCompleter(cmd);
        Bukkit.getPluginManager().registerEvents(this, this);

        // BetterModel loads its models during its own enable; our world models need them, so spawn next tick
        Bukkit.getScheduler().runTask(this, () -> {
            sweepStaleCameras();
            worldModels.load();
            hookReload();
            getLogger().info("BetterModel models available: " + Bm.modelNames());
        });
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("Cutscenes " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        for (CutsceneRun r : new ArrayList<>(runs)) r.end();
        runs.clear();
        viewerRuns.clear();
        if (worldModels != null) worldModels.closeAll();
        sweepStaleCameras();
    }

    private void hookReload() {
        if (reloadHooked) return;
        try {
            BetterModel.platform().addReloadEndHandler(result -> Bukkit.getScheduler().runTask(this, () -> {
                getLogger().info("BetterModel reloaded - re-creating " + worldModels.all().size() + " world model(s)" + (runs.isEmpty() ? "" : " and " + runs.size() + " running cutscene(s)"));
                worldModels.respawnAll();
                for (CutsceneRun r : runs) r.respawnActors();
            }));
            reloadHooked = true;
        } catch (Throwable t) {
            getLogger().warning("Could not hook BetterModel reload (models will not re-appear after /bettermodel reload until /cutscene reload): " + t);
        }
    }

    private void tick() {
        worldModels.tick();
        for (CutsceneRun r : new ArrayList<>(runs)) {
            try {
                r.tickOnce();
            } catch (Throwable t) {
                getLogger().severe("Cutscene '" + r.def.name + "' crashed, stopping it: " + t);
                t.printStackTrace();
                r.end();
            }
            if (r.ended()) runs.remove(r);
        }
    }

    /** Remove camera displays left behind by a crash (they are non-persistent, but a /reload keeps them). */
    private void sweepStaleCameras() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(ItemDisplay.class)) {
                if (e.getPersistentDataContainer().has(cameraKey, PersistentDataType.BYTE)) e.remove();
            }
        }
    }

    // ---------------------------------------------------------------- cutscene control

    public CutsceneRun play(CutsceneDef def, Collection<Player> players) {
        List<Player> free = new ArrayList<>();
        for (Player p : players) {
            CutsceneRun cur = viewerRuns.get(p.getUniqueId());
            if (cur != null) cur.release(p);
            free.add(p);
        }
        if (free.isEmpty()) return null;
        CutsceneRun run = new CutsceneRun(this, def, free);
        if (!run.start()) return null;
        runs.add(run);
        return run;
    }

    public List<CutsceneRun> runs() { return runs; }

    public CutsceneRun runOf(Player p) { return viewerRuns.get(p.getUniqueId()); }

    void markViewer(UUID id, CutsceneRun run) { viewerRuns.put(id, run); }

    void unmarkViewer(UUID id) { viewerRuns.remove(id); }

    public int stopAll() {
        int n = runs.size();
        for (CutsceneRun r : new ArrayList<>(runs)) r.end();
        runs.clear();
        return n;
    }

    public void reloadAll() {
        reloadConfig();
        settings = new Settings(getConfig());
        store.loadAll();
        worldModels.load();
        hookReload();
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (viewerState.has(p.getUniqueId()) && viewerRuns.get(p.getUniqueId()) == null) {
            Bukkit.getScheduler().runTask(this, () -> { if (p.isOnline() && viewerState.restore(p)) p.sendMessage("§7[Cutscenes] restored your position from before the cutscene."); });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        CutsceneRun run = viewerRuns.get(p.getUniqueId());
        if (run != null) run.release(p);
        worldModels.playerQuit(p);
    }

    /** Nobody else may pull a viewer out of spectator while the cutscene plays (we restore it ourselves). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        CutsceneRun run = viewerRuns.get(e.getPlayer().getUniqueId());
        if (run != null && !run.ended() && e.getNewGameMode() != org.bukkit.GameMode.SPECTATOR) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- accessors

    public Settings settings() { return settings; }
    public CutsceneStore store() { return store; }
    public WorldModels worldModels() { return worldModels; }
    public ViewerState viewerState() { return viewerState; }
    public NamespacedKey cameraKey() { return cameraKey; }
}
