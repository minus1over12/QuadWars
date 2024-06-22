package io.github.minus1over12.quadwars;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
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
    /**
     * How much of a gap to put between the axis and the inner edges of the world border. Used to
     * prevent accidental nether portal linking.
     */
    static final int AXIS_BUFFER_OFFSET = 128;
    /**
     * The command string for the world border.
     */
    static final String WORLDBORDER_COMMAND = "worldborder";
    /**
     * The command string for the world border damage.
     */
    static final String DAMAGE_COMMAND = "damage";
    /**
     * The command string for the world border warning.
     */
    static final String WARNING_COMMAND = "warning";
    /**
     * The command string for the world border distance.
     */
    static final String DISTANCE_COMMAND = "distance";
    /**
     * The prefix for QuadWars teams.
     */
    static final Pattern QUADWARS_PREFIX = TeamController.QUADWARS_PREFIX;
    /**
     * The command string for the world border amount.
     */
    static final String AMOUNT_COMMAND = "amount";
    /**
     * The command string for the world border buffer.
     */
    static final String BUFFER_COMMAND = "buffer";
    /**
     * The command string for getting world border sizes.
     */
    static final String GET_COMMAND = "get";
    /**
     * The command string for the world border time.
     */
    static final String TIME_COMMAND = "time";
    /**
     * The command string for the setting world border size.
     */
    static final String SET_COMMAND = "set";
    /**
     * The command string for the adding to the world border size.
     */
    static final String ADD_COMMAND = "add";
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
     * Gets the quadrant of a location.
     *
     * @param location the location to get the quadrant of
     * @return the quadrant of the location
     */
    private static Quadrant getQuadrantFromLocation(Location location) {
        if (location.getX() < 0) {
            return location.getZ() < 0 ? Quadrant.SW : Quadrant.SE;
        } else {
            return location.getZ() < 0 ? Quadrant.NW : Quadrant.NE;
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
        player.getScheduler().run(plugin, ignored -> setPlayerWorldBorder(player), null);
    }
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void setGameStateEarly(GameStateChangeEvent event) {
        gameState = event.getState();
    }
    
    /**
     * Sets the world borders when the game state changes.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onGameStateChange(GameStateChangeEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            setPlayerWorldBorder(player);
        }
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
                Quadrant quadrant = getQuadrant(team);
                event.setRespawnLocation(event.getRespawnLocation().getWorld()
                        .getHighestBlockAt(quadrant.xSign * AXIS_BUFFER_OFFSET * 2,
                                quadrant.zSign * AXIS_BUFFER_OFFSET * 2).getLocation()
                        .add(0, 1, 0));
            }
        }
    }
    
    /**
     * Sets a new world border for players when they switch worlds.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        setPlayerWorldBorder(event.getPlayer());
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
    private static Location getShiftedLocation(Player player) {
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
            Quadrant quadrant = getQuadrant(Objects.requireNonNull(
                    Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player)));
            double size = worldBorder.getSize();
            // This is the "reverse engineered" algorithm for how Minecraft shifts the position of
            // coordinates for world borders. You take the center, then subtract half the size of
            // the border. You then subtract the distance from the origin to the border.
            double xShift = worldBorder.getCenter().getX() - quadrant.xSign * size / 2 -
                    (AXIS_BUFFER_OFFSET / coordinateScale * quadrant.xSign);
            double zShift = worldBorder.getCenter().getZ() - quadrant.zSign * size / 2 -
                    (AXIS_BUFFER_OFFSET / coordinateScale * quadrant.zSign);
            // We then add our shifts to the player's location.
            playerLocation = playerLocation.add(xShift, 0, zShift);
        }
        return playerLocation;
        // And proceed to be sad about how long this took to figure out… ☹
    }
    
    /**
     * Gets the quadrant of a team.
     *
     * @param team the team to get the quadrant of
     * @return the quadrant of the team
     */
    private static @NotNull Quadrant getQuadrant(Team team) {
        return Quadrant.valueOf(QUADWARS_PREFIX.matcher(team.getName()).replaceFirst(""));
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
            Location shiftedLocation = getShiftedLocation(player);
            if (worldBorder.isInside(shiftedLocation)) {
                oobPlayers.remove(player);
                scheduledTask.cancel();
            } else {
                Quadrant quadrant = getQuadrant(Objects.requireNonNull(
                        Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player)));
                double size = worldBorder.getSize();
                double xMin = quadrant.xSign *
                        Math.min(Math.abs(worldBorder.getCenter().getX() - size / 2),
                                Math.abs(worldBorder.getCenter().getX() + size / 2));
                double zMin = quadrant.zSign *
                        Math.min(Math.abs(worldBorder.getCenter().getX() - size / 2),
                                Math.abs(worldBorder.getCenter().getX() + size / 2));
                double distance =
                        Math.hypot(Math.min(0, Math.abs(shiftedLocation.getX()) - Math.abs(xMin)),
                                Math.min(0, Math.abs(shiftedLocation.getZ()) - Math.abs(zMin)));
                
                player.damage(worldBorder.getDamageAmount() *
                        Math.floor(distance / worldBorder.getDamageBuffer()));
            }
        } else {
            scheduledTask.cancel();
            oobPlayers.remove(player);
        }
    }
    
    /**
     * Sets the world border for players when they respawn.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onPlayerPostRespawn(PlayerPostRespawnEvent event) {
        setPlayerWorldBorder(event.getPlayer());
    }
    
    /**
     * Creates a new world border for a quadrant at a world's scaling.
     *
     * @param player the player to set the world border for
     */
    private void setPlayerWorldBorder(Player player) {
        World world = player.getWorld();
        if (gameState != GameState.PREP ||
                world.getEnvironment().equals(World.Environment.THE_END) ||
                ignoredWorldKeys.contains(world.getKey())) {
            player.setWorldBorder(null);
        } else {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(player);
            if (team != null) {
                WorldBorder worldBorder = makeWorldBorder(getQuadrant(team), world);
                player.setWorldBorder(worldBorder);
                if (Bukkit.getPluginManager().isPluginEnabled("Apollo-Bukkit")) {
                    player.getScheduler().runDelayed(plugin,
                            scheduledTask -> LunarClientIntegration.setWorldBorders(player,
                                    worldBorder.getSize(), getQuadrant(team), ignoredWorldKeys),
                            null, 10);
                }
            } else if (Bukkit.getPluginManager().isPluginEnabled("Apollo-Bukkit") &&
                    player.hasPermission(QuadWars.GAMEMASTER_PERMISSION)) {
                player.getScheduler().runDelayed(plugin,
                        scheduledTask -> LunarClientIntegration.setGameMasterWorldBorders(player,
                                worldBorderSize / world.getCoordinateScale(), ignoredWorldKeys),
                        null, 10);
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
     * Sends a message to the sender about the world border being set.
     *
     * @param sender      the sender to send the message to
     * @param worldBorder the world border to send the message about
     */
    private static void sendWorldBorderSetMessage(@NotNull Audience sender,
                                                  WorldBorder worldBorder) {
        sender.sendMessage(Component.textOfChildren(
                Component.text(Objects.requireNonNull(worldBorder.getWorld()).getName() + ": "),
                Component.translatable("commands.worldborder.set.immediate",
                        Component.text(worldBorder.getSize()))));
    }
    
    /**
     * Sets the size of a world border over time.
     *
     * @param sender       the sender to send the message to
     * @param arg2         the time to take to set the border
     * @param worldBorder  the world border to set the size of
     * @param originalSize the original size of the world border
     * @param newSize      the new size of the world border
     */
    private static void setSizeOverTime(@NotNull Audience sender, long arg2,
                                        WorldBorder worldBorder, double originalSize,
                                        double newSize) {
        worldBorder.setSize(newSize, arg2);
        Component message;
        if (newSize > originalSize) {
            message =
                    Component.translatable("commands.worldborder.set.grow", Component.text(newSize),
                            Component.text(arg2));
        } else if (newSize < originalSize) {
            message = Component.translatable("commands.worldborder.set.shrink",
                    Component.text(newSize), Component.text(arg2));
        } else {
            message = Component.translatable("commands.worldborder.set.failed.nochange");
        }
        sender.sendMessage(Component.textOfChildren(
                Component.text(Objects.requireNonNull(worldBorder.getWorld()).getName() + ": "),
                message));
    }
    
    /**
     * Processes a world border command.
     *
     * @param sender the sender of the command
     * @param args   the arguments of the command
     * @return whether the command was processed
     */
    boolean processCommand(@NotNull Audience sender, @NotNull String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0]) {
            case ADD_COMMAND -> {
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
                        sendWorldBorderSetMessage(sender, worldBorder);
                    }
                } else {
                    long arg2;
                    try {
                        arg2 = Long.parseLong(args[2]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    for (WorldBorder worldBorder : getWorldBorderList()) {
                        double originalSize = worldBorder.getSize();
                        double newSize = originalSize + (arg1 /
                                Objects.requireNonNull(worldBorder.getWorld())
                                        .getCoordinateScale());
                        setSizeOverTime(sender, arg2, worldBorder, originalSize, newSize);
                    }
                }
                return true;
            }
            case SET_COMMAND -> {
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
                        sendWorldBorderSetMessage(sender, worldBorder);
                    }
                } else {
                    long arg2;
                    try {
                        arg2 = Long.parseLong(args[2]);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    for (WorldBorder worldBorder : getWorldBorderList()) {
                        double originalSize = worldBorder.getSize();
                        double newSize = arg1 /
                                Objects.requireNonNull(worldBorder.getWorld()).getCoordinateScale();
                        setSizeOverTime(sender, arg2, worldBorder, originalSize, newSize);
                    }
                }
                return true;
            }
            case DAMAGE_COMMAND -> {
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
                    case AMOUNT_COMMAND -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setDamageAmount(arg2);
                        }
                        sender.sendMessage(
                                Component.translatable("commands.worldborder.damage.amount.success",
                                        Component.text(arg2)));
                        return true;
                    }
                    case BUFFER_COMMAND -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setDamageBuffer(arg2);
                        }
                        sender.sendMessage(
                                Component.translatable("commands.worldborder.damage.buffer.success",
                                        Component.text(arg2)));
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
            case GET_COMMAND -> {
                for (WorldBorder worldBorder : getWorldBorderList()) {
                    sender.sendMessage(Component.textOfChildren(Component.text(
                                    Objects.requireNonNull(worldBorder.getWorld()).getName() + ':' + ' '),
                            Component.translatable("commands.worldborder.get",
                                    Component.text(worldBorder.getSize()))));
                }
                return true;
            }
            case WARNING_COMMAND -> {
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
                    case TIME_COMMAND -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setWarningTime(arg2);
                        }
                        sender.sendMessage(
                                Component.translatable("commands.worldborder.warning.time.success",
                                        Component.text(arg2)));
                        return true;
                    }
                    case DISTANCE_COMMAND -> {
                        for (WorldBorder worldBorder : getWorldBorderList()) {
                            worldBorder.setWarningDistance(arg2);
                        }
                        sender.sendMessage(Component.translatable(
                                "commands.worldborder.warning.distance.success",
                                Component.text(arg2)));
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
    
    /**
     * Creates a new world border.
     *
     * @param quadrant the quadrant to make the world border for
     * @param world    the world to make the world border in
     * @return the new world border
     */
    private @NotNull WorldBorder makeWorldBorder(Quadrant quadrant, World world) {
        WorldBorder worldBorder = Bukkit.createWorldBorder();
        double scale = world.getCoordinateScale();
        //DO NOT SCALE THE CENTER!!
        //The API does it for you
        // The 128 part is for preventing accidental cross-quadrant portal linkage.
        worldBorder.setCenter(
                (worldBorderSize / 2) * quadrant.xSign + (AXIS_BUFFER_OFFSET * quadrant.xSign),
                (worldBorderSize / 2) * quadrant.zSign + (AXIS_BUFFER_OFFSET * quadrant.zSign));
        //The size does need to be scaled though. Thanks Bukkit.
        worldBorder.setSize(worldBorderSize / scale);
        worldBorder.setDamageAmount(0.5);
        return worldBorder;
    }
    
    /**
     * Prevents pistons from moving outside active team quadrants.
     *
     * @param event the event that triggered this method
     */
    private void onBlockPistonEventHelper(BlockPistonEvent event) {
        if (gameState == GameState.PREP) {
            Block piston = event.getBlock();
            if (Arrays.stream(Quadrant.values()).noneMatch(
                    quadrant -> makeWorldBorder(quadrant, piston.getWorld()).isInside(
                            piston.getLocation()))) {
                event.setCancelled(true);
            }
        }
    }
    
    /**
     * Sends events to the method that prevents pistons from moving outside active team quadrants.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onBlockPistonEvent(BlockPistonExtendEvent event) {
        onBlockPistonEventHelper(event);
    }
    
    /**
     * Sends events to the method that prevents pistons from moving outside active team quadrants.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onBlockPistonEvent(BlockPistonRetractEvent event) {
        onBlockPistonEventHelper(event);
    }
    
    /**
     * Prevents projectiles from doing damage outside their spawn quadrant in prep phase.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onProjectileHitEvent(ProjectileHitEvent event) {
        cancelOOBEntityEventIfNeeded(event);
    }
    
    /**
     * Prevents entities from exploding outside their spawn quadrant in prep phase.
     *
     * @param event the event that triggered this method
     * @param <T>   the type of event
     */
    private <T extends EntityEvent & Cancellable> void cancelOOBEntityEventIfNeeded(T event) {
        if (gameState == GameState.PREP) {
            Entity projectile = event.getEntity();
            Location origin = projectile.getOrigin();
            if (origin != null) {
                Quadrant spawnQuadrant = getQuadrantFromLocation(origin);
                if (spawnQuadrant != null) {
                    if (!getQuadrantFromLocation(projectile.getLocation()).equals(spawnQuadrant)) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
    
    /**
     * Prevents entities from exploding outside their spawn quadrant in prep phase.
     *
     * @param event the event that triggered this method
     */
    @EventHandler
    public void onEntityExplodeEvent(EntityExplodeEvent event) {
        cancelOOBEntityEventIfNeeded(event);
    }
}
