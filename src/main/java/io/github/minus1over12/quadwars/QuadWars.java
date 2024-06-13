package io.github.minus1over12.quadwars;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * Main class for the plugin.
 *
 * @author War Pigeon
 */
public final class QuadWars extends JavaPlugin {
    /**
     * The file to store the game state in.
     */
    private final File gameStateFile = new File(getDataFolder(), "game-state.yml");
    /**
     * The control for teams.
     */
    private TeamControl teamControl;
    /**
     * The control for the lobby world.
     */
    private LobbyWorldControl lobbyWorldControl;
    /**
     * The control for the world border.
     */
    private WorldBorderControl worldBorderControl;
    /**
     * The control for players.
     */
    private PlayerControl playerControl;
    /**
     * The control for the world.
     */
    private WorldControl worldControl;
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
        
        lobbyWorldControl = new LobbyWorldControl(this);
        teamControl = new TeamControl(this);
        worldBorderControl = new WorldBorderControl(this);
        playerControl = new PlayerControl();
        worldControl = new WorldControl();
        getServer().getPluginManager().registerEvents(lobbyWorldControl, this);
        getServer().getPluginManager().registerEvents(teamControl, this);
        getServer().getPluginManager().registerEvents(worldBorderControl, this);
        getServer().getPluginManager().registerEvents(playerControl, this);
        getServer().getPluginManager().registerEvents(worldControl, this);
    }

    @Override
    public void onDisable() {
        saveConfig();
        lobbyWorldControl.unloadLobby();
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
        }
        return false;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command, @NotNull String alias,
                                                @NotNull String[] args) {
        return super.onTabComplete(sender, command, alias, args);
    }
}
