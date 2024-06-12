package io.github.minus1over12.quadwars;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class WorldControl implements Listener {
    @EventHandler
    public void onGameStateChange(GameStateEvent event) {
        GameState state = event.getState();
        Bukkit.getWorlds().forEach(world -> {
            switch (state) {
                case PREGAME:
                    world.setPVP(false);
                    world.setHardcore(false);
                    break;
                case PREP:
                    world.setPVP(false);
                    world.setHardcore(false);
                    break;
                case BATTLE:
                    world.setPVP(true);
                    world.setHardcore(true);
                    break;
                case POSTGAME:
                    world.setPVP(false);
                    world.setHardcore(false);
                    break;
            }
        });
    }
}
