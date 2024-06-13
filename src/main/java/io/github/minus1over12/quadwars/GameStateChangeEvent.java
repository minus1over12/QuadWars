package io.github.minus1over12.quadwars;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event that is called when the game state changes.
 *
 * @author War Pigeon
 */
public class GameStateChangeEvent extends Event {
    /**
     * The handler list for this event.
     */
    private static final HandlerList HANDLER_LIST = new HandlerList();
    /**
     * The new state of the game.
     */
    private final GameState state;
    
    /**
     * Constructor for the event.
     *
     * @param state The new state of the game.
     */
    GameStateChangeEvent(GameState state) {
        this.state = state;
    }
    
    /**
     * Static method required by the API.
     * @return The handler list for this event.
     */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
    
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }
    
    /**
     * Get the new state of the game.
     * @return The new state of the game.
     */
    public GameState getState() {
        return state;
    }
}
