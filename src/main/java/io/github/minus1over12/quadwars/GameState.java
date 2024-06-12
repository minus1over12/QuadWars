package io.github.minus1over12.quadwars;

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
    POSTGAME
}
