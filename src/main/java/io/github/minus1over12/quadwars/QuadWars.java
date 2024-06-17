package io.github.minus1over12.quadwars;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.jetbrains.annotations.Nullable;

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
    static final String GAMEMASTER_PERMISSION = "quadwars.gamemaster";
    static final String HARDCORE_CONFIG_PATH = "hardcore";
    /**
     * The file to store the game state in.
     */
    private final File gameStateFile = new File(getDataFolder(), "game-state.yml");
    /**
     * The control for teams.
     */
    private TeamController teamControl;
    /**
     * The control for the lobby world.
     */
    private LobbyWorldController lobbyWorldControl;
    /**
     * The control for the world border.
     */
    private WorldBorderController worldBorderControl;
    /**
     * The control for players.
     */
    private PlayerController playerControl;
    /**
     * The control for the world.
     */
    private WorldController worldControl;
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
        getLogger().config("Game state is " + gameState);
        lobbyWorldControl = new LobbyWorldController(this);
        teamControl = new TeamController(this);
        Collection<NamespacedKey> ignoredWorldKeys = Set.of(lobbyWorldControl.getLobbyWorldKey());
        worldBorderControl = new WorldBorderController(this, ignoredWorldKeys);
        playerControl = new PlayerController(this);
        worldControl = new WorldController(ignoredWorldKeys, this);
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(lobbyWorldControl, this);
        pluginManager.registerEvents(teamControl, this);
        pluginManager.registerEvents(worldBorderControl, this);
        pluginManager.registerEvents(playerControl, this);
        pluginManager.registerEvents(worldControl, this);
        pluginManager.registerEvents(this, this);
    }
    
    @Override
    public void onDisable() {
        saveConfig();
    }
    
    @Override
    public void reloadConfig() {
        super.reloadConfig();
        gameStateConfig = YamlConfiguration.loadConfiguration(gameStateFile);
        gameState = GameState.valueOf(gameStateConfig.getString("gameState"));
    }
    
    @Override
    public void saveConfig() {
        super.saveConfig();
        try {
            gameStateConfig.save(gameStateFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save game state file", e);
        }
    }
    
    @Override
    public void saveDefaultConfig() {
        super.saveDefaultConfig();
        
        if (!gameStateFile.exists()) {
            FileConfiguration defaultGameStateConfig = new YamlConfiguration();
            defaultGameStateConfig.set("gameState", GameState.PREGAME.toString());
            try {
                defaultGameStateConfig.save(gameStateFile);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not save default game state file", e);
            }
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
            case "qwsetstate" -> {
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
            case "quadwars" -> {
                sender.sendMessage(Component.text(this + " by War Pigeon"));
                return true;
            }
            case "qwtransition" -> {
                if (!transitionLock) {
                    if (args.length == 0) {
                        transitionState(sender);
                        return true;
                    } else if (args.length == 1) {
                        BossBar progressBar = BossBar.bossBar(Component.text(switch (gameState) {
                            case PREGAME -> "Start of Prep Phase";
                            case PREP -> "Start of Battle Phase";
                            case BATTLE -> "End of Battle Phase";
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
            case "jointeam" -> {
                switch (gameState) {
                    case PREGAME, PREP -> {
                        if (args.length != 1) {
                            return false;
                        }
                        if (sender instanceof Entity entity) {
                            Scoreboard scoreboard =
                                    Bukkit.getScoreboardManager().getMainScoreboard();
                            if (scoreboard
                                    .getEntityTeam(entity) != null) {
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
                                    sender.sendMessage(Component.text("Invalid team name."));
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
            case "worldborder" -> {
                if (gameState != GameState.BATTLE) {
                    sender.sendMessage(Component.text(
                            "World border can only be changed during the battle phase."));
                    return true;
                } else {
                    return worldBorderControl.processCommand(sender, args);
                }
            }
            case "qwgetstate" -> {
                sender.sendMessage(Component.text("The current game state is " + gameState));
                return true;
            }
            default -> throw new UnsupportedOperationException("Command not supported.");
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
            case POST_GAME -> {
                Bukkit.getScheduler().runTask(this, () -> getServer().getPluginManager()
                        .callEvent(new GameStateChangeEvent(GameState.PREGAME)));
            }
        }
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command, @NotNull String alias,
                                                @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "qwsetstate" -> {
                return args.length == 1 ?
                        Arrays.stream(GameState.values()).map(GameState::toString).toList() :
                        List.of();
            }
            case "quadwars", "qwtransition", "qwgetstate" -> {
                return List.of();
            }
            case "worldborder" -> {
                if (args.length == 1) {
                    return List.of("add", "damage", "get", "set", "warning");
                }
                switch (args[0]) {
                    case "damage" -> {
                        return args.length == 2 ? List.of("amount", "buffer") : List.of();
                    }
                    case "warning" -> {
                        return args.length == 2 ? List.of("distance", "time") : List.of();
                    }
                    default -> {
                        return List.of();
                    }
                }
            }
            case "jointeam" -> {
                if (args.length == 1) {
                    return Bukkit.getScoreboardManager().getMainScoreboard().getTeams().stream()
                            .map(team -> PlainTextComponentSerializer.plainText()
                                    .serialize(team.displayName())).toList();
                }
            }
        }
        return super.onTabComplete(sender, command, alias, args);
    }
    
    /**
     * Sets the game state on changes.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        gameState = event.getState();
        gameStateConfig.set("gameState", gameState.toString());
        saveConfig();
    }
}
