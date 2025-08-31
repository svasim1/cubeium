package cubeium.cubeium.rendering.viewport;

import cubeium.cubeium.rendering.MapRenderer;

import java.awt.event.*;

/**
 * Manages viewport interactions for the map renderer.
 * Handles mouse and keyboard input for panning, zooming, and navigation.
 */
public class MapViewportManager {
    
    // Interaction settings
    private static final double PAN_SENSITIVITY = 1.0;
    private static final int SMOOTH_PAN_STEPS = 10;
    private static final int ANIMATION_DELAY_MS = 16; // ~60 FPS
    
    // Enhanced drag settings
    private static final double MOMENTUM_DECAY = 0.85; // How quickly momentum fades (0.0-1.0)
    private static final double MIN_MOMENTUM_THRESHOLD = 0.5; // Minimum velocity to continue momentum
    private static final int MOMENTUM_HISTORY_SIZE = 3; // Number of samples for velocity calculation
    private static final double ADAPTIVE_SENSITIVITY_MIN = 0.5;
    private static final double ADAPTIVE_SENSITIVITY_MAX = 2.0;
    
    // Enhanced zoom settings
    private static final double SMOOTH_ZOOM_DURATION_MS = 300.0; // Duration for smooth zoom animation
    private static final double ZOOM_ACCELERATION_THRESHOLD = 0.2; // Time threshold for zoom acceleration
    private static final double MAX_ZOOM_ACCELERATION = 3.0; // Maximum zoom steps per wheel event
    private static final double ZOOM_PRECISION_STEPS = 10.0; // Steps for smooth zoom interpolation
    
    // Keyboard movement speeds (blocks per second)
    private static final double KEYBOARD_PAN_SPEED = 50.0;
    private static final double FAST_PAN_MULTIPLIER = 5.0;
    private static final double FINE_PAN_MULTIPLIER = 0.2; // Ctrl modifier for fine control
    private static final double KEYBOARD_ACCELERATION_TIME = 2.0; // Seconds to reach full speed
    private static final double MAX_KEYBOARD_SPEED_MULTIPLIER = 3.0; // Maximum acceleration
    
    // Additional keyboard controls
    private static final double KEYBOARD_ZOOM_SPEED = 0.5; // Zoom levels per second
    
    private final MapRenderer mapRenderer;
    private final ZoomConstraintsManager zoomConstraintsManager;
    
    // Mouse interaction state
    private boolean isDragging = false;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    
    // Enhanced drag tracking
    private long lastDragTime = 0;
    private double dragVelocityX = 0.0;
    private double dragVelocityY = 0.0;
    private double[] dragHistoryX = new double[MOMENTUM_HISTORY_SIZE];
    private double[] dragHistoryY = new double[MOMENTUM_HISTORY_SIZE];
    private long[] dragTimeHistory = new long[MOMENTUM_HISTORY_SIZE];
    private int dragHistoryIndex = 0;
    private boolean momentumActive = false;
    private Thread momentumThread;
    
    // Enhanced zoom state
    private boolean smoothZoomActive = false;
    private Thread smoothZoomThread;
    private long lastZoomTime = 0;
    private int zoomAcceleration = 1;
    private double targetZoomLevel = 0;
    private double currentZoomLevel = 0;
    private double zoomCenterX = 0;
    private double zoomCenterY = 0;
    
    // Animation state
    private volatile boolean isAnimating = false;
    private Thread animationThread;
    
    // Keyboard state
    private boolean keyUp = false, keyDown = false, keyLeft = false, keyRight = false;
    private boolean fastPanMode = false; // Shift modifier
    private boolean finePanMode = false; // Ctrl modifier
    private boolean keyZoomIn = false, keyZoomOut = false; // Continuous zoom keys
    private Thread keyboardPanThread;
    private long keyboardStartTime = 0; // For acceleration tracking
    private double currentKeyboardSpeedMultiplier = 1.0;
    
    // Viewport constraints
    private double minWorldX = Double.NEGATIVE_INFINITY;
    private double maxWorldX = Double.POSITIVE_INFINITY;
    private double minWorldZ = Double.NEGATIVE_INFINITY;
    private double maxWorldZ = Double.POSITIVE_INFINITY;
    
