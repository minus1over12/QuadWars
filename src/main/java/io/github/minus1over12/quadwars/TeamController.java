package io.github.minus1over12.quadwars;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controls the teams in the game.
 *
 * @author War Pigeon
 */
public class TeamController implements Listener {
    /**
     * The prefixed used at the front of the team names to indicate that they are from QuadWars.
     */
    static final String TEAM_PREFIX = "quadwars_";
    /**
     * The pattern to match the QuadWars team prefix.
     */
    static final Pattern QUADWARS_PREFIX = Pattern.compile(TEAM_PREFIX);
    /**
     * Used to downshift the sounds played when a team is eliminated or the game is over when
     * playing with hardcore.
     */
    private static final float MINIMUM_PITCH = 0.5f;
    /**
     * The logger for the plugin.
     */
    private final Logger logger;
    /**
     * The default world set in the config.
     */
    private final World defaultWorld;
    /**
     * The current game state.
     */
    private GameState gameState;
    /**
     * If the plugin is running in hardcore mode.
     */
    private final boolean hardcore;
    
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
                .anyMatch(team -> !team.getName().startsWith(TEAM_PREFIX))) {
            logger.warning("QuadWars is powered by Minecraft's built-in scoreboard system. " +
                    "Minecraft only allows a player to be on one team at a time. Your server has " +
                    "teams not managed by QuadWars. These will cause problems if anything else " +
                    "tries to assign a player to them.");
        }
        FileConfiguration config = plugin.getConfig();
        for (Quadrant quadrant : Quadrant.values()) {
            String teamName = TEAM_PREFIX + quadrant;
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
            team.color(NamedTextColor.NAMES.value(
                    Objects.requireNonNull(config.getString(teamKey + "Color")).toLowerCase()));
        }
        defaultWorld = Objects.requireNonNull(
                Bukkit.getWorld(Objects.requireNonNull(config.getString("defaultWorld"))),
                "defaultWorld was not set to a valid world.");
        hardcore = config.getBoolean(QuadWars.HARDCORE_CONFIG_PATH);
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
            case PREGAME, BATTLE, POST_GAME -> {
            }
            case PREP -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Team team =
                            Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
                    if (team != null) {
                        Quadrant quadrant = Quadrant.valueOf(
                                QUADWARS_PREFIX.matcher(team.getName()).replaceFirst(""));
                        player.sendMessage(Component.text(
                                "Prep phase is starting, you are being teleported…"));
                        player.teleportAsync(defaultWorld.getHighestBlockAt(
                                                quadrant.xSign * WorldBorderController.AXIS_BUFFER_OFFSET * 2,
                                                quadrant.zSign * WorldBorderController.AXIS_BUFFER_OFFSET * 2)
                                        .getLocation().add(0, 1, 0))
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
    private void checkWinCondition(OfflinePlayer outPlayer) {
        if (hardcore && gameState == GameState.BATTLE) {
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
                                MINIMUM_PITCH), Sound.Emitter.self());
                
                Bukkit.getPluginManager().callEvent(new GameStateChangeEvent(GameState.POST_GAME));
            } else if (playerTeam != null && !aliveTeams.contains(playerTeam)) {
                Bukkit.broadcast(
                        playerTeam.displayName().append(Component.text(" has been eliminated!")));
                Bukkit.getServer().playSound(
                        Sound.sound(Key.key("entity.wither.death"), Sound.Source.MASTER, 1,
                                Math.nextUp(MINIMUM_PITCH)),
                        Sound.Emitter.self());
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
                Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM_PREFIX + quadrant),
                "Could not load a team");
        if (Bukkit.getMaxPlayers() / 4 > team.getSize()) {
            team.addEntity(entity);
            logger.info("Adding " + entity.getName() + " to team " + quadrant);
            if (gameState != GameState.PREGAME) {
                entity.teleportAsync(defaultWorld.getHighestBlockAt(
                                quadrant.xSign * WorldBorderController.AXIS_BUFFER_OFFSET * 2,
                                quadrant.zSign * WorldBorderController.AXIS_BUFFER_OFFSET * 2).getLocation()
                        .add(0, 1, 0)).thenRun(() -> {
                    if (entity instanceof HumanEntity humanEntity) {
                        humanEntity.setGameMode(GameMode.SURVIVAL);
                    }
                });
                
            }
            entity.sendMessage(
                    Component.translatable("commands.team.join.success.single", entity.name(),
                            team.displayName()));
        } else {
            entity.sendMessage(
                    Component.textOfChildren(Component.text("Team ").color(NamedTextColor.RED),
                            team.displayName(),
                            Component.text(" is full.").color(NamedTextColor.RED)));
            if (entity.isOp()) {
                entity.sendMessage(Component.text(
                        "To increase this, increase max-players in server.properties."));
            }
        }
    }
    
}
