package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Main class for the plugin.
 *
 * @author War Pigeon
 */
public final class QuadWars extends JavaPlugin implements Listener {
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
        playerControl = new PlayerController();
        worldControl = new WorldController(ignoredWorldKeys);
        getServer().getPluginManager().registerEvents(lobbyWorldControl, this);
        getServer().getPluginManager().registerEvents(teamControl, this);
        getServer().getPluginManager().registerEvents(worldBorderControl, this);
        getServer().getPluginManager().registerEvents(playerControl, this);
        getServer().getPluginManager().registerEvents(worldControl, this);
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
        getLogger().config("Game state is " + gameState);
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
            YamlConfiguration defaultGameStateConfig = new YamlConfiguration();
            defaultGameStateConfig.set("gameState", GameState.PREGAME.toString());
            try {
                defaultGameStateConfig.save(gameStateFile);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not save game state file", e);
            }
        }
    }
    
    /**
     * Gets the current game state.
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
                    gameState = newState;
                    gameStateConfig.set("gameState", newState.toString());
                    getServer().getPluginManager().callEvent(new GameStateChangeEvent(newState));
                    saveConfig();
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            case "quadwars" -> {
                sender.sendMessage(this + " by War Pigeon");
                return true;
            }
            case "qwtransition" -> {
                switch (gameState) {
                    case PREGAME -> {
                        sender.sendMessage(Component.text("Starting prep phase…"));
                        gameState = GameState.PREP;
                        gameStateConfig.set("gameState", GameState.PREP.toString());
                        getServer().getPluginManager()
                                .callEvent(new GameStateChangeEvent(GameState.PREP));
                        saveConfig();
                        return true;
                    }
                    case PREP -> {
                        sender.sendMessage(Component.text("Starting battle phase…"));
                        gameState = GameState.BATTLE;
                        gameStateConfig.set("gameState", GameState.BATTLE.toString());
                        getServer().getPluginManager()
                                .callEvent(new GameStateChangeEvent(GameState.BATTLE));
                        saveConfig();
                        return true;
                    }
                    case BATTLE -> {
                        sender.sendMessage(Component.text(
                                "Battle state ends when only one team " + "is left alive."));
                        return true;
                    }
                    case POSTGAME -> {
                        gameState = GameState.PREGAME;
                        gameStateConfig.set("gameState", GameState.PREGAME.toString());
                        getServer().getPluginManager()
                                .callEvent(new GameStateChangeEvent(GameState.PREGAME));
                        saveConfig();
                        return true;
                    }
                }
            }
            case "jointeam" -> {
                switch (gameState) {
                    case PREGAME, PREP -> {
                        if (args.length != 1) {
                            return false;
                        }
                        if (sender instanceof Entity entity) {
                            if (Bukkit.getScoreboardManager().getMainScoreboard()
                                    .getEntityTeam(entity) != null) {
                                sender.sendMessage(Component.text("You are already on a team."));
                            } else {
                                try {
                                    teamControl.addEntityToTeam(entity, Quadrant.valueOf(args[0]));
                                } catch (IllegalArgumentException e) {
                                    sender.sendMessage(Component.text("Invalid team name."));
                                }
                            }
                        } else {
                            sender.sendMessage(
                                    Component.text("You must be a player to join a " + "team."));
                        }
                        return true;
                    }
                    case BATTLE -> {
                        sender.sendMessage(
                                Component.text("You can't join a team during the battle."));
                        return true;
                    }
                    case POSTGAME -> {
                        sender.sendMessage(Component.text("You can't join a team after the game."));
                        return true;
                    }
                    
                }
            }
            default -> throw new UnsupportedOperationException("Command not supported.");
        }
        return false;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command, @NotNull String alias,
                                                @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "qwsetstate" -> {
                if (args.length == 1) {
                    return Arrays.stream(GameState.values()).map(GameState::toString).toList();
                } else {
                    return List.of();
                }
            }
            case "quadwars" -> {
                return List.of();
            }
        }
        return super.onTabComplete(sender, command, alias, args);
    }
    
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        gameState = event.getState();
    }
}
