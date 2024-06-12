package io.github.minus1over12.quadwars;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class GameStateEvent extends Event {
    private static HandlerList handlerList = new HandlerList();
    
    private final GameState state;
    
    protected GameStateEvent(GameState state) {
        this.state = state;
    }
    
    @Override
    public @NotNull HandlerList getHandlers() {
        return handlerList;
    }
    
    /**
     * Static method required by the API.
     * @return The handler list for this event.
     */
    public static HandlerList getHandlerList() {
        return handlerList;
    }
    
    public GameState getState() {
        return state;
    }
}
