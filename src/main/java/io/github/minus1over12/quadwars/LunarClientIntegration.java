package io.github.minus1over12.quadwars;

import com.lunarclient.apollo.Apollo;
import com.lunarclient.apollo.common.cuboid.Cuboid2D;
import com.lunarclient.apollo.module.border.Border;
import com.lunarclient.apollo.module.border.BorderModule;
import com.lunarclient.apollo.module.serverrule.ServerRuleModule;
import com.lunarclient.apollo.option.Options;
import com.lunarclient.apollo.player.ApolloPlayer;
import com.lunarclient.apollo.recipients.Recipients;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collection;

/**
 * Integrates QuadWars with Lunar Client using their Apollo plugin.
 *
 * @author War Pigeon
 */
public class LunarClientIntegration implements Listener {
    
    
    /**
     * Sets the world borders for the player for the other teams.
     *
     * @param player           The player to set the borders for.
     * @param size             The size of the world.
     * @param homeQuadrant     The quadrant the player's team is in.
     * @param ignoredWorldKeys The keys of the worlds to ignore.
     */
    static void setWorldBorders(Entity player, double size, Quadrant homeQuadrant,
                                Collection<NamespacedKey> ignoredWorldKeys) {
        World world = player.getWorld();
        if (!(world.getEnvironment().equals(World.Environment.THE_END) ||
                ignoredWorldKeys.contains(world.getKey()))) {
            Apollo.getPlayerManager().getPlayer(player.getUniqueId()).ifPresent(apolloPlayer -> {
                BorderModule borderModule = Apollo.getModuleManager().getModule(BorderModule.class);
                for (Quadrant quadrant : Arrays.stream(Quadrant.values())
                        .filter(quadrant -> !quadrant.equals(homeQuadrant)).toList()) {
                    makeBorder(size, apolloPlayer, quadrant, world, borderModule, true);
                }
            });
        }
    }
    
    /**
     * Makes a border for the given player in the given quadrant.
     *
     * @param size         The size of the world.
     * @param apolloPlayer The player to set the border for.
     * @param quadrant     The quadrant to set the border in.
     * @param world        The world to set the border in.
     * @param borderModule The border module to use.
     * @param cancelEntry  whether to cancel entry into the border.
     */
    private static void makeBorder(double size, Recipients apolloPlayer, Quadrant quadrant,
                                   World world, BorderModule borderModule, boolean cancelEntry) {
        double minCorner = WorldBorderController.AXIS_BUFFER_OFFSET / world.getCoordinateScale();
        double maxCorner = size + minCorner;
        // https://minecraft.wiki/w/Miscellaneous_colors#World_border
        borderModule.displayBorder(apolloPlayer,
                Border.builder().id("qw" + quadrant + world.getName()).world(world.getName())
                        .cancelEntry(cancelEntry).cancelExit(false).canShrinkOrExpand(false)
                        .color(new Color(0x20, 0xA0, 0xFF))
                        .bounds(Cuboid2D.builder().minX(quadrant.xSign * minCorner)
                                .minZ(quadrant.zSign * minCorner).maxX(quadrant.xSign * maxCorner)
                                .maxZ(quadrant.zSign * maxCorner).build()).build());
    }
    
    /**
     * Sets the world borders for the game master, allowing travel through the borders.
     *
     * @param player           The player to set the borders for.
     * @param size             The size of the world.
     * @param ignoredWorldKeys The keys of the worlds to ignore.
     */
    static void setGameMasterWorldBorders(Entity player, double size,
                                          Collection<NamespacedKey> ignoredWorldKeys) {
        World world = player.getWorld();
        if (!(world.getEnvironment().equals(World.Environment.THE_END) ||
                ignoredWorldKeys.contains(world.getKey()))) {
            Apollo.getPlayerManager().getPlayer(player.getUniqueId()).ifPresent(apolloPlayer -> {
                BorderModule borderModule = Apollo.getModuleManager().getModule(BorderModule.class);
                for (Quadrant quadrant : Quadrant.values()) {
                    makeBorder(size, apolloPlayer, quadrant, world, borderModule, false);
                }
            });
        }
    }
    
    /**
     * Changes the client's competitive game option based on the game state.
     *
     * @param event The event that triggered this method.
     */
    @EventHandler
    public static void onGameStateChange(GameStateChangeEvent event) {
        GameState state = event.getState();
        Options options = Apollo.getModuleManager().getModule(ServerRuleModule.class).getOptions();
        options.set(ServerRuleModule.COMPETITIVE_GAME, state.equals(GameState.BATTLE));
        if (!state.equals(GameState.PREP)) {
            for (ApolloPlayer player : Apollo.getPlayerManager().getPlayers()) {
                Apollo.getModuleManager().getModule(BorderModule.class).resetBorders(player);
            }
        }
    }
}
