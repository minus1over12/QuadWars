package io.github.minus1over12.quadwars;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Main class for the plugin.
 *
 * @author War Pigeon
 */
public final class QuadWars extends JavaPlugin implements Listener {
    /**
     * The permission used for game masters.
     */
    static final String GAMEMASTER_PERMISSION = "quadwars.gamemaster";
    /**
     * The permission used for players to join a team.
     */
    static final String JOIN_TEAM_PERMISSION = "quadwars.player.jointeam";
    /**
     * The path to the hardcore configuration option.
     */
    static final String HARDCORE_CONFIG_PATH = "hardcore";
    /**
     * The path to the game state in the configuration.
     */
    private static final String GAME_STATE_PATH = "gameState";
    /**
     * The command string to set the game state.
     */
    private static final String SET_STATE_COMMAND = "qwsetstate";
    /**
     * The command string to display the plugin information.
     */
    private static final String QUADWARS_COMMAND = "quadwars";
    /**
     * The command string to transition the game state.
     */
    private static final String TRANSITION_COMMAND = "qwtransition";
    /**
     * The command string to join a team.
     */
    private static final String JOIN_TEAM_COMMAND = "jointeam";
    /**
     * The command string to get the game state.
     */
    private static final String GET_STATE_COMMAND = "qwgetstate";
    /**
     * The message to display when a command is not supported.
     */
    private static final String COMMAND_NOT_SUPPORTED = "Command not supported.";
    /**
     * The file to store the game state in.
     */
    private final File gameStateFile = new File(getDataFolder(), "game-state.yml");
    /**
     * The control for teams.
     */
    private TeamController teamControl;
    /**
     * The control for the world border.
     */
    private WorldBorderController worldBorderControl;
    /**
     * The configuration for the game state.
     */
    private YamlConfiguration gameStateConfig;
    /**
     * The current game state.
     */
    private GameState gameState;
    /**
     * Prevents multiple transitions from being queued.
     */
    private boolean transitionLock;
    
    @Override
    public void onLoad() {
        saveDefaultConfig();
        if (gameStateConfig == null) {
            reloadConfig();
        }
    }
    
    @Override
    public void onEnable() {
        // Plugin startup logic
        metrics();
        getLogger().config("Game state is " + gameState);
        LobbyWorldController lobbyWorldControl = new LobbyWorldController(this);
        teamControl = new TeamController(this);
        Collection<NamespacedKey> ignoredWorldKeys = Set.of(lobbyWorldControl.getLobbyWorldKey());
        worldBorderControl = new WorldBorderController(this, ignoredWorldKeys);
        Listener playerControl = new PlayerController(this);
        Listener worldControl = new WorldController(ignoredWorldKeys, this);
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(lobbyWorldControl, this);
        pluginManager.registerEvents(teamControl, this);
        pluginManager.registerEvents(worldBorderControl, this);
        pluginManager.registerEvents(playerControl, this);
        pluginManager.registerEvents(worldControl, this);
        pluginManager.registerEvents(this, this);
        if (Bukkit.getPluginManager().isPluginEnabled("Apollo-Bukkit")) {
            Listener lunarClientIntegration = new LunarClientIntegration();
            pluginManager.registerEvents(lunarClientIntegration, this);
        }
    }
    
