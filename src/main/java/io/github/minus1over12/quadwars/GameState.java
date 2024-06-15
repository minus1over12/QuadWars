package io.github.minus1over12.quadwars;

/**
 * Represents the state of the game.
 *
 * @author War Pigeon
 */
public enum GameState {
    /**
     * State before the game starts.
     */
    PREGAME,
    /**
     * State during the team preparation phase. Teams are restricted to their own quadrants.
     */
    PREP,
    /**
     * Teams can move around. Hardcore is switched on.
     */
    BATTLE,
    /**
     * The game has ended.
     */
    POST_GAME
}
