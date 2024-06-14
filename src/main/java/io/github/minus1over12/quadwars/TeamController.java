package io.github.minus1over12.quadwars;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controls the teams in the game.
 *
 * @author War Pigeon
 */
public class TeamController implements Listener {
    /**
     * The logger for the plugin.
     */
    private final Logger logger;
    private final World defaultWorld;
    /**
     * The current game state.
     */
    private GameState gameState;
    
    /**
     * Creates a team control object.
     *
     * @param plugin the plugin creating the object
     */
    TeamController(QuadWars plugin) {
        gameState = plugin.getGameState();
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        logger = plugin.getLogger();
        if (scoreboard.getTeams().stream()
                .anyMatch(team -> !team.getName().startsWith("quadwars_"))) {
            logger.warning("QuadWars is powered by Minecraft's built-in scoreboard system. " +
                    "Minecraft only allows a player to be on one team at a time. Your server has " +
                    "teams not managed by QuadWars. These will cause problems if anything else " +
                    "tries to assign a player to them.");
        }
        FileConfiguration config = plugin.getConfig();
        for (Quadrant quadrant : Quadrant.values()) {
            String teamName = "quadwars_" + quadrant;
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                logger.info("Creating Scoreboard team " + teamName);
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setCanSeeFriendlyInvisibles(true);
            team.setAllowFriendlyFire(false);
            String teamKey = quadrant.toString().toLowerCase();
            team.prefix(config.getRichMessage(teamKey + "Prefix"));
            team.displayName(config.getRichMessage(teamKey + "DisplayName"));
            team.suffix(config.getRichMessage(teamKey + "Suffix"));
            team.color(NamedTextColor.namedColor(config.getInt(teamKey + "Color")));
        }
        defaultWorld = Objects.requireNonNull(
                Bukkit.getWorld(Objects.requireNonNull(config.getString("defaultWorld"))),
                "defaultWorld was not set to a valid world.");
    }
    
    /**
     * Handles team events when the game state changes.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        gameState = event.getState();
        switch (gameState) {
            case PREGAME, BATTLE, POSTGAME -> {
            }
            case PREP -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Team team =
                            Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
                    if (team != null) {
                        Quadrant quadrant =
                                Quadrant.valueOf(team.getName().replaceFirst("quadwars_", ""));
                        player.teleportAsync(defaultWorld.getHighestBlockAt(quadrant.xSign * 256,
                                        quadrant.zSign * 256).getLocation().add(0, 1, 0))
                                .thenRun(() -> player.setGameMode(GameMode.SURVIVAL));
                    }
                }
            }
        }
    }
    
    /**
     * Checks if the game should end when a player dies.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        checkWinCondition(event.getEntity());
    }
    
    /**
     * Checks if the game should end when a player quits.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        checkWinCondition(event.getPlayer());
    }
    
    /**
     * Checks if the game is over.
     *
     * @param outPlayer the player that was eliminated
     */
    private void checkWinCondition(Player outPlayer) {
        if (gameState == GameState.BATTLE) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Set<Team> aliveTeams = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !player.isDead() &&
                            player.getGameMode().equals(GameMode.SURVIVAL))
                    .map(scoreboard::getPlayerTeam).filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            Team playerTeam = scoreboard.getPlayerTeam(outPlayer);
            if (aliveTeams.size() == 1) {
                Bukkit.broadcast(
                        Component.text("Team ").append(aliveTeams.iterator().next().displayName())
                                .append(Component.text(" has won!")));
                Bukkit.getServer().playSound(
                        Sound.sound(Key.key("entity.ender_dragon.death"), Sound.Source.MASTER, 1,
                                0.5f));
                
                Bukkit.getPluginManager().callEvent(new GameStateChangeEvent(GameState.POSTGAME));
            } else if (playerTeam != null && !aliveTeams.contains(playerTeam)) {
                Bukkit.broadcast(
                        playerTeam.displayName().append(Component.text(" has been eliminated!")));
                Bukkit.getServer().playSound(
                        Sound.sound(Key.key("entity.wither.death"), Sound.Source.MASTER, 1, 0.5f));
            }
        }
    }
    
    /**
     * Adds an entity to a team.
     *
     * @param entity   the entity to add
     * @param quadrant the team to add the entity to
     */
    void addEntityToTeam(Entity entity, Quadrant quadrant) {
        Team team = Objects.requireNonNull(
                Bukkit.getScoreboardManager().getMainScoreboard().getTeam("quadwars_" + quadrant),
                "Could not load a team");
        team.addEntity(entity);
        logger.info("Adding " + entity.getName() + " to team " + quadrant);
        if (gameState != GameState.PREGAME) {
            entity.teleportAsync(
                    defaultWorld.getHighestBlockAt(quadrant.xSign * 256, quadrant.zSign * 256)
                            .getLocation().add(0, 1, 0)).thenRun(() -> {
                if (entity instanceof HumanEntity humanEntity) {
                    humanEntity.setGameMode(GameMode.SURVIVAL);
                }
            });
            
        }
        entity.sendMessage(Component.text("Added you to ").append(team.displayName()));
    }
    
}
