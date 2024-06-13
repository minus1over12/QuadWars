package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Team;

import java.util.regex.Pattern;

/**
 * Controls the world border for players.
 * @author War Pigeon
 */
public class WorldBorderControl implements Listener {
    /**
     * The prefix for QuadWars teams.
     */
    private static final Pattern QUADWARS_PREFIX = Pattern.compile("quadwars_");
    /**
     * The current game state.
     */
    GameState gameState;
    
    /**
     * Creates a world border control object.
     * @param plugin the plugin to get the game state from
     */
    protected WorldBorderControl(QuadWars plugin) {
        gameState = plugin.getGameState();
    }
    
    /**
     * Sets the world border for joining players.
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!(player.isOp() || player.hasPermission("quadwars.gamemaster"))) {
            Team team =
                            Bukkit.getScoreboardManager().getMainScoreboard().getEntityTeam(player);
            switch (gameState) {
                case GameState.PREGAME -> {
                    //do nothing
                }
                case GameState.PREP -> {
                    if (team != null) {
                        player.setWorldBorder(makeWorldBorder(
                                Quadrant.valueOf(
                                        QUADWARS_PREFIX.matcher(team.getName()).replaceFirst("")),
                                player.getWorld()));
                    }
                }
                case GameState.BATTLE, GameState.POSTGAME -> {
                    player.setWorldBorder(null);
                }
            }
        } else {
            player.sendMessage(Component.text(
                    "This is a friendly reminder that QuadWars does not control the world border " +
                            "for ops."));
        }
    }
    
    /**
     * Sets the world borders when the game state changes.
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        gameState = event.getState();
        switch (gameState) {
            case PREP -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Team team =
                            Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
                    if (team != null) {
                        player.setWorldBorder(makeWorldBorder(
                                Quadrant.valueOf(
                                        QUADWARS_PREFIX.matcher(team.getName()).replaceFirst("")),
                                player.getWorld()));
                    }
                }
            }
            case BATTLE, POSTGAME, PREGAME -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.setWorldBorder(null);
                }
            }
        }
    }
    
    /**
     * Sets a new world border for players when they switch worlds.
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (gameState == GameState.PREP) {
            Player player = event.getPlayer();
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
            if (team != null) {
                player.setWorldBorder(makeWorldBorder(
                        Quadrant.valueOf(QUADWARS_PREFIX.matcher(team.getName()).replaceFirst("")),
                        player.getWorld()));
            }
        }
    }
    
    /**
     * Creates a new world border for a quadrant at a world's scaling.
     * @param quadrant the quadrant to lock the world border to
     * @param world the world to get scaling from
     * @return the new world border
     */
    private WorldBorder makeWorldBorder(Quadrant quadrant, World world) {
        assert gameState == GameState.PREP;
        WorldBorder worldBorder = Bukkit.createWorldBorder();
        double scale = world.getCoordinateScale();
        double size = 10_000 / scale;
        worldBorder.setCenter((size / 2) * quadrant.xSign,
                (size / 2) * quadrant.zSign);
        worldBorder.setSize(size);
        return worldBorder;
    }
}
