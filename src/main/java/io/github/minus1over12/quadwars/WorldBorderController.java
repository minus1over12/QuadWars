package io.github.minus1over12.quadwars;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Controls the world border for players.
 *
 * @author War Pigeon
 */
public class WorldBorderController implements Listener {
    static final int AXIS_BUFFER_OFFSET = 128;
    /**
     * The prefix for QuadWars teams.
     */
    private static final Pattern QUADWARS_PREFIX = TeamController.QUADWARS_PREFIX;
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
    /**
     * The plugin associated with this controller.
     */
    private final Plugin plugin;
    /**
     * Set of players known to be out of bounds.
     */
    private final Set<Player> oobPlayers = ConcurrentHashMap.newKeySet();
    /**
     * The current game state.
     */
    private GameState gameState;
    
    /**
     * Creates a world border control object.
     *
     * @param plugin the plugin to get the game state from and use for scheduling events.
     * @param ignoredWorldKeys the keys of worlds to ignore.
     */
    WorldBorderController(QuadWars plugin, Collection<NamespacedKey> ignoredWorldKeys) {
        this.plugin = plugin;
        this.ignoredWorldKeys = ignoredWorldKeys;
        gameState = plugin.getGameState();
        FileConfiguration config = plugin.getConfig();
        allowEndInPrepPhase = config.getBoolean("allowEndInPrepPhase");
        worldBorderSize = config.getDouble("worldBorderSize");
        for (WorldBorder worldBorder : getWorldBorderList()) {
            worldBorder.setCenter(0, 0);
            double coordinateScale =
                    Objects.requireNonNull(worldBorder.getWorld()).getCoordinateScale();
            worldBorder.setSize(worldBorderSize * 2 / coordinateScale +
                    (AXIS_BUFFER_OFFSET * 2 / coordinateScale));
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
    
    /**
     * Sets the world border for players when they respawn.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public static void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!(event.isAnchorSpawn() || event.isBedSpawn())) {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard()
                    .getPlayerTeam(event.getPlayer());
            if (team != null) {
                Quadrant quadrant =
                        Quadrant.valueOf(QUADWARS_PREFIX.matcher(team.getName()).replaceFirst(""));
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
    
    /**
     * Schedules damage for players moving outside the world border in the prep phase, because the
     * virtual one they get won't do it for us.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (gameState == GameState.PREP) {
            Player player = event.getPlayer();
            WorldBorder worldBorder = player.getWorldBorder();
            if (worldBorder != null) {
                if (!worldBorder.isInside(getShiftedLocation(player)) &&
                        !oobPlayers.contains(player)) {
                    player.getScheduler().runAtFixedRate(plugin,
                            scheduledTask -> worldBorderDamageTask(scheduledTask, player),
                            () -> oobPlayers.remove(player), 1, 1);
                }
            }
        }
    }
    
    /**
     * Shifts a player's location if they are in a world with coordinate scaling. For use with
     * WorldBorder.isInside(Location).
     *
     * @param player the player to shift the location of
     * @return the shifted location
     */
    private Location getShiftedLocation(Player player) {
        double coordinateScale = player.getWorld().getCoordinateScale();
        // Gets a clone of the player's location. Not cloning this will cause the real player to
        // move when we call Location.add().
        Location playerLocation = player.getLocation().clone();
        WorldBorder worldBorder = player.getWorldBorder();
        // Make sure the player actually has their own world border.
        Objects.requireNonNull(worldBorder);
        // If the player is in a world with no coordinate scaling, we will just leave everything be.
        if (coordinateScale != 1.0) {
            // QuadWars' players are each in one Quadrant of the world. We will need to multiply
            // our offsets by -1 in some cases to get the right quadrant.
            Quadrant quadrant = Quadrant.valueOf(QUADWARS_PREFIX.matcher(Objects.requireNonNull(
                            Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player))
                    .getName()).replaceFirst(""));
            // This is the "reverse engineered" algorithm for how Minecraft shifts the position of
            // coordinates for world borders. You take the center, then subtract half the size of
            // the border. You then subtract the distance from the origin to the border.
            double xShift = worldBorder.getCenter().getX() -
                    quadrant.xSign * worldBorderSize / coordinateScale / 2 -
                    (AXIS_BUFFER_OFFSET / coordinateScale * quadrant.xSign);
            double zShift = worldBorder.getCenter().getZ() -
                    quadrant.zSign * worldBorderSize / coordinateScale / 2 -
                    (AXIS_BUFFER_OFFSET / coordinateScale * quadrant.zSign);
            // We then add our shifts to the player's location.
            playerLocation = playerLocation.add(xShift, 0, zShift);
        }
        return playerLocation;
        // And proceed to be sad about how long this took to figure out… ☹
    }
    
    /**
     * Damages the player until they are back inside the border. This is not an anonymous function
     * because I want to make sure I don't accidentally cache the current world border for the
     * player.
     *
     * @param scheduledTask the task that is running this method
     * @param player        the player to damage
     */
    private void worldBorderDamageTask(ScheduledTask scheduledTask, Player player) {
        WorldBorder worldBorder = player.getWorldBorder();
        if (worldBorder != null) {
            if (worldBorder.isInside(getShiftedLocation(player))) {
                oobPlayers.remove(player);
                scheduledTask.cancel();
            } else {
                player.damage(worldBorder.getDamageAmount());
            }
        } else {
            scheduledTask.cancel();
            oobPlayers.remove(player);
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
                worldBorder.setCenter((worldBorderSize / 2) * quadrant.xSign +
                                (AXIS_BUFFER_OFFSET * quadrant.xSign),
                        (worldBorderSize / 2) * quadrant.zSign +
                                (AXIS_BUFFER_OFFSET * quadrant.zSign));
                //The size does need to be scaled though. Thanks Bukkit.
                worldBorder.setSize(worldBorderSize / scale);
                worldBorder.setDamageAmount(0.5);
                player.setWorldBorder(worldBorder);
            }
        }
    }
    