    @Override
    public void onDisable() {
        saveFileConfiguration(gameStateConfig, "Could not save game state file");
    }
    
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        gameStateConfig = YamlConfiguration.loadConfiguration(gameStateFile);
        gameState = GameState.valueOf(gameStateConfig.getString(GAME_STATE_PATH));
    }
    
    @Override
    public void saveConfig() {
        super.saveConfig();
        saveFileConfiguration(gameStateConfig, "Could not save game state file");
    }
    
    @Override
    public void saveDefaultConfig() {
        super.saveDefaultConfig();
        
        if (!gameStateFile.exists()) {
            FileConfiguration defaultStateConfig = new YamlConfiguration();
            defaultStateConfig.set(GAME_STATE_PATH, GameState.PREGAME.toString());
            saveFileConfiguration(defaultStateConfig, "Could not save default game state file");
        }
    }
    
    /**
     * Saves the file configuration.
     *
     * @param gameStateConfig the configuration to save
     * @param errorMessage    the message to log if the save fails
     */
    private void saveFileConfiguration(FileConfiguration gameStateConfig, String errorMessage) {
        try {
            gameStateConfig.save(gameStateFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, errorMessage, e);
        }
    }
    
    /**
     * Gets the current game state.
     *
     * @return the current game state
     */
    GameState getGameState() {
        return gameState;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case SET_STATE_COMMAND -> {
                if (args.length != 1) {
                    return false;
                }
                try {
                    GameState newState = GameState.valueOf(args[0].toUpperCase());
                    getServer().getPluginManager().callEvent(new GameStateChangeEvent(newState));
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            case QUADWARS_COMMAND -> {
                sender.sendMessage(Component.text(this + " by War Pigeon"));
                return true;
            }
            case TRANSITION_COMMAND -> {
                if (!transitionLock) {
                    if (args.length == 0) {
                        transitionState(sender);
                        return true;
                    } else if (args.length == 1) {
                        BossBar progressBar = BossBar.bossBar(Component.text(switch (gameState) {
                            case PREGAME -> "Prep Phase Starts";
                            case PREP -> "Battle Phase Starts";
                            case BATTLE -> "Battle Phase Ends";
                            case POST_GAME -> "End of Post-Game Phase";
                        }), 0, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                        try {
                            Duration delay = Duration.ofSeconds(Long.parseLong(args[0]));
                            Instant executionTime = Instant.now();
                            Instant changeTime = executionTime.plus(delay);
                            progressBar.addViewer(getServer());
                            transitionLock = true;
                            Bukkit.getAsyncScheduler().runAtFixedRate(this, scheduledTask -> {
                                float progress = (float) (Instant.now().getEpochSecond() -
                                        executionTime.getEpochSecond()) /
                                        (changeTime.getEpochSecond() -
                                                executionTime.getEpochSecond());
                                if (progress >= 1) {
                                    transitionLock = false;
                                    scheduledTask.cancel();
                                    progressBar.removeViewer(getServer());
                                    transitionState(sender);
                                }
                                progressBar.progress(progress);
                            }, 1, 1, TimeUnit.SECONDS);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    sender.sendMessage(Component.text("A transition is already in progress.")
                            .color(NamedTextColor.RED));
                    return true;
                }
            }
            case JOIN_TEAM_COMMAND -> {
                switch (gameState) {
                    case PREGAME, PREP -> {
                        if (args.length != 1) {
                            return false;
                        }
                        if (sender instanceof Entity entity) {
                            Scoreboard scoreboard =
                                    Bukkit.getScoreboardManager().getMainScoreboard();
                            if (scoreboard.getEntityTeam(entity) != null) {
                                sender.sendMessage(Component.text("You are already on a team."));
                            } else {
                                try {
                                    teamControl.addEntityToTeam(entity, Quadrant.valueOf(
                                            scoreboard.getTeams().stream()
                                                    .map(team -> PlainTextComponentSerializer.plainText()
                                                            .serialize(team.displayName()))
                                                    .filter(displayName -> displayName.equalsIgnoreCase(
                                                            args[0])).findAny().orElseThrow()));
                                } catch (IllegalArgumentException | NoSuchElementException e) {
                                    sender.sendMessage(Component.translatable("team.notFound",
                                            Component.text(args[0])).color(NamedTextColor.RED));
                                }
                            }
                        } else {
                            sender.sendMessage(
                                    Component.text("You must be a player to join a team."));
                        }
                        return true;
                    }
                    case BATTLE -> {
                        sender.sendMessage(
                                Component.text("You can't join a team during the battle."));
                        return true;
                    }
                    case POST_GAME -> {
                        sender.sendMessage(Component.text("You can't join a team after the game."));
                        return true;
                    }
                    
                }
            }
            case WorldBorderController.WORLDBORDER_COMMAND -> {
                if (gameState != GameState.BATTLE) {
                    sender.sendMessage(Component.text(
                            "World border can only be changed during the battle phase."));
                    return true;
                } else {
                    return worldBorderControl.processCommand(sender, args);
                }
            }
            case GET_STATE_COMMAND -> {
                sender.sendMessage(Component.text("The current game state is " + gameState));
                return true;
            }
            default -> throw new UnsupportedOperationException(COMMAND_NOT_SUPPORTED);
        }
        return false;
    }
    
    /**
     * Transitions the state from one to the next.
     *
     * @param sender the sender of the command to send messages to.
     */
    private void transitionState(@NotNull Audience sender) {
        switch (gameState) {
            case PREGAME -> {
                Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager()
                        .callEvent(new GameStateChangeEvent(GameState.PREP)));
                sender.sendMessage(Component.text("Starting prep phase…"));
            }
            case PREP -> {
                Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager()
                        .callEvent(new GameStateChangeEvent(GameState.BATTLE)));
                sender.sendMessage(Component.text("Starting battle phase…"));
            }
            case BATTLE -> {
                if (getConfig().getBoolean("hardcore")) {
                    sender.sendMessage(
                            Component.text("Battle state ends when only one team is left alive."));
                } else {
                    Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager()
                            .callEvent(new GameStateChangeEvent(GameState.POST_GAME)));
                    sender.sendMessage(Component.text("Starting post-game phase…"));
                }
            }
            case POST_GAME -> Bukkit.getScheduler().runTask(this,
                    () -> getServer().getPluginManager()
                            .callEvent(new GameStateChangeEvent(GameState.PREGAME)));
        }
    }
    
    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender,
                                               @NotNull Command command, @NotNull String alias,
                                               @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case SET_STATE_COMMAND -> {
                return args.length == 1 ?
                        Arrays.stream(GameState.values()).map(GameState::toString).toList() :
                        List.of();
            }
            case QUADWARS_COMMAND, TRANSITION_COMMAND, GET_STATE_COMMAND -> {
                return List.of();
            }
            case WorldBorderController.WORLDBORDER_COMMAND -> {
                if (args.length == 1) {
                    return List.of(WorldBorderController.ADD_COMMAND,
                            WorldBorderController.DAMAGE_COMMAND, WorldBorderController.GET_COMMAND,
                            WorldBorderController.SET_COMMAND,
                            WorldBorderController.WARNING_COMMAND);
                }
                switch (args[0]) {
                    case WorldBorderController.DAMAGE_COMMAND -> {
                        return args.length == 2 ? List.of(WorldBorderController.AMOUNT_COMMAND,
                                WorldBorderController.BUFFER_COMMAND) : List.of();
                    }
                    case WorldBorderController.WARNING_COMMAND -> {
                        return args.length == 2 ? List.of(WorldBorderController.DISTANCE_COMMAND,
                                WorldBorderController.TIME_COMMAND) : List.of();
                    }
                    default -> {
                        return List.of();
                    }
                }
            }
            case JOIN_TEAM_COMMAND -> {
                return args.length == 1 ?
                        Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream()
                                .map(team -> PlainTextComponentSerializer.plainText()
                                        .serialize(team.displayName())).toList() : List.of();
            }
            default -> throw new UnsupportedOperationException(COMMAND_NOT_SUPPORTED);
        }
    }
    
    /**
     * Sets the game state on changes.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        gameState = event.getState();
        gameStateConfig.set(GAME_STATE_PATH, gameState.toString());
        saveFileConfiguration(gameStateConfig, "Could not save game state file");
    }
    
    /**
     * Sends plugin metrics to bStats.
     */
    private void metrics() {
        // All you have to do is adding the following two lines in your onEnable method.
        // You can find the plugin ids of your plugins on the page https://bstats.org/what-is-my-plugin-id
        int pluginId = 22364; // <-- Replace with the id of your plugin!
        Metrics metrics = new Metrics(this, pluginId);
    }
}
