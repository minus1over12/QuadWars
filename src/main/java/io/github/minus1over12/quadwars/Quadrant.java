package io.github.minus1over12.quadwars;

/**
 * Represents the four quadrants of the map.
 *
 * @author War Pigeon
 */
enum Quadrant {
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
    /**
     * The sign of the x coordinate.
     */
    final int xSign;
    /**
     * The sign of the z coordinate.
     */
    final int zSign;
    
    /**
     * Constructor for the enum.
     * @param xSign the sign of the x coordinate
     * @param zSign the sign of the z coordinate
     */
    Quadrant(int xSign, int zSign) {
        this.xSign = xSign;
        this.zSign = zSign;
    }
}