    /**
     * Prevents players from traveling to the end during the prep phase if needed, since controller
     * the border there in unreasonable.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (!allowEndInPrepPhase &&
                event.getTo().getWorld().getEnvironment().equals(World.Environment.THE_END)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Processes a world border command.
     *
     * @param sender the sender of the command
     * @param args   the arguments of the command
     * @return whether the command was processed
     */
    boolean processCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0]) {
            case "add" -> {
                if (args.length < 2 || args.length > 3) {
                    return false;
                }
                double arg1;
                try {
                    arg1 = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
                if (args.length == 2) {
                    for (WorldBorder worldBorder : getWorldBorderList()) {
                        worldBorder.setSize(worldBorder.getSize() + (arg1 /
                                Objects.requireNonNull(worldBorder.getWorld())
                                        .getCoordinateScale()));
                    }
                } else {
                    long arg2;
                    try {
                        arg2 = Long.parseLong(args[2]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    for (WorldBorder worldBorder : getWorldBorderList()) {
                        worldBorder.setSize(worldBorder.getSize() + (arg1 /
                                Objects.requireNonNull(worldBorder.getWorld())
                                        .getCoordinateScale()), arg2);
                    }
                }
                return true;
            }
            case "set" -> {
                if (args.length < 2 || args.length > 3) {
                    return false;
                }
                double arg1;
                try {
                    arg1 = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
                if (args.length == 2) {
                    for (WorldBorder worldBorder : getWorldBorderList()) {
                        worldBorder.setSize(arg1 / Objects.requireNonNull(worldBorder.getWorld())
                                .getCoordinateScale());
                    }
                } else {
                    long arg2;
                    try {
                        arg2 = Long.parseLong(args[2]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    for (WorldBorder worldBorder : getWorldBorderList()) {
                        worldBorder.setSize(arg1, arg2);
                    }
                }
                return true;
            }
            case "damage" -> {
                if (args.length != 3) {
                    return false;
                }
                double arg2;
                try {
                    arg2 = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    return false;
                }
                switch (args[1]) {
                    case "amount" -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setDamageAmount(arg2);
                        }
                        return true;
                    }
                    case "buffer" -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setDamageBuffer(arg2);
                        }
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
            case "get" -> {
                for (WorldBorder worldBorder : getWorldBorderList()) {
                    sender.sendMessage(Component.text("The world border for " +
                            Objects.requireNonNull(worldBorder.getWorld()).getName() +
                            " is currently " + worldBorder.getSize() + " block(s) wide"));
                }
                return true;
            }
            case "warning" -> {
                if (args.length != 3) {
                    return false;
                }
                int arg2;
                try {
                    arg2 = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    return false;
                }
                switch (args[1]) {
                    case "time" -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setWarningTime(arg2);
                        }
                        return true;
                    }
                    case "distance" -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setWarningDistance(arg2);
                        }
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
            default -> {
                return false;
            }
        }
    }
    
    /**
     * Returns a list of world borders for all worlds except those to ignore.
     *
     * @return the list of world borders
     */
    private @NotNull List<WorldBorder> getWorldBorderList() {
        return Bukkit.getWorlds().stream()
                .filter(world -> !ignoredWorldKeys.contains(world.getKey()))
                .map(World::getWorldBorder).toList();
    }
}
