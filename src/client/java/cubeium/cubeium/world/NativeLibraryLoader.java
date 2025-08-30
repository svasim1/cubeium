package cubeium.cubeium.world;

import cubeium.cubeium.Cubeium;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Utility class for loading native libraries from JAR resources
 */
public class NativeLibraryLoader {
    
    private static final String TEMP_DIR_PREFIX = "cubeium_natives_";
    private static Path tempDir = null;
    
    /**
     * Load a native library from JAR resources
     * @param libraryName Base name of the library (without extension)
     * @param resourcePath Path to the library resource within the JAR
     * @throws IOException If library cannot be loaded
     */
    public static void loadLibraryFromResources(String libraryName, String resourcePath) throws IOException {
        // Get the resource as a stream
        InputStream libraryStream = NativeLibraryLoader.class.getResourceAsStream(resourcePath);
        
        if (libraryStream == null) {
            throw new IOException("Native library not found in resources: " + resourcePath);
        }
        
        try {
            // Create temporary directory if it doesn't exist
            if (tempDir == null || !Files.exists(tempDir)) {
                tempDir = Files.createTempDirectory(TEMP_DIR_PREFIX);
                tempDir.toFile().deleteOnExit();
                
                // Add shutdown hook to clean up temp directory
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        deleteDirectory(tempDir);
                    } catch (Exception e) {
                        Cubeium.LOGGER.warn("Failed to clean up temporary native library directory: " + tempDir, e);
                    }
                }));
            }
            
            // Extract library name and extension from resource path
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path tempLibraryPath = tempDir.resolve(fileName);
            
            // Copy library from JAR to temporary file
            Files.copy(libraryStream, tempLibraryPath, StandardCopyOption.REPLACE_EXISTING);
            
            // Make the file executable (important for Unix systems)
            tempLibraryPath.toFile().setExecutable(true);
            tempLibraryPath.toFile().deleteOnExit();
            
            // Load the library
            System.load(tempLibraryPath.toAbsolutePath().toString());
            
            Cubeium.LOGGER.info("Successfully loaded native library: " + fileName + " from " + tempLibraryPath);
            
        } finally {
            // Close the stream
            try {
                libraryStream.close();
            } catch (IOException e) {
                Cubeium.LOGGER.warn("Failed to close library stream", e);
            }
        }
    }
    
    /**
     * Recursively delete a directory and its contents
     * @param dir Directory to delete
     * @throws IOException If deletion fails
     */
    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        
        Files.walk(dir)
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2)) // Delete files before directories
            .forEach(File::delete);
    }
    
    /**
     * Check if a native library is available in resources
     * @param resourcePath Path to the library resource
     * @return True if the library exists in resources
     */
    public static boolean isLibraryAvailable(String resourcePath) {
        return NativeLibraryLoader.class.getResource(resourcePath) != null;
    }
    
    /**
     * Get the temporary directory used for native libraries
     * @return Path to temp directory, or null if not created yet
     */
    public static Path getTempDirectory() {
        return tempDir;
    }
}
