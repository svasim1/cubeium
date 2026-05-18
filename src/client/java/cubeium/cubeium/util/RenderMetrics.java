package cubeium.cubeium.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight runtime metrics for map rendering and generation.
 * This is used for Phase 0 instrumentation baselines.
 */
public final class RenderMetrics {
    private static final RenderMetrics INSTANCE = new RenderMetrics();

    private final ThreadLocal<Long> frameStartNanos = ThreadLocal.withInitial(() -> 0L);
    private final AtomicLong frameCount = new AtomicLong();
    private final AtomicLong totalFrameNanos = new AtomicLong();
    private final AtomicLong totalRenderNanos = new AtomicLong();

    private final AtomicLong tileDrawCount = new AtomicLong();
    private final AtomicLong tileCacheHits = new AtomicLong();
    private final AtomicLong tileCacheMisses = new AtomicLong();
    private final AtomicInteger tilesDrawnLastFrame = new AtomicInteger();

    private final AtomicLong tileGenerationCount = new AtomicLong();
    private final AtomicLong totalTileGenerationNanos = new AtomicLong();

    private final AtomicLong jniCallCount = new AtomicLong();
    private final AtomicLong totalJniNanos = new AtomicLong();

    private final AtomicInteger queueDepth = new AtomicInteger();
    private final AtomicInteger tileCacheSize = new AtomicInteger();
    private final AtomicInteger chunkCacheSize = new AtomicInteger();

    private final AtomicLong lastLogMillis = new AtomicLong(0L);

    private RenderMetrics() {
    }

    public static RenderMetrics get() {
        return INSTANCE;
    }

    public void beginFrame() {
        tilesDrawnLastFrame.set(0);
        frameStartNanos.set(System.nanoTime());
    }

    public void endFrame() {
        long start = frameStartNanos.get();
        if (start <= 0) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        frameCount.incrementAndGet();
        totalFrameNanos.addAndGet(elapsed);
    }

    public void addRenderNanos(long nanos) {
        if (nanos > 0) {
            totalRenderNanos.addAndGet(nanos);
        }
    }

    public void recordTileDraw() {
        tileDrawCount.incrementAndGet();
        tilesDrawnLastFrame.incrementAndGet();
    }

    public void recordTileCacheHit() {
        tileCacheHits.incrementAndGet();
    }

    public void recordTileCacheMiss() {
        tileCacheMisses.incrementAndGet();
    }

    public void recordTileGenerationNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        tileGenerationCount.incrementAndGet();
        totalTileGenerationNanos.addAndGet(nanos);
    }

    public void recordJniCallNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        jniCallCount.incrementAndGet();
        totalJniNanos.addAndGet(nanos);
    }

    public void setQueueDepth(int depth) {
        queueDepth.set(Math.max(0, depth));
    }

    public void setTileCacheSize(int size) {
        tileCacheSize.set(Math.max(0, size));
    }

    public void setChunkCacheSize(int size) {
        chunkCacheSize.set(Math.max(0, size));
    }

    public boolean shouldLogNow(long intervalMillis) {
        long now = System.currentTimeMillis();
        long previous = lastLogMillis.get();
        if (now - previous < intervalMillis) {
            return false;
        }
        return lastLogMillis.compareAndSet(previous, now);
    }

    public Snapshot snapshot() {
        long frames = Math.max(1L, frameCount.get());

        double avgFrameMs = nanosToMs(totalFrameNanos.get() / (double) frames);
        double avgRenderMs = nanosToMs(totalRenderNanos.get() / (double) frames);
        double fpsEstimate = avgFrameMs > 0.0 ? 1000.0 / avgFrameMs : 0.0;

        long totalCacheEvents = tileCacheHits.get() + tileCacheMisses.get();
        double cacheHitRate = totalCacheEvents > 0
            ? (100.0 * tileCacheHits.get() / totalCacheEvents)
            : 0.0;

        double avgTileGenMs = tileGenerationCount.get() > 0
            ? nanosToMs(totalTileGenerationNanos.get() / (double) tileGenerationCount.get())
            : 0.0;

        double avgJniMs = jniCallCount.get() > 0
            ? nanosToMs(totalJniNanos.get() / (double) jniCallCount.get())
            : 0.0;

        return new Snapshot(
            frames,
            fpsEstimate,
            avgFrameMs,
            avgRenderMs,
            tilesDrawnLastFrame.get(),
            tileDrawCount.get(),
            tileCacheHits.get(),
            tileCacheMisses.get(),
            cacheHitRate,
            queueDepth.get(),
            tileCacheSize.get(),
            chunkCacheSize.get(),
            tileGenerationCount.get(),
            avgTileGenMs,
            jniCallCount.get(),
            avgJniMs
        );
    }

    private static double nanosToMs(double nanos) {
        return nanos / 1_000_000.0;
    }

    public static final class Snapshot {
        public final long frames;
        public final double fpsEstimate;
        public final double avgFrameMs;
        public final double avgRenderMs;
        public final int tilesLastFrame;
        public final long tilesTotal;
        public final long tileCacheHits;
        public final long tileCacheMisses;
        public final double tileCacheHitRate;
        public final int queueDepth;
        public final int tileCacheSize;
        public final int chunkCacheSize;
        public final long tileGenerations;
        public final double avgTileGenerationMs;
        public final long jniCalls;
        public final double avgJniMs;

        private Snapshot(
            long frames,
            double fpsEstimate,
            double avgFrameMs,
            double avgRenderMs,
            int tilesLastFrame,
            long tilesTotal,
            long tileCacheHits,
            long tileCacheMisses,
            double tileCacheHitRate,
            int queueDepth,
            int tileCacheSize,
            int chunkCacheSize,
            long tileGenerations,
            double avgTileGenerationMs,
            long jniCalls,
            double avgJniMs
        ) {
            this.frames = frames;
            this.fpsEstimate = fpsEstimate;
            this.avgFrameMs = avgFrameMs;
            this.avgRenderMs = avgRenderMs;
            this.tilesLastFrame = tilesLastFrame;
            this.tilesTotal = tilesTotal;
            this.tileCacheHits = tileCacheHits;
            this.tileCacheMisses = tileCacheMisses;
            this.tileCacheHitRate = tileCacheHitRate;
            this.queueDepth = queueDepth;
            this.tileCacheSize = tileCacheSize;
            this.chunkCacheSize = chunkCacheSize;
            this.tileGenerations = tileGenerations;
            this.avgTileGenerationMs = avgTileGenerationMs;
            this.jniCalls = jniCalls;
            this.avgJniMs = avgJniMs;
        }

        public String toLogLine() {
            return String.format(
                "frames=%d fps=%.1f frame=%.2fms render=%.2fms tiles(frame/total)=%d/%d cacheHit=%.1f%% (h=%d m=%d) queue=%d tileCache=%d chunkCache=%d tileGen=%d avgTileGen=%.2fms jni=%d avgJni=%.3fms",
                frames,
                fpsEstimate,
                avgFrameMs,
                avgRenderMs,
                tilesLastFrame,
                tilesTotal,
                tileCacheHitRate,
                tileCacheHits,
                tileCacheMisses,
                queueDepth,
                tileCacheSize,
                chunkCacheSize,
                tileGenerations,
                avgTileGenerationMs,
                jniCalls,
                avgJniMs
            );
        }
    }
}
