package cubeium.cubeium.world.processing;

import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;
import cubeium.cubeium.world.generation.WorldGenerationManager;
import cubeium.cubeium.world.data.WorldDataCache;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages background processing of world generation tasks.
 * Provides efficient, non-blocking world data generation with priority queues.
 */
public class BackgroundWorldProcessor {
    private static final int DEFAULT_THREAD_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final int MAX_QUEUE_SIZE = 1000;
    private static final long STATS_UPDATE_INTERVAL = 5000; // 5 seconds
    
    // Thread pool and queues
    private final ExecutorService executor;
    private final PriorityBlockingQueue<GenerationTask> taskQueue;
    private final Map<TaskKey, GenerationTask> activeTasks;
    private final AtomicBoolean isRunning;
    private final AtomicBoolean isShutdown;
    
    // Components
    private final WorldGenerationManager worldManager;
    private final WorldDataCache dataCache;
    
    // Statistics
    private final AtomicLong totalTasksProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong tasksInProgress = new AtomicLong(0);
    private volatile long lastStatsUpdate = System.currentTimeMillis();
    
    // Listeners
    private final Set<Consumer<ProcessingStats>> statsListeners = ConcurrentHashMap.newKeySet();
    private final Map<Class<? extends GenerationTask>, Set<Consumer<? extends GenerationTask>>> taskListeners = new ConcurrentHashMap<>();
    
    /**
     * Create a BackgroundWorldProcessor with default thread count
     * @param worldManager WorldGenerationManager instance
     * @param dataCache WorldDataCache instance
     */
    public BackgroundWorldProcessor(WorldGenerationManager worldManager, WorldDataCache dataCache) {
        this(worldManager, dataCache, DEFAULT_THREAD_COUNT);
    }
    