    /**
     * Create a MapViewportManager
     * @param mapRenderer Map renderer to control
     */
    public MapViewportManager(MapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;
        this.zoomConstraintsManager = new ZoomConstraintsManager(mapRenderer);
        startKeyboardPanThread();
    }
    
    // ===============================
    // Mouse Event Handlers
    // ===============================
    
    /**
     * Handle mouse press event
     * @param e Mouse event
     */
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) { // Left click
            startDrag(e.getX(), e.getY());
        } else if (e.getButton() == MouseEvent.BUTTON3) { // Right click
            // Center view on clicked position
            double[] worldPos = mapRenderer.screenToWorld(e.getX(), e.getY());
            animateToPosition(worldPos[0], worldPos[1], mapRenderer.getZoomLevel());
        }
    }
    
    /**
     * Handle mouse drag event
     * @param e Mouse event
     */
    public void mouseDragged(MouseEvent e) {
        if (isDragging) {
            updateDrag(e.getX(), e.getY());
        }
    }
    
    /**
     * Handle mouse release event
     * @param e Mouse event
     */
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            endDrag();
        }
    }
    
    /**
     * Handle mouse wheel event for enhanced zooming with smooth transitions
     * @param e Mouse wheel event
     */
    public void mouseWheelMoved(MouseWheelEvent e) {
        long currentTime = System.currentTimeMillis();
        
        // Calculate zoom acceleration based on rapid scrolling
        if (currentTime - lastZoomTime < ZOOM_ACCELERATION_THRESHOLD * 1000) {
            zoomAcceleration = Math.min((int)MAX_ZOOM_ACCELERATION, zoomAcceleration + 1);
        } else {
            zoomAcceleration = 1;
        }
        lastZoomTime = currentTime;
        
        // Get mouse position for zoom center
        zoomCenterX = e.getX();
        zoomCenterY = e.getY();
        
        // Calculate zoom delta with acceleration
        int baseZoomDelta = e.getWheelRotation();
        int acceleratedDelta = baseZoomDelta * zoomAcceleration;
        
        // Calculate target zoom level
        int currentZoom = mapRenderer.getZoomLevel();
        double newTargetZoom = currentZoom + acceleratedDelta;
        
        // Clamp target zoom level
        newTargetZoom = Math.max(mapRenderer.getMinZoomLevel(), 
                                Math.min(mapRenderer.getMaxZoomLevel(), newTargetZoom));
        
        if (Math.abs(newTargetZoom - currentZoom) > 0.01) {
            // Set target and start smooth zoom animation
            targetZoomLevel = newTargetZoom;
            currentZoomLevel = currentZoom;
            
            startSmoothZoom();
        }
    }
    
    /**
     * Start dragging from the given screen position
     */
    private void startDrag(int screenX, int screenY) {
        // Stop any momentum animation
        stopMomentum();
        
        isDragging = true;
        lastMouseX = screenX;
        lastMouseY = screenY;
        lastDragTime = System.currentTimeMillis();
        
        // Reset velocity history
        for (int i = 0; i < MOMENTUM_HISTORY_SIZE; i++) {
            dragHistoryX[i] = 0.0;
            dragHistoryY[i] = 0.0;
            dragTimeHistory[i] = lastDragTime;
        }
        dragHistoryIndex = 0;
    }
    
    /**
     * Update drag to the given screen position with enhanced features
     */
    private void updateDrag(int screenX, int screenY) {
        if (!isDragging) return;
        
        long currentTime = System.currentTimeMillis();
        
        // Calculate pan delta based on mouse movement
        int deltaScreenX = screenX - lastMouseX;
        int deltaScreenY = screenY - lastMouseY;
        
        // Adaptive sensitivity based on zoom level
        double adaptiveSensitivity = calculateAdaptiveSensitivity();
        
        // Convert to world coordinates with adaptive sensitivity
        double deltaWorldX = -deltaScreenX * mapRenderer.getScale() * adaptiveSensitivity;
        double deltaWorldZ = -deltaScreenY * mapRenderer.getScale() * adaptiveSensitivity;
        
        // Update velocity history for momentum calculation
        updateVelocityHistory(deltaWorldX, deltaWorldZ, currentTime);
        
        // Pan the map
        mapRenderer.pan(deltaWorldX, deltaWorldZ);
        clampViewportToConstraints();
        
        // Update last mouse position and time
        lastMouseX = screenX;
        lastMouseY = screenY;
        lastDragTime = currentTime;
    }
    
    /**
     * End dragging and optionally start momentum panning
     */
    private void endDrag() {
        isDragging = false;
        
        // Calculate final velocity and start momentum if significant
        calculateDragVelocity();
        if (Math.abs(dragVelocityX) > MIN_MOMENTUM_THRESHOLD || Math.abs(dragVelocityY) > MIN_MOMENTUM_THRESHOLD) {
            startMomentum();
        }
    }
    
    // ===============================
    // Enhanced Drag Helper Methods
    // ===============================
    
    /**
     * Calculate adaptive sensitivity based on current zoom level
     */
    private double calculateAdaptiveSensitivity() {
        double scale = mapRenderer.getScale();
        
        // Higher sensitivity for zoomed in views (smaller scale values)
        // Lower sensitivity for zoomed out views (larger scale values)
        if (scale < 1.0) {
            return ADAPTIVE_SENSITIVITY_MAX; // Very zoomed in - high sensitivity
        } else if (scale > 32.0) {
            return ADAPTIVE_SENSITIVITY_MIN; // Very zoomed out - low sensitivity
        } else {
            // Linear interpolation between min and max
            double factor = Math.log(scale) / Math.log(32.0);
            return ADAPTIVE_SENSITIVITY_MAX - (ADAPTIVE_SENSITIVITY_MAX - ADAPTIVE_SENSITIVITY_MIN) * factor;
        }
    }
    
    /**
     * Update velocity history for momentum calculation
     */
    private void updateVelocityHistory(double deltaWorldX, double deltaWorldZ, long currentTime) {
        dragHistoryX[dragHistoryIndex] = deltaWorldX;
        dragHistoryY[dragHistoryIndex] = deltaWorldZ;
        dragTimeHistory[dragHistoryIndex] = currentTime;
        dragHistoryIndex = (dragHistoryIndex + 1) % MOMENTUM_HISTORY_SIZE;
    }
    
    /**
     * Calculate current drag velocity from history
     */
    private void calculateDragVelocity() {
        dragVelocityX = 0.0;
        dragVelocityY = 0.0;
        
        long totalTime = 0;
        for (int i = 0; i < MOMENTUM_HISTORY_SIZE - 1; i++) {
            int currentIndex = (dragHistoryIndex + i) % MOMENTUM_HISTORY_SIZE;
            int nextIndex = (dragHistoryIndex + i + 1) % MOMENTUM_HISTORY_SIZE;
            
            long timeDiff = dragTimeHistory[nextIndex] - dragTimeHistory[currentIndex];
            if (timeDiff > 0) {
                dragVelocityX += dragHistoryX[nextIndex] / (timeDiff / 1000.0); // Convert to blocks per second
                dragVelocityY += dragHistoryY[nextIndex] / (timeDiff / 1000.0);
                totalTime += timeDiff;
            }
        }
        
        if (totalTime > 0) {
            // Average the velocity over the time period
            double timeSeconds = totalTime / 1000.0;
            dragVelocityX = dragVelocityX * timeSeconds / (MOMENTUM_HISTORY_SIZE - 1);
            dragVelocityY = dragVelocityY * timeSeconds / (MOMENTUM_HISTORY_SIZE - 1);
        }
    }
    
    /**
     * Start momentum panning animation
     */
    private void startMomentum() {
        if (momentumActive) return;
        
        momentumActive = true;
        momentumThread = new Thread(() -> {
            try {
                while (momentumActive && !Thread.currentThread().isInterrupted()) {
                    // Apply momentum movement
                    double deltaX = dragVelocityX * (ANIMATION_DELAY_MS / 1000.0);
                    double deltaZ = dragVelocityY * (ANIMATION_DELAY_MS / 1000.0);
                    
                    mapRenderer.pan(deltaX, deltaZ);
                    clampViewportToConstraints();
                    
                    // Decay velocity
                    dragVelocityX *= MOMENTUM_DECAY;
                    dragVelocityY *= MOMENTUM_DECAY;
                    
                    // Stop if velocity is too small
                    if (Math.abs(dragVelocityX) < MIN_MOMENTUM_THRESHOLD && 
                        Math.abs(dragVelocityY) < MIN_MOMENTUM_THRESHOLD) {
                        momentumActive = false;
                        break;
                    }
                    
                    Thread.sleep(ANIMATION_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                momentumActive = false;
            }
        });
        momentumThread.setDaemon(true);
        momentumThread.setName("MapViewport-Momentum");
        momentumThread.start();
    }
    
    /**
     * Stop momentum panning animation
     */
    private void stopMomentum() {
        momentumActive = false;
        if (momentumThread != null && momentumThread.isAlive()) {
            momentumThread.interrupt();
        }
    }
    
    // ===============================
    // Enhanced Smooth Zoom Methods
    // ===============================
    
    /**
     * Start smooth zoom animation to target zoom level
     */
    private void startSmoothZoom() {
        // Stop any existing smooth zoom
        stopSmoothZoom();
        
        if (Math.abs(targetZoomLevel - currentZoomLevel) < 0.01) {
            return; // No significant zoom change needed
        }
        
        smoothZoomActive = true;
        smoothZoomThread = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                double startZoomLevel = currentZoomLevel;
                double zoomDifference = targetZoomLevel - startZoomLevel;
                
                // Get initial mouse world position for zoom center
                double[] initialMouseWorldPos = mapRenderer.screenToWorld((int)zoomCenterX, (int)zoomCenterY);
                
                while (smoothZoomActive && !Thread.currentThread().isInterrupted()) {
                    long currentTime = System.currentTimeMillis();
                    double elapsed = currentTime - startTime;
                    double progress = Math.min(1.0, elapsed / SMOOTH_ZOOM_DURATION_MS);
                    
                    // Apply smooth easing function
                    double easedProgress = easeInOutCubic(progress);
                    
                    // Calculate current zoom level with easing
                    double newZoomLevel = startZoomLevel + (zoomDifference * easedProgress);
                    
                    // Apply the zoom level
                    int discreteZoomLevel = (int) Math.round(newZoomLevel);
                    if (discreteZoomLevel != mapRenderer.getZoomLevel()) {
                        mapRenderer.setZoomLevel(discreteZoomLevel);
                        
                        // Keep zoom center position stable
                        double[] newMouseWorldPos = mapRenderer.screenToWorld((int)zoomCenterX, (int)zoomCenterY);
                        double deltaX = initialMouseWorldPos[0] - newMouseWorldPos[0];
                        double deltaZ = initialMouseWorldPos[1] - newMouseWorldPos[1];
                        
                        mapRenderer.pan(deltaX, deltaZ);
                        clampViewportToConstraints();
                    }
                    
                    // Update current zoom level for interpolation
                    currentZoomLevel = newZoomLevel;
                    
                    // Check if animation is complete
                    if (progress >= 1.0) {
                        smoothZoomActive = false;
                        break;
                    }
                    
                    Thread.sleep(ANIMATION_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                smoothZoomActive = false;
                currentZoomLevel = mapRenderer.getZoomLevel();
            }
        });
        
        smoothZoomThread.setDaemon(true);
        smoothZoomThread.setName("MapViewport-SmoothZoom");
        smoothZoomThread.start();
    }
    
    /**
     * Stop smooth zoom animation
     */
    private void stopSmoothZoom() {
        smoothZoomActive = false;
        if (smoothZoomThread != null && smoothZoomThread.isAlive()) {
            smoothZoomThread.interrupt();
        }
    }
    
    /**
     * Cubic easing function for smooth zoom animation
     * @param t Progress value (0.0 to 1.0)
     * @return Eased progress value
     */
    private double easeInOutCubic(double t) {
        if (t < 0.5) {
            return 4.0 * t * t * t;
        } else {
            double p = 2.0 * t - 2.0;
            return 1.0 + p * p * p / 2.0;
        }
    }
    
    // ===============================
    // Keyboard Event Handlers
    // ===============================
    
    /**
     * Handle key press event
     * @param e Key event
     */
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                keyUp = true;
                startKeyboardMovement();
                break;
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                keyDown = true;
                startKeyboardMovement();
                break;
            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                keyLeft = true;
                startKeyboardMovement();
                break;
            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                keyRight = true;
                startKeyboardMovement();
                break;
            case KeyEvent.VK_SHIFT:
                fastPanMode = true;
                break;
            case KeyEvent.VK_CONTROL:
                finePanMode = true;
                break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS:
                if (e.isShiftDown()) {
                    keyZoomIn = true; // Continuous zoom
                } else {
                    zoomIn(); // Single zoom step
                }
                break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_UNDERSCORE:
                if (e.isShiftDown()) {
                    keyZoomOut = true; // Continuous zoom
                } else {
                    zoomOut(); // Single zoom step
                }
                break;
            case KeyEvent.VK_PAGE_UP:
                keyZoomIn = true; // Page Up for continuous zoom in
                break;
            case KeyEvent.VK_PAGE_DOWN:
                keyZoomOut = true; // Page Down for continuous zoom out
                break;
            case KeyEvent.VK_HOME:
                goToSpawn();
                break;
            case KeyEvent.VK_SPACE:
                centerOnCurrentPosition();
                break;
            case KeyEvent.VK_END:
                resetZoomToDefault();
                break;
            case KeyEvent.VK_INSERT:
                toggleFollowMode();
                break;
        }
    }
    
    /**
     * Handle key release event
     * @param e Key event
     */
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                keyUp = false;
                stopKeyboardMovementIfNeeded();
                break;
            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                keyDown = false;
                stopKeyboardMovementIfNeeded();
                break;
            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                keyLeft = false;
                stopKeyboardMovementIfNeeded();
                break;
            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                keyRight = false;
                stopKeyboardMovementIfNeeded();
                break;
            case KeyEvent.VK_SHIFT:
                fastPanMode = false;
                break;
            case KeyEvent.VK_CONTROL:
                finePanMode = false;
                break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS:
                keyZoomIn = false;
                break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_UNDERSCORE:
                keyZoomOut = false;
                break;
            case KeyEvent.VK_PAGE_UP:
                keyZoomIn = false;
                break;
            case KeyEvent.VK_PAGE_DOWN:
                keyZoomOut = false;
                break;
        }
    }
    
    /**
     * Start keyboard pan thread
     */
    private void startKeyboardPanThread() {
        keyboardPanThread = new Thread(() -> {
            long lastTime = System.nanoTime();
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(ANIMATION_DELAY_MS);
                    
                    long currentTime = System.nanoTime();
                    double deltaTime = (currentTime - lastTime) / 1_000_000_000.0; // Convert to seconds
                    lastTime = currentTime;
                    
                    // Handle movement keys
                    if (keyUp || keyDown || keyLeft || keyRight) {
                        // Calculate speed with modifiers and acceleration
                        double speed = KEYBOARD_PAN_SPEED * mapRenderer.getScale();
                        
                        if (fastPanMode) {
                            speed *= FAST_PAN_MULTIPLIER;
                        }
                        if (finePanMode) {
                            speed *= FINE_PAN_MULTIPLIER;
                        }
                        
                        // Apply acceleration over time
                        speed *= currentKeyboardSpeedMultiplier;
                        
                        double deltaX = 0, deltaZ = 0;
                        
                        if (keyLeft) deltaX -= speed * deltaTime;
                        if (keyRight) deltaX += speed * deltaTime;
                        if (keyUp) deltaZ -= speed * deltaTime;
                        if (keyDown) deltaZ += speed * deltaTime;
                        
                        if (deltaX != 0 || deltaZ != 0) {
                            mapRenderer.pan(deltaX, deltaZ);
                            clampViewportToConstraints();
                        }
                        
                        // Update acceleration
                        updateKeyboardAcceleration(deltaTime);
                    }
                    
                    // Handle continuous zoom keys
                    if (keyZoomIn || keyZoomOut) {
                        double zoomDelta = KEYBOARD_ZOOM_SPEED * deltaTime;
                        if (keyZoomIn) {
                            smoothZoomAtCenter(Math.pow(2.0, zoomDelta));
                        }
                        if (keyZoomOut) {
                            smoothZoomAtCenter(Math.pow(2.0, -zoomDelta));
                        }
                    }
                    
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "KeyboardPan");
        
        keyboardPanThread.setDaemon(true);
        keyboardPanThread.start();
    }
    
    // ===============================
    // Navigation Methods
    // ===============================
    
    /**
     * Zoom in one level
     */
    public void zoomIn() {
        int currentZoom = mapRenderer.getZoomLevel();
        int proposedZoom = currentZoom - 1;
        
        // Check constraints
        ZoomConstraintsManager.ZoomViolationInfo violation = 
            zoomConstraintsManager.checkZoomViolation(proposedZoom);
        
        if (!violation.isViolation()) {
            mapRenderer.zoomIn();
        } else {
            // Could show notification about constraint violation
            handleZoomConstraintViolation(violation);
        }
    }
    
    /**
     * Zoom out one level
     */
    public void zoomOut() {
        int currentZoom = mapRenderer.getZoomLevel();
        int proposedZoom = currentZoom + 1;
        
        // Check constraints
        ZoomConstraintsManager.ZoomViolationInfo violation = 
            zoomConstraintsManager.checkZoomViolation(proposedZoom);
        
        if (!violation.isViolation()) {
            mapRenderer.zoomOut();
        } else {
            // Could show notification about constraint violation
            handleZoomConstraintViolation(violation);
        }
    }
    
    /**
     * Animate to a specific world position
     * @param targetX Target world X coordinate
     * @param targetZ Target world Z coordinate
     * @param targetZoom Target zoom level
     */
    public void animateToPosition(double targetX, double targetZ, int targetZoom) {
        if (isAnimating) {
            animationThread.interrupt();
        }
        
        // Apply zoom constraints
        int constrainedZoom = zoomConstraintsManager.clampZoomLevel(targetZoom);
        
        isAnimating = true;
        
        animationThread = new Thread(() -> {
            double startX = mapRenderer.getViewportCenterX();
            double startZ = mapRenderer.getViewportCenterZ();
            int startZoom = mapRenderer.getZoomLevel();
            
            // Clamp target position (create final variables for lambda)
            final double finalTargetX = Math.max(minWorldX, Math.min(maxWorldX, targetX));
            final double finalTargetZ = Math.max(minWorldZ, Math.min(maxWorldZ, targetZ));
            final int finalTargetZoom = constrainedZoom;
            
            for (int step = 0; step <= SMOOTH_PAN_STEPS && !Thread.currentThread().isInterrupted(); step++) {
                double progress = (double) step / SMOOTH_PAN_STEPS;
                progress = smoothStep(progress); // Apply easing
                
                double currentX = lerp(startX, finalTargetX, progress);
                double currentZ = lerp(startZ, finalTargetZ, progress);
                int currentZoom = (int) lerp(startZoom, finalTargetZoom, progress);
                
                mapRenderer.setViewportCenter(currentX, currentZ);
                if (currentZoom != mapRenderer.getZoomLevel()) {
                    mapRenderer.setZoomLevel(currentZoom);
                }
                
                try {
                    Thread.sleep(ANIMATION_DELAY_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            // Ensure final position is exact
            if (!Thread.currentThread().isInterrupted()) {
                mapRenderer.setViewportCenter(finalTargetX, finalTargetZ);
                mapRenderer.setZoomLevel(finalTargetZoom);
            }
            
            isAnimating = false;
        }, "ViewportAnimation");
        
        animationThread.setDaemon(true);
        animationThread.start();
    }
    
    /**
     * Go to spawn point (0, 0)
     */
    public void goToSpawn() {
        animateToPosition(0, 0, Math.max(0, mapRenderer.getZoomLevel()));
    }
    
    /**
     * Center on current position (placeholder - would need player position in real implementation)
     */
    public void centerOnCurrentPosition() {
        // For now, just go to spawn
        goToSpawn();
    }
    
    /**
     * Reset zoom to default level
     */
    public void resetZoomToDefault() {
        int defaultZoom = (mapRenderer.getMinZoomLevel() + mapRenderer.getMaxZoomLevel()) / 2;
        animateToPosition(mapRenderer.getViewportCenterX(), mapRenderer.getViewportCenterZ(), defaultZoom);
    }
    
    /**
     * Toggle follow mode (placeholder for future player tracking)
     */
    public void toggleFollowMode() {
        // Placeholder - would implement player following in real game
        centerOnCurrentPosition();
    }
    
    /**
     * Start keyboard movement acceleration tracking
     */
    private void startKeyboardMovement() {
        if (keyboardStartTime == 0) {
            keyboardStartTime = System.currentTimeMillis();
            currentKeyboardSpeedMultiplier = 1.0;
        }
    }
    
    /**
     * Stop keyboard movement if no keys are pressed
     */
    private void stopKeyboardMovementIfNeeded() {
        if (!keyUp && !keyDown && !keyLeft && !keyRight) {
            keyboardStartTime = 0;
            currentKeyboardSpeedMultiplier = 1.0;
        }
    }
    
    /**
     * Update keyboard acceleration based on time held
     */
    private void updateKeyboardAcceleration(double deltaTime) {
        if (keyboardStartTime > 0) {
            double timeHeld = (System.currentTimeMillis() - keyboardStartTime) / 1000.0;
            double accelerationProgress = Math.min(1.0, timeHeld / KEYBOARD_ACCELERATION_TIME);
            
            // Smooth acceleration curve
            double easedProgress = smoothStep(accelerationProgress);
            currentKeyboardSpeedMultiplier = 1.0 + (MAX_KEYBOARD_SPEED_MULTIPLIER - 1.0) * easedProgress;
        }
    }
    
    /**
     * Smooth zoom at screen center
     */
    private void smoothZoomAtCenter(double zoomFactor) {
        int screenWidth = 800; // Would get from actual screen size
        int screenHeight = 600;
        
        // Set zoom center to screen center
        zoomCenterX = screenWidth / 2;
        zoomCenterY = screenHeight / 2;
        
        // Calculate new target zoom level
        double currentZoom = mapRenderer.getZoomLevel();
        double newTargetZoom = currentZoom + Math.log(zoomFactor) / Math.log(2.0); // Convert factor to zoom levels
        
        // Apply constraints
        int constrainedZoom = zoomConstraintsManager.clampZoomLevel((int)Math.round(newTargetZoom));
        
        if (Math.abs(constrainedZoom - currentZoom) > 0.01) {
            targetZoomLevel = constrainedZoom;
            currentZoomLevel = currentZoom;
            startSmoothZoom();
        }
    }
    
    /**
     * Stop any current animation
     */
    public void stopAnimation() {
        if (isAnimating && animationThread != null) {
            animationThread.interrupt();
            isAnimating = false;
        }
    }
    
    /**
     * Update viewport manager state (should be called regularly)
     */
    public void tick() {
        zoomConstraintsManager.tick();
    }
    
    /**
     * Handle zoom constraint violation
     */
    private void handleZoomConstraintViolation(ZoomConstraintsManager.ZoomViolationInfo violation) {
        // Could play a sound, show a visual indicator, or send a message
        // For now, just clamp to the allowed level
        mapRenderer.setZoomLevel(violation.constrainedZoom);
    }
    
    // ===============================
    // Viewport Constraints
    // ===============================
    
    /**
     * Set world coordinate constraints
     * @param minX Minimum world X coordinate
     * @param maxX Maximum world X coordinate
     * @param minZ Minimum world Z coordinate
     * @param maxZ Maximum world Z coordinate
     */
    public void setWorldConstraints(double minX, double maxX, double minZ, double maxZ) {
        this.minWorldX = minX;
        this.maxWorldX = maxX;
        this.minWorldZ = minZ;
        this.maxWorldZ = maxZ;
        clampViewportToConstraints();
    }
    
    /**
     * Clear world coordinate constraints
     */
    public void clearWorldConstraints() {
        this.minWorldX = Double.NEGATIVE_INFINITY;
        this.maxWorldX = Double.POSITIVE_INFINITY;
        this.minWorldZ = Double.NEGATIVE_INFINITY;
        this.maxWorldZ = Double.POSITIVE_INFINITY;
    }
    
    /**
     * Clamp viewport to constraints
     */
    private void clampViewportToConstraints() {
        double currentX = mapRenderer.getViewportCenterX();
        double currentZ = mapRenderer.getViewportCenterZ();
        
        double clampedX = Math.max(minWorldX, Math.min(maxWorldX, currentX));
        double clampedZ = Math.max(minWorldZ, Math.min(maxWorldZ, currentZ));
        
        if (clampedX != currentX || clampedZ != currentZ) {
            mapRenderer.setViewportCenter(clampedX, clampedZ);
        }
    }
    
    // ===============================
    // Utility Methods
    // ===============================
    
    /**
     * Get zoom constraints manager
     */
    public ZoomConstraintsManager getZoomConstraintsManager() {
        return zoomConstraintsManager;
    }
    
    /**
     * Check if zoom level is currently allowed
     */
    public boolean isZoomLevelAllowed(int zoomLevel) {
        return zoomConstraintsManager.isZoomLevelAllowed(zoomLevel);
    }
    
    /**
     * Get current effective zoom constraints
     */
    public ZoomConstraintsManager.ZoomConstraintsStatus getZoomConstraintsStatus() {
        return zoomConstraintsManager.getStatus();
    }
    
    /**
     * Linear interpolation
     */
    private double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
    
    /**
     * Smooth step easing function
     */
    private double smoothStep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }
    
    /**
     * Get current interaction state
     * @return ViewportState with current state
     */
    public ViewportState getState() {
        return new ViewportState(
            isDragging, isAnimating, smoothZoomActive,
            keyUp, keyDown, keyLeft, keyRight, fastPanMode, finePanMode,
            keyZoomIn, keyZoomOut, currentKeyboardSpeedMultiplier,
            minWorldX, maxWorldX, minWorldZ, maxWorldZ,
            zoomAcceleration, targetZoomLevel
        );
    }
    
    /**
     * Cleanup resources and stop all threads
     */
    public void shutdown() {
        // Stop all animations and threads
        stopAnimation();
        stopMomentum();
        stopSmoothZoom();
        
        if (keyboardPanThread != null) {
            keyboardPanThread.interrupt();
        }
        
        // Reset interaction state
        isDragging = false;
        momentumActive = false;
        smoothZoomActive = false;
        isAnimating = false;
    }
    
    // ===============================
    // State Class
    // ===============================
    
    /**
     * Current viewport interaction state
     */
    public static class ViewportState {
        public final boolean isDragging;
        public final boolean isAnimating;
        public final boolean smoothZoomActive;
        public final boolean keyUp, keyDown, keyLeft, keyRight, fastPanMode, finePanMode;
        public final boolean keyZoomIn, keyZoomOut;
        public final double currentKeyboardSpeedMultiplier;
        public final double minWorldX, maxWorldX, minWorldZ, maxWorldZ;
        public final int zoomAcceleration;
        public final double targetZoomLevel;
        
        ViewportState(boolean isDragging, boolean isAnimating, boolean smoothZoomActive,
                     boolean keyUp, boolean keyDown, boolean keyLeft, boolean keyRight, 
                     boolean fastPanMode, boolean finePanMode, boolean keyZoomIn, boolean keyZoomOut,
                     double currentKeyboardSpeedMultiplier,
                     double minWorldX, double maxWorldX, double minWorldZ, double maxWorldZ,
                     int zoomAcceleration, double targetZoomLevel) {
            this.isDragging = isDragging;
            this.isAnimating = isAnimating;
            this.smoothZoomActive = smoothZoomActive;
            this.keyUp = keyUp;
            this.keyDown = keyDown;
            this.keyLeft = keyLeft;
            this.keyRight = keyRight;
            this.fastPanMode = fastPanMode;
            this.finePanMode = finePanMode;
            this.keyZoomIn = keyZoomIn;
            this.keyZoomOut = keyZoomOut;
            this.currentKeyboardSpeedMultiplier = currentKeyboardSpeedMultiplier;
            this.minWorldX = minWorldX;
            this.maxWorldX = maxWorldX;
            this.minWorldZ = minWorldZ;
            this.maxWorldZ = maxWorldZ;
            this.zoomAcceleration = zoomAcceleration;
            this.targetZoomLevel = targetZoomLevel;
        }
        
        @Override
        public String toString() {
            return String.format("Viewport: drag=%s, anim=%s, zoom=%s(x%d→%.1f), keys=%s%s%s%s%s%s, zoom=%s%s, accel=%.1fx", 
                isDragging, isAnimating, smoothZoomActive, zoomAcceleration, targetZoomLevel,
                keyUp ? "↑" : "", keyDown ? "↓" : "", keyLeft ? "←" : "", keyRight ? "→" : "",
                fastPanMode ? " (fast)" : "", finePanMode ? " (fine)" : "",
                keyZoomIn ? "+" : "", keyZoomOut ? "-" : "",
                currentKeyboardSpeedMultiplier);
        }
    }
}
