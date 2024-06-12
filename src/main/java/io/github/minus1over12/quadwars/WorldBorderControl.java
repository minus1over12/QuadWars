package io.github.minus1over12.quadwars;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

public class WorldBorderControl implements Listener {
    private final Map<TeamControl.Quadrant, WorldBorder> worldBorders = HashMap.newHashMap(4);
    
    GameState gameState;
    
    protected WorldBorderControl() {
        for (TeamControl.Quadrant quadrant : TeamControl.Quadrant.values()) {
            WorldBorder worldBorder = Bukkit.createWorldBorder();
            worldBorder.setCenter(10_000 * quadrant.xSign, 10_000 * quadrant.zSign);
            worldBorder.setSize(10_000);
            worldBorders.put(quadrant, worldBorder);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() || player.hasPermission("quadwars.gamemaster")) {
            Team team =
                            Bukkit.getScoreboardManager().getMainScoreboard().getEntityTeam(player);
            switch (gameState) {
                case GameState.PREGAME -> {
                    //todo team selection
                }
                case GameState.PREP -> {
                    
                    if (team == null) {
                        //TODO player team selection
                    } else {
                        player.setWorldBorder(worldBorders.get(
                                TeamControl.Quadrant.valueOf(team.getName().split(":")[1])));
                    }
                }
                case GameState.BATTLE, GameState.POSTGAME -> {
                    if (team == null) {
                        //todo prevent new player from joining
                    }
                }
            }
        } else {
            player.sendMessage(Component.text(
                    "This is a friendly reminder that QuadWars does not control the world border " +
                            "for ops."));
        }
    }
}
