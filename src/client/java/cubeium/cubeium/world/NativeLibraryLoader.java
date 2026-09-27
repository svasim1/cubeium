package cubeium.cubeium.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import cubeium.cubeium.Cubeium;

/**
 * Loads the bundled native library (natives/&lt;os&gt;/&lt;arch&gt;/libcubeium.&lt;ext&gt; in the jar).
 *
 * <p>The library is extracted to {@code <tmpdir>/cubeium-natives/<sha256>/} and reused on later
 * launches. Keying the directory by content hash means nothing has to be deleted on exit, which
 * Windows does not allow for a loaded DLL anyway.
 */
final class NativeLibraryLoader {
    private static boolean loaded;

    private NativeLibraryLoader() {
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }

        String resourcePath = "/natives/" + osName() + "/" + archName() + "/" + libraryFileName();
        byte[] library;
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Cubeium has no native library for this platform ("
                        + System.getProperty("os.name") + ", " + System.getProperty("os.arch")
                        + "): missing " + resourcePath);
            }
            library = in.readAllBytes();
        } catch (IOException e) {
            throw linkError("Failed to read " + resourcePath, e);
        }

        Path target = Path.of(System.getProperty("java.io.tmpdir"), "cubeium-natives", sha256(library), libraryFileName());
        try {
            if (!Files.exists(target) || Files.size(target) != library.length) {
                Files.createDirectories(target.getParent());
                Path tmp = Files.createTempFile(target.getParent(), "libcubeium", ".tmp");
                Files.write(tmp, library);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    // Another instance may have extracted (and loaded) the same file concurrently.
                    Files.deleteIfExists(tmp);
                    if (!Files.exists(target)) {
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            throw linkError("Failed to extract native library to " + target, e);
        }

        System.load(target.toAbsolutePath().toString());
        loaded = true;
        Cubeium.LOGGER.info("Loaded native library {}", target);
    }

    static String osName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "macos";
        if (os.contains("linux")) return "linux";
        return os.replace(' ', '_');
    }

    static String archName() {
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> arch;
        };
    }

    private static String libraryFileName() {
        return switch (osName()) {
            case "windows" -> "libcubeium.dll";
            case "macos" -> "libcubeium.dylib";
            default -> "libcubeium.so";
        };
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UnsatisfiedLinkError linkError(String message, Throwable cause) {
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(message + ": " + cause.getMessage());
        error.initCause(cause);
        return error;
    }
}
