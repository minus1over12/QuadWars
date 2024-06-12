package io.github.minus1over12.quadwars;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerControl implements Listener {
    @EventHandler
    public void onGameStateChange(GameStateEvent event) {
        GameState state = event.getState();
        switch (state) {
            case PREGAME:
                // Do nothing
                break;
            case PREP:
                // Do nothing
                break;
            case BATTLE:
                // Do nothing
                Bukkit.getOnlinePlayers().forEach(player -> player.playSound(Sound.sound(Key.key("event.raid.horn"), Sound.Source.MASTER, 1, 1)));
            case POSTGAME:
                // Do nothing
                break;
        }
    }
    
}
