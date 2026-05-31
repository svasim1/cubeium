package cubeium.cubeium.gui;

/**
 * Smooth mouse movement handler that accumulates fractional pixel movements.
 * Smooth mouse movement handler for precise panning.
 */
public class MouseSubpixelSmoother {
    private double partialX, partialY;
    private int movementX, movementY;

    public void addMovement(double moveX, double moveY) {
        moveX += partialX;
        moveY += partialY;
        partialX = moveX % 1.0;
        partialY = moveY % 1.0;
        movementX = (int) moveX;
        movementY = (int) moveY;
    }

    public int movementX() {
        return movementX;
    }

    public int movementY() {
        return movementY;
    }
    
    public void reset() {
        partialX = 0;
        partialY = 0;
        movementX = 0;
        movementY = 0;
    }
}
