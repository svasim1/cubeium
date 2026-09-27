package cubeium.cubeium.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import cubeium.cubeium.Cubeium;
import dev.xpple.cubiomes.CubiomesInit;

/**
 * Loads the cubiomes native library bundled in the dev.xpple:cubiomes bindings jar.
 *
 * <p>The bindings resolve symbols through {@code SymbolLookup.loaderLookup()}, i.e. libraries
 * loaded by their class loader, which is also ours (Fabric's Knot, or the app class loader in
 * tests). Instead of {@link CubiomesInit#load()}, which writes a new temp file on every launch,
 * the library is extracted to {@code <tmpdir>/cubeium-natives/<sha256>/} and reused.
 */
public final class NativeLibrary {
    private static boolean loaded;

    private NativeLibrary() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        String fileName = System.mapLibraryName("cubiomes");
        byte[] library;
        try (InputStream in = CubiomesInit.class.getResourceAsStream("/" + fileName)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Cubeium has no cubiomes library for this platform ("
                        + System.getProperty("os.name") + ", " + System.getProperty("os.arch") + ")");
            }
            library = in.readAllBytes();
        } catch (IOException e) {
            throw linkError("Failed to read " + fileName, e);
        }

        Path target = Path.of(System.getProperty("java.io.tmpdir"), "cubeium-natives", sha256(library), fileName);
        try {
            if (!Files.exists(target) || Files.size(target) != library.length) {
                Files.createDirectories(target.getParent());
                Path tmp = Files.createTempFile(target.getParent(), "cubiomes", ".tmp");
                Files.write(tmp, library);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    // Another game instance may have extracted (and loaded) the same file concurrently.
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
        Cubeium.LOGGER.info("Loaded cubiomes from {}", target);
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
