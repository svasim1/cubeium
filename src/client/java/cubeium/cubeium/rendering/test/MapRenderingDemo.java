package cubeium.cubeium.rendering.test;

import cubeium.cubeium.rendering.MapRenderer;
import cubeium.cubeium.rendering.viewport.MapViewportManager;
import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;
import cubeium.cubeium.util.ClientColorPalette;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * Simple demonstration and validation of the map rendering system.
 * This class provides a basic GUI to test the complete rendering pipeline.
 */
public class MapRenderingDemo {
    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 700;
    private static final long DEFAULT_SEED = 12345L;
    
    private JFrame frame;
    private MapPanel mapPanel;
    private MapRenderer mapRenderer;
    private MapViewportManager viewportManager;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new MapRenderingDemo().createAndShow();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Error starting map demo: " + e.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    private void createAndShow() {
        System.out.println("Initializing Map Rendering Demo...");
        
        try {
            // Initialize world generation components
            System.out.println("Creating world generators...");
            BiomeGenerator biomeGenerator = new BiomeGenerator();
            StructureGenerator structureGenerator = new StructureGenerator();
            ClientColorPalette colorPalette = new ClientColorPalette();
            
            // Create map renderer
            System.out.println("Creating map renderer...");
            mapRenderer = new MapRenderer(biomeGenerator, structureGenerator, colorPalette, 4);
            
            // Set up world state
            System.out.println("Setting world state (seed: " + DEFAULT_SEED + ")...");
            mapRenderer.setWorldState(DEFAULT_SEED, 0); // Overworld
            
            // Create GUI
            System.out.println("Creating GUI...");
            createGUI();
            
            System.out.println("Map Rendering Demo initialized successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize map rendering demo", e);
        }
    }
    
    private void createGUI() {
        frame = new JFrame("Cubeium Map Rendering Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        // Create map panel
        mapPanel = new MapPanel();
        mapPanel.setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT - 100));
        
        // Create viewport manager
        viewportManager = new MapViewportManager(mapRenderer);
        
        // Add mouse and keyboard listeners through adapter methods
        mapPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { viewportManager.mousePressed(e); }
            @Override
            public void mouseReleased(MouseEvent e) { viewportManager.mouseReleased(e); }
        });
        mapPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) { viewportManager.mouseDragged(e); }
        });
        mapPanel.addMouseWheelListener(e -> viewportManager.mouseWheelMoved(e));
        mapPanel.setFocusable(true);
        mapPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) { viewportManager.keyPressed(e); }
            @Override
            public void keyReleased(KeyEvent e) { viewportManager.keyReleased(e); }
        });
        
        // Create control panel
        JPanel controlPanel = createControlPanel();
        
        // Add components to frame
        frame.add(mapPanel, BorderLayout.CENTER);
        frame.add(controlPanel, BorderLayout.SOUTH);
        
        // Set initial viewport
        mapRenderer.setViewportSize(WINDOW_WIDTH, WINDOW_HEIGHT - 100);
        mapRenderer.setViewportCenter(0, 0);
        mapRenderer.setZoomLevel(0);
        
        // Show frame
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        // Request focus for keyboard input
        mapPanel.requestFocusInWindow();
    }
    
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        
        // Seed input
        JTextField seedField = new JTextField(String.valueOf(DEFAULT_SEED), 10);
        JButton loadSeedButton = new JButton("Load Seed");
        loadSeedButton.addActionListener(e -> {
            try {
                long seed = Long.parseLong(seedField.getText());
                mapRenderer.setWorldState(seed, 0);
                mapPanel.repaint();
                System.out.println("Loaded seed: " + seed);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid seed format", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        // Navigation buttons
        JButton goToSpawnButton = new JButton("Go to Spawn");
        goToSpawnButton.addActionListener(e -> {
            viewportManager.goToSpawn();
            mapPanel.repaint();
        });
        
        JButton zoomInButton = new JButton("Zoom In");
        zoomInButton.addActionListener(e -> {
            mapRenderer.setZoomLevel(mapRenderer.getZoomLevel() + 1);
            mapPanel.repaint();
        });
        
        JButton zoomOutButton = new JButton("Zoom Out");
        zoomOutButton.addActionListener(e -> {
            mapRenderer.setZoomLevel(mapRenderer.getZoomLevel() - 1);
            mapPanel.repaint();
        });
        
        // Info label
        JLabel infoLabel = new JLabel("Use WASD/arrows to pan, mouse wheel to zoom, drag to pan");
        
        // Add components
        panel.add(new JLabel("Seed:"));
        panel.add(seedField);
        panel.add(loadSeedButton);
        panel.add(new JLabel(" | "));
        panel.add(goToSpawnButton);
        panel.add(zoomInButton);
        panel.add(zoomOutButton);
        panel.add(new JLabel(" | "));
        panel.add(infoLabel);
        
        return panel;
    }
    
    /**
     * Custom JPanel for rendering the map
     */
    private class MapPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            try {
                // Update viewport size if panel was resized
                Dimension size = getSize();
                mapRenderer.setViewportSize(size.width, size.height);
                
                // Render the viewport
                BufferedImage mapImage = mapRenderer.renderViewport();
                
                if (mapImage != null) {
                    g.drawImage(mapImage, 0, 0, this);
                } else {
                    // Draw loading message
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(0, 0, size.width, size.height);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font("Arial", Font.BOLD, 24));
                    String message = "Loading map...";
                    FontMetrics fm = g.getFontMetrics();
                    int x = (size.width - fm.stringWidth(message)) / 2;
                    int y = size.height / 2;
                    g.drawString(message, x, y);
                }
                
                // Draw stats
                drawStats(g);
                
            } catch (Exception e) {
                // Draw error message
                g.setColor(Color.RED);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.BOLD, 16));
                String error = "Error: " + e.getMessage();
                g.drawString(error, 10, 30);
                e.printStackTrace();
            }
        }
        
        private void drawStats(Graphics g) {
            try {
                MapRenderer.RenderingStats stats = mapRenderer.getStats();
                
                g.setColor(new Color(0, 0, 0, 180));
                g.fillRect(10, 10, 300, 140);
                
                g.setColor(Color.WHITE);
                g.setFont(new Font("Arial", Font.PLAIN, 12));
                
                int y = 25;
                int lineHeight = 15;
                
                g.drawString("Center: (" + (int)mapRenderer.getViewportCenterX() + ", " + (int)mapRenderer.getViewportCenterZ() + ")", 15, y);
                y += lineHeight;
                
                g.drawString("Zoom: " + mapRenderer.getZoomLevel(), 15, y);
                y += lineHeight;
                
                g.drawString("Total Renders: " + stats.totalRenderRequests, 15, y);
                y += lineHeight;
                
                g.drawString("Tile Cache Hits: " + (int)(stats.cacheStats.tileHitRate * 100) + "%", 15, y);
                y += lineHeight;
                
                g.drawString("Avg Render Time: " + String.format("%.1f", stats.avgRenderTimeMs) + "ms", 15, y);
                y += lineHeight;
                
                g.drawString("Pending Tiles: " + stats.pendingTiles, 15, y);
                y += lineHeight;
                
                g.drawString("Memory: " + String.format("%.1f", stats.cacheStats.estimatedMemoryUsage / (1024.0 * 1024.0)) + " MB", 15, y);
                y += lineHeight;
                
                g.drawString("FPS: " + getCurrentFPS(), 15, y);
                
            } catch (Exception e) {
                // Ignore stats drawing errors
            }
        }
        
        private long lastFrameTime = System.currentTimeMillis();
        private int frameCount = 0;
        private int currentFPS = 0;
        
        private int getCurrentFPS() {
            frameCount++;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameTime >= 1000) {
                currentFPS = frameCount;
                frameCount = 0;
                lastFrameTime = currentTime;
            }
            return currentFPS;
        }
    }
}
