package io.github.minus1over12.quadwars;

import org.bukkit.plugin.java.JavaPlugin;

public final class QuadWars extends JavaPlugin {
    TeamControl teamControl;
    
    
    @Override
    public void onEnable() {
        // Plugin startup logic
        teamControl = new TeamControl(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
