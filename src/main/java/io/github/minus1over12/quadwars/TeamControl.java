package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Objects;
import java.util.logging.Logger;

public class TeamControl {
    private final Logger logger;
    
    
    protected TeamControl(Plugin plugin) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        logger = plugin.getLogger();
        for (Quadrant quadrant : Quadrant.values()) {
            String teamName = "quadwars:" + quadrant;
            if (scoreboard.getTeam(teamName) == null) {
                logger.info("Creating Scoreboard team " + teamName);
                Team team = scoreboard.registerNewTeam(teamName);
                team.setCanSeeFriendlyInvisibles(true);
                team.setAllowFriendlyFire(false);
                //TODO: Allow setting name, prefix, and suffix from config
                team.prefix(Component.text(quadrant.toString()));
                team.displayName(Component.text(quadrant.toString()));
                team.suffix(Component.text(quadrant.toString()));
            }
        }
    }
    
    protected void addEntryToTeam(Entity entity, Quadrant quadrant) {
        Objects.requireNonNull(Bukkit.getScoreboardManager().getMainScoreboard()
                .getTeam("quadwars:" + quadrant)).addEntity(entity);
        logger.info("Adding " + entity.getName() + " to team " + quadrant);
        entity.teleportAsync(
                entity.getWorld().getHighestBlockAt(quadrant.xSign * 1024, quadrant.zSign * 1024)
                        .getLocation().add(0, 1, 0));
    }
    
    protected enum Quadrant {
        /**
         * The team in the north-west quadrant of the map.
         */
        NW(-1, -1),
        /**
         * The team in the north-east quadrant of the map.
         */
        NE(1, -1),
        /**
         * The team in the south-east quadrant of the map.
         */
        SE(1, 1),
        /**
         * The team in the south-west quadrant of the map.
         */
        SW(-1, 1);
        final int xSign;
        final int zSign;
        
        Quadrant(int xSign, int zSign) {
            this.xSign = xSign;
            this.zSign = zSign;
        }
    }
}