    /**
     * Create a BackgroundWorldProcessor with custom thread count
     * @param worldManager WorldGenerationManager instance
     * @param dataCache WorldDataCache instance
     * @param threadCount Number of worker threads
     */
    public BackgroundWorldProcessor(WorldGenerationManager worldManager, WorldDataCache dataCache, int threadCount) {
        this.worldManager = worldManager;
        this.dataCache = dataCache;
        this.taskQueue = new PriorityBlockingQueue<>();
        this.activeTasks = new ConcurrentHashMap<>();
        this.isRunning = new AtomicBoolean(false);
        this.isShutdown = new AtomicBoolean(false);
        
        // Create thread pool with custom thread factory
        this.executor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "WorldGen-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // Lower priority than main thread
            return t;
        });
        
        startProcessing();
    }
    
    /**
     * Start background processing
     */
    private void startProcessing() {
        if (!isRunning.compareAndSet(false, true)) {
            return; // Already running
        }
        
        // Start worker threads
        for (int i = 0; i < DEFAULT_THREAD_COUNT; i++) {
            executor.submit(this::processTaskLoop);
        }
        
        // Start stats update thread
        executor.submit(this::statsUpdateLoop);
    }
    
    /**
     * Main task processing loop
     */
    private void processTaskLoop() {
        while (!isShutdown.get()) {
            try {
                GenerationTask task = taskQueue.poll(1, TimeUnit.SECONDS);
                if (task != null) {
                    processTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in background world processor: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Process a single generation task
     */
    private void processTask(GenerationTask task) {
        long startTime = System.currentTimeMillis();
        tasksInProgress.incrementAndGet();
        
        try {
            // Mark task as active
            activeTasks.put(task.getKey(), task);
            
            // Execute the task
            task.execute(worldManager, dataCache);
            
            // Notify listeners
            notifyTaskCompleted(task);
            
            // Update statistics
            long processingTime = System.currentTimeMillis() - startTime;
            totalTasksProcessed.incrementAndGet();
            totalProcessingTimeMs.addAndGet(processingTime);
            
        } catch (Exception e) {
            task.setError(e);
            System.err.println("Task execution failed: " + e.getMessage());
        } finally {
            // Remove from active tasks
            activeTasks.remove(task.getKey());
            tasksInProgress.decrementAndGet();
        }
    }
    
    /**
     * Statistics update loop
     */
    private void statsUpdateLoop() {
        while (!isShutdown.get()) {
            try {
                Thread.sleep(STATS_UPDATE_INTERVAL);
                
                if (System.currentTimeMillis() - lastStatsUpdate >= STATS_UPDATE_INTERVAL) {
                    notifyStatsListeners();
                    lastStatsUpdate = System.currentTimeMillis();
                }
                
                // Perform cache maintenance
                dataCache.performMaintenance();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // ===============================
    // Task Submission Methods
    // ===============================
    
    /**
     * Submit a biome generation task
     * @param x Region X coordinate
     * @param z Region Z coordinate  
     * @param width Region width
     * @param height Region height
     * @param scale Scale factor
     * @param priority Task priority
     * @return CompletableFuture with BiomeRegion result
     */
    public CompletableFuture<BiomeGenerator.BiomeRegion> submitBiomeGeneration(int x, int z, int width, int height, int scale, TaskPriority priority) {
        BiomeGenerationTask task = new BiomeGenerationTask(x, z, width, height, scale, priority);
        return submitTask(task);
    }
    
    /**
     * Submit a structure search task
     * @param structureType Structure type ID
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Search radius
     * @param maxResults Maximum results
     * @param priority Task priority
     * @return CompletableFuture with structure list result
     */
    public CompletableFuture<List<StructureGenerator.StructurePos>> submitStructureSearch(int structureType, int centerX, int centerZ, int radius, int maxResults, TaskPriority priority) {
        StructureSearchTask task = new StructureSearchTask(structureType, centerX, centerZ, radius, maxResults, priority);
        return submitTask(task);
    }
    
    /**
     * Submit a chunk data generation task
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param priority Task priority
     * @return CompletableFuture with CachedChunkData result
     */
    public CompletableFuture<WorldDataCache.CachedChunkData> submitChunkGeneration(int chunkX, int chunkZ, TaskPriority priority) {
        ChunkGenerationTask task = new ChunkGenerationTask(chunkX, chunkZ, priority);
        return submitTask(task);
    }
    
    /**
     * Submit a generic task
     * @param task GenerationTask to submit
     * @return CompletableFuture with task result
     */
    @SuppressWarnings("unchecked")
    private <T> CompletableFuture<T> submitTask(GenerationTask task) {
        if (isShutdown.get()) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Processor is shutdown"));
            return future;
        }
        
        // Check for duplicate task
        GenerationTask existing = activeTasks.get(task.getKey());
        if (existing != null) {
            return (CompletableFuture<T>) existing.getFuture();
        }
        
        // Check queue size
        if (taskQueue.size() >= MAX_QUEUE_SIZE) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Task queue is full"));
            return future;
        }
        
        // Add to queue
        taskQueue.offer(task);
        return (CompletableFuture<T>) task.getFuture();
    }
    
    // ===============================
    // Listener Management
    // ===============================
    
    /**
     * Add a statistics listener
     * @param listener Consumer that receives ProcessingStats
     */
    public void addStatsListener(Consumer<ProcessingStats> listener) {
        statsListeners.add(listener);
    }
    
    /**
     * Remove a statistics listener
     * @param listener Consumer to remove
     */
    public void removeStatsListener(Consumer<ProcessingStats> listener) {
        statsListeners.remove(listener);
    }
    
    /**
     * Add a task completion listener
     * @param taskClass Class of task to listen for
     * @param listener Consumer that receives completed tasks
     */
    @SuppressWarnings("unchecked")
    public <T extends GenerationTask> void addTaskListener(Class<T> taskClass, Consumer<T> listener) {
        taskListeners.computeIfAbsent(taskClass, k -> ConcurrentHashMap.newKeySet()).add((Consumer<? extends GenerationTask>) listener);
    }
    
    /**
     * Remove a task completion listener
     * @param taskClass Class of task
     * @param listener Consumer to remove
     */
    public <T extends GenerationTask> void removeTaskListener(Class<T> taskClass, Consumer<T> listener) {
        Set<Consumer<? extends GenerationTask>> listeners = taskListeners.get(taskClass);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }
    
    private void notifyStatsListeners() {
        ProcessingStats stats = getProcessingStats();
        for (Consumer<ProcessingStats> listener : statsListeners) {
            try {
                listener.accept(stats);
            } catch (Exception e) {
                System.err.println("Error notifying stats listener: " + e.getMessage());
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void notifyTaskCompleted(GenerationTask task) {
        Set<Consumer<? extends GenerationTask>> listeners = taskListeners.get(task.getClass());
        if (listeners != null) {
            for (Consumer<? extends GenerationTask> listener : listeners) {
                try {
                    ((Consumer<GenerationTask>) listener).accept(task);
                } catch (Exception e) {
                    System.err.println("Error notifying task listener: " + e.getMessage());
                }
            }
        }
    }
    
    // ===============================
    // Status and Management
    // ===============================
    
    /**
     * Get current processing statistics
     * @return ProcessingStats with current metrics
     */
    public ProcessingStats getProcessingStats() {
        long totalTasks = totalTasksProcessed.get();
        long totalTime = totalProcessingTimeMs.get();
        double avgProcessingTime = totalTasks > 0 ? (double) totalTime / totalTasks : 0.0;
        
        return new ProcessingStats(
            taskQueue.size(),
            activeTasks.size(),
            totalTasks,
            avgProcessingTime,
            tasksInProgress.get(),
            isRunning.get()
        );
    }
    
    /**
     * Clear all pending tasks
     */
    public void clearQueue() {
        taskQueue.clear();
    }
    
    /**
     * Get number of pending tasks
     * @return Task queue size
     */
    public int getPendingTaskCount() {
        return taskQueue.size();
    }
    
    /**
     * Get number of active tasks
     * @return Active task count
     */
    public int getActiveTaskCount() {
        return activeTasks.size();
    }
    
    /**
     * Check if processor is running
     * @return True if running
     */
    public boolean isRunning() {
        return isRunning.get() && !isShutdown.get();
    }
    
    /**
     * Shutdown the processor
     */
    public void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            return; // Already shut down
        }
        
        isRunning.set(false);
        taskQueue.clear();
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Task priority levels
     */
    public enum TaskPriority {
        LOW(3),
        NORMAL(2),
        HIGH(1),
        URGENT(0);
        
        public final int value;
        
        TaskPriority(int value) {
            this.value = value;
        }
    }
    
    /**
     * Base class for generation tasks
     */
    public static abstract class GenerationTask implements Comparable<GenerationTask> {
        protected final TaskPriority priority;
        protected final long creationTime;
        protected final CompletableFuture<Object> future;
        protected volatile Exception error;
        
        protected GenerationTask(TaskPriority priority) {
            this.priority = priority;
            this.creationTime = System.currentTimeMillis();
            this.future = new CompletableFuture<>();
        }
        
        public abstract TaskKey getKey();
        public abstract void execute(WorldGenerationManager worldManager, WorldDataCache dataCache) throws Exception;
        
        public CompletableFuture<Object> getFuture() {
            return future;
        }
        
        public void setError(Exception error) {
            this.error = error;
            future.completeExceptionally(error);
        }
        
        @Override
        public int compareTo(GenerationTask other) {
            // Higher priority first, then older tasks first
            int priorityCompare = Integer.compare(this.priority.value, other.priority.value);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.creationTime, other.creationTime);
        }
    }
    
    /**
     * Task key for deduplication
     */
    public static class TaskKey {
        private final String type;
        private final String key;
        private final int hashCode;
        
        public TaskKey(String type, String key) {
            this.type = type;
            this.key = key;
            this.hashCode = (type + ":" + key).hashCode();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TaskKey)) return false;
            TaskKey other = (TaskKey) obj;
            return type.equals(other.type) && key.equals(other.key);
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
        
        @Override
        public String toString() {
            return type + ":" + key;
        }
    }
    
    /**
     * Processing statistics
     */
    public static class ProcessingStats {
        public final int queueSize;
        public final int activeTaskCount;
        public final long totalTasksProcessed;
        public final double averageProcessingTimeMs;
        public final long tasksInProgress;
        public final boolean isRunning;
        
        ProcessingStats(int queueSize, int activeTaskCount, long totalTasksProcessed, 
                       double averageProcessingTimeMs, long tasksInProgress, boolean isRunning) {
            this.queueSize = queueSize;
            this.activeTaskCount = activeTaskCount;
            this.totalTasksProcessed = totalTasksProcessed;
            this.averageProcessingTimeMs = averageProcessingTimeMs;
            this.tasksInProgress = tasksInProgress;
            this.isRunning = isRunning;
        }
        
        @Override
        public String toString() {
            return String.format("BackgroundProcessor: %d queued, %d active, %d processed (%.1fms avg)",
                queueSize, activeTaskCount, totalTasksProcessed, averageProcessingTimeMs);
        }
    }
    
    // ===============================
    // Specific Task Implementations
    // ===============================
    
    private static class BiomeGenerationTask extends GenerationTask {
        private final int x, z, width, height, scale;
        
        BiomeGenerationTask(int x, int z, int width, int height, int scale, TaskPriority priority) {
            super(priority);
            this.x = x;
            this.z = z;
            this.width = width;
            this.height = height;
            this.scale = scale;
        }
        
        @Override
        public TaskKey getKey() {
            return new TaskKey("biome", String.format("%d,%d,%d,%d,%d", x, z, width, height, scale));
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void execute(WorldGenerationManager worldManager, WorldDataCache dataCache) throws Exception {
            BiomeGenerator.BiomeRegion result = worldManager.getBiomeGenerator().generateBiomes(x, z, width, height, scale);
            dataCache.cacheBiomeRegion(result);
            future.complete(result);
        }
    }
    
    private static class StructureSearchTask extends GenerationTask {
        private final int structureType, centerX, centerZ, radius, maxResults;
        
        StructureSearchTask(int structureType, int centerX, int centerZ, int radius, int maxResults, TaskPriority priority) {
            super(priority);
            this.structureType = structureType;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.maxResults = maxResults;
        }
        
        @Override
        public TaskKey getKey() {
            return new TaskKey("structure", String.format("%d,%d,%d,%d,%d", structureType, centerX, centerZ, radius, maxResults));
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void execute(WorldGenerationManager worldManager, WorldDataCache dataCache) throws Exception {
            List<StructureGenerator.StructurePos> result = worldManager.getStructureGenerator()
                .findStructures(structureType, centerX, centerZ, radius, maxResults);
            dataCache.cacheStructureSearch(structureType, centerX, centerZ, radius, maxResults, result);
            future.complete(result);
        }
    }
    
    private static class ChunkGenerationTask extends GenerationTask {
        private final int chunkX, chunkZ;
        
        ChunkGenerationTask(int chunkX, int chunkZ, TaskPriority priority) {
            super(priority);
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
        
        @Override
        public TaskKey getKey() {
            return new TaskKey("chunk", String.format("%d,%d", chunkX, chunkZ));
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public void execute(WorldGenerationManager worldManager, WorldDataCache dataCache) throws Exception {
            // Generate biome data for the chunk
            BiomeGenerator.BiomeRegion biomeRegion = worldManager.getBiomeGenerator().generateChunkBiomes(chunkX, chunkZ);
            
            // Find structures in the chunk area
            int centerX = chunkX * 16 + 8;
            int centerZ = chunkZ * 16 + 8;
            Set<StructureGenerator.StructurePos> structures = new HashSet<>();
            
            // Search for common structures
            for (int structureType : worldManager.getSupportedStructures()) {
                List<StructureGenerator.StructurePos> found = worldManager.getStructureGenerator()
                    .findStructures(structureType, centerX, centerZ, 32, 5);
                structures.addAll(found);
            }
            
            // Create cached chunk data
            WorldDataCache.CachedChunkData result = new WorldDataCache.CachedChunkData(
                chunkX, chunkZ, biomeRegion.biomes, structures, biomeRegion.seed);
            
            dataCache.cacheChunk(result);
            future.complete(result);
        }
    }
}
