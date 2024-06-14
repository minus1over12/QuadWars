package io.github.minus1over12.quadwars;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Controls the world border for players.
 *
 * @author War Pigeon
 */
public class WorldBorderController implements Listener {
    /**
     * The prefix for QuadWars teams.
     */
    private static final Pattern QUADWARS_PREFIX = Pattern.compile("quadwars_");
    /**
     * The size of the world border to use.
     */
    private final double worldBorderSize;
    /**
     * Whether to allow the end in the prep phase.
     */
    private final boolean allowEndInPrepPhase;
    /**
     * NamespacedKeys to ignore.
     */
    private final Collection<NamespacedKey> ignoredWorldKeys;
    private final Plugin plugin;
    /**
     * The current game state.
     */
    GameState gameState;
    
    /**
     * Creates a world border control object.
     *
     * @param plugin the plugin to get the game state from
     */
    WorldBorderController(QuadWars plugin, Collection<NamespacedKey> ignoredWorldKeys) {
        this.plugin = plugin;
        this.ignoredWorldKeys = ignoredWorldKeys;
        gameState = plugin.getGameState();
        FileConfiguration config = plugin.getConfig();
        allowEndInPrepPhase = config.getBoolean("allowEndInPrepPhase");
        worldBorderSize = config.getDouble("worldBorderSize");
        for (World world : Bukkit.getWorlds().stream()
                .filter(world -> !ignoredWorldKeys.contains(world.getKey())).toList()) {
            WorldBorder worldBorder = world.getWorldBorder();
            worldBorder.setCenter(0, 0);
            double coordinateScale = world.getCoordinateScale();
            worldBorder.setSize(
                    worldBorderSize * 2 / coordinateScale + (128 * 2 / coordinateScale));
        }
    }
    
    /**
     * Sets the world border for joining players.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        //I'm not sure why the WorldBorder has to be set in the next tick instead of the current
        // one, but it does.
        player.getScheduler().run(plugin, ignored -> makeWorldBorder(player), null);
    }
    
    /**
     * Sets the world borders when the game state changes.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            makeWorldBorder(player);
        }
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void setGameStateEarly(GameStateChangeEvent event) {
        gameState = event.getState();
    }
    
    /**
     * Sets a new world border for players when they switch worlds.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        makeWorldBorder(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!(event.isAnchorSpawn() || event.isBedSpawn())) {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard()
                    .getPlayerTeam(event.getPlayer());
            if (team != null) {
                Quadrant quadrant = Quadrant.valueOf(team.getName().replaceFirst("quadwars_", ""));
                event.setRespawnLocation(event.getRespawnLocation().getWorld()
                        .getHighestBlockAt(quadrant.xSign * 256, quadrant.zSign * 256).getLocation()
                        .add(0, 1, 0));
            }
        }
    }
    
    /**
     * Sets the world border for players when they respawn.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        makeWorldBorder(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!allowEndInPrepPhase &&
                event.getTo().getWorld().getEnvironment().equals(World.Environment.THE_END)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Creates a new world border for a quadrant at a world's scaling.
     *
     * @param player the player to set the world border for
     */
    private void makeWorldBorder(Player player) {
        World world = player.getWorld();
        if (gameState != GameState.PREP ||
                world.getEnvironment().equals(World.Environment.THE_END) ||
                ignoredWorldKeys.contains(world.getKey())) {
            player.setWorldBorder(null);
        } else {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
            if (team != null) {
                WorldBorder worldBorder = Bukkit.createWorldBorder();
                Quadrant quadrant =
                        Quadrant.valueOf(QUADWARS_PREFIX.matcher(team.getName()).replaceFirst(""));
                double scale = world.getCoordinateScale();
                //DO NOT SCALE THE CENTER!!
                //The API does it for you
                // The 128 part is for preventing accidental cross-quadrant portal linkage.
                worldBorder.setCenter(
                        (worldBorderSize / 2) * quadrant.xSign + (128 * quadrant.xSign),
                        (worldBorderSize / 2) * quadrant.zSign + (128 * quadrant.zSign));
                //The size does need to be scaled though. Thanks Bukkit.
                worldBorder.setSize(worldBorderSize / scale);
                player.setWorldBorder(worldBorder);
            }
        }
    }
}
