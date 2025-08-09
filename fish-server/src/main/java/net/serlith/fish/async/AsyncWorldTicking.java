package net.serlith.fish.async;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.serlith.fish.FishConfig;
import net.serlith.fish.async.thread.WorldTickThread;
import net.serlith.fish.util.WorldTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.HeightMap;
import org.bukkit.craftbukkit.CraftHeightMap;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.ApiStatus;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public class AsyncWorldTicking {

    private static final Logger LOGGER = LogManager.getLogger("Fish Async World Ticking");
    private static final Semaphore SEMAPHORE = new Semaphore(FishConfig.ASYNC.WORLD_TICKING._THREADS);
    private static final CompletableFuture<?>[] EMPTY_ARRAY = new CompletableFuture[0];

    public static void tickWorlds(Iterable<ServerLevel> worlds, BooleanSupplier hasTimeLeft) {
        Queue<CompletableFuture<Void>> tasks = new ArrayDeque<>();
        try {
            for (ServerLevel serverLevel : worlds) {
                serverLevel.hasPhysicsEvent = org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - BlockPhysicsEvent
                serverLevel.hasEntityMoveEvent = io.papermc.paper.event.entity.EntityMoveEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - Add EntityMoveEvent
                serverLevel.updateLagCompensationTick(); // Paper - lag compensation
                net.minecraft.world.level.block.entity.HopperBlockEntity.skipHopperEvents = serverLevel.paperConfig().hopper.disableMoveEvent || org.bukkit.event.inventory.InventoryMoveItemEvent.getHandlerList().getRegisteredListeners().length == 0; // Paper - Perf: Optimize Hoppers

                SEMAPHORE.acquire();
                tasks.offer(CompletableFuture.runAsync(() -> {
                    serverLevel.fish$lock.writeLock().lock();
                    try {
                        WorldTickThread currentThread = (WorldTickThread) Thread.currentThread();
                        currentThread.setTickingWorld(serverLevel);

                        long start = Util.getNanos();
                        serverLevel.tick(hasTimeLeft);
                        Runnable task;
                        while ((task = serverLevel.fish$endOfTickTasks.poll()) != null) {
                            task.run();
                        }
                        long duration = Util.getNanos() - start;

                        int tickCount = MinecraftServer.getServer().getTickCount();
                        serverLevel.tickTimes5s.fish$add(tickCount, duration);
                        serverLevel.tickTimes10s.fish$add(tickCount, duration);
                        serverLevel.tickTimes60s.fish$add(tickCount, duration);

                    } catch (Throwable var7) {
                        CrashReport crashReport = CrashReport.forThrowable(var7, "Exception ticking world [" + serverLevel.getWorld().getName() + "]");
                        serverLevel.fillReportDetails(crashReport);
                        throw new ReportedException(crashReport);
                    } finally {
                        serverLevel.fish$lock.writeLock().unlock();
                        SEMAPHORE.release();
                    }
                }, serverLevel.tickExecutor));
                serverLevel.explosionDensityCache.clear(); // Paper - Optimize explosions
            }
            CompletableFuture.allOf(tasks.toArray(EMPTY_ARRAY)).join();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T scheduleForEndOfWorldTick(ServerLevel level, Callable<T> callable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        if (level.fish$lock.readLock().tryLock()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level.fish$lock.readLock().unlock();
            }
        } else {
            WorldTask<T> task = new WorldTask<>(callable);
            level.fish$endOfTickTasks.offer(task);
            return task.get();
        }
    }

    public static void scheduleVoidForEndOfWorldTick(ServerLevel level, Runnable runnable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        if (level.fish$lock.readLock().tryLock()) {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level.fish$lock.readLock().unlock();
            }
        } else {
            level.fish$endOfTickTasks.offer(runnable);
        }
    }

    /*
     * Some tasks (like loading chunks), have a chance of doing it sync, which can cause a deadlock.
     * This method guarantees that these potentially dangerous tasks are always enqueued instead.
     * The recommendation is to never call these methods async with PWT, it is only left here for compatibility
     *   purposes with the few scenarios where this cannot be avoided.
     *
     * Known scenarios:
     *   1. CraftWorld#getHighestBlockYAt <- Common for Random Teleport plugins.
     *
     * Some other tasks call NMS functions that have been protected from async reads.
     * To respect the protected code, these tasks will be enqueued anyway.
     * Other tasks call events that are meant to be sync anyway so they will also be enqueued.
     * And some others may result in try to spawn entities (xp orbs) async.
     *
     * Known scenarios:
     *   1. CraftBlock#setBlockState <- Internally calls Level#setBlock.
     *   2. CraftBlock#breakNaturally <- Internally calls Block#dropResources and Level#setBlock.
     *   3. CraftBlock#applyBoneMeal <- Calls StructureGrowEvent, BlockFertilizeEvent and also could use ThreadLocal
     *        variables on which PWT depends. This one could be directly blocked instead of enqueued.
     *
     */
    public static <T> T scheduleForEndOfWorldTickDirect(ServerLevel level, Callable<T> callable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        WorldTask<T> task = new WorldTask<>(callable);
        level.fish$endOfTickTasks.offer(task);
        return task.get();
    }

    /*
     * Some tasks (like loading chunks), have a chance of doing it sync, which can cause a deadlock.
     * This method guarantees that these potentially dangerous tasks are always enqueued instead.
     * Compared to AsyncWorldTicking#scheduleForEndOfWorldTickDirect this method will only
     *   exist until I have enough free time to verify if no plugins actually call protected methods async (none should).
     *
     * Known scenarios:
     *   1. CraftWorld#setBiome <- Probably used in FAWE (I'm not sure).
     *
     * Some other tasks call NMS functions that have been protected from async reads.
     *   1. CraftBlock#setData <- Internally calls Level#setBlock.
     *
     */
    public static void scheduleVoidForEndOfWorldTickDirect(ServerLevel level, Runnable runnable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        level.fish$endOfTickTasks.offer(runnable);
    }

    /*
     * These functions below are EXPERIMENTAL functions meant to solve a possible deadlock situation
     *   without forcing the entire task to be executed sync.
     * It is marked as experimental because I, personally, don't like verbose functions or even worse,
     *   having to compete for the lock twice.
     * Both functions, after retrieving a chunk, still have tasks that might not be safe to be executed async,
     *   being ChunkAccess#setBiome and Heightmap#primeHeightmaps.
     * It is probably the best approach, since only the wait is done async and the rest can still be done async,
     *   however, I have the feeling the logic can still be simplified, since the current state makes it harder to
     *   follow the logic up, which can make it less maintainable.
     *
     * The experimental annotation will be removed once I have enough time to play around with these functions
     *   to find a way to abbreviate them, and make them easier to read.
     *
     */

    @ApiStatus.Experimental
    @SuppressWarnings("ConstantConditions")
    public static void scheduleWorldSetBiome(ServerLevel level, int x, int y, int z, Holder<Biome> bb) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        if (level.fish$lock.readLock().tryLock()) {
            CompletableFuture<ChunkAccess> result = null;
            try {
                BlockPos pos = new BlockPos(x, 0, z);
                if (level.hasChunkAt(pos)) {
                    result = level.fish$getChunkAt(pos);
                }
                if (result == null) { // Early return
                    throw new IllegalStateException("Chunk not loaded when requested");
                }

                // If the future is not waiting, keep going and return
                ChunkAccess chunk;
                if (result.isDone() && (chunk = result.join()) != null) {
                    AsyncWorldTicking.doSetChunkBiome(chunk, x, y, z, bb);
                    return;
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level.fish$lock.readLock().unlock();
            }

            // If the future was waiting, wait outside the read lock.
            // Most reads will never each this point, this will only happen is the chunks falls to load and has to fallback
            //   and only if the fallback also fails to read the chunk async.
            ChunkAccess chunk;
            if ((chunk = result.join()) != null) { // Too verbose to my taste, but has to be done
                if (level.fish$lock.readLock().tryLock()) {
                    try {
                        AsyncWorldTicking.doSetChunkBiome(chunk, x, y, z, bb);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    } finally {
                        level.fish$lock.readLock().unlock();
                    }
                } else {
                    level.fish$endOfTickTasks.offer(() -> AsyncWorldTicking.doSetChunkBiome(chunk, x, y, z, bb));
                }
            }

        } else {
            level.fish$endOfTickTasks.offer(() -> {
                BlockPos pos = new BlockPos(x, 0, z);
                if (level.hasChunkAt(pos)) {
                    LevelChunk chunk = level.getChunkAt(pos);
                    if (chunk != null) {
                        AsyncWorldTicking.doSetChunkBiome(chunk, x, y, z, bb);
                    }
                }
            });
        }
    }

    private static void doSetChunkBiome(ChunkAccess chunk, int x, int y, int z, Holder<Biome> bb) {
        chunk.setBiome(x >> 2, y >> 2, z >> 2, bb);
        chunk.markUnsaved(); // SPIGOT-2890
    }

    @ApiStatus.Experimental
    @SuppressWarnings("ConstantConditions")
    public static int scheduleWorldGetHighestBlockYAt(ServerLevel level, int x, int z, HeightMap heightMap) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        if (level.fish$lock.readLock().tryLock()) {
            CompletableFuture<ChunkAccess> result;
            try {
                CraftWorld.fish$warnUnsafeChunk("getting a faraway chunk", x >> 4, z >> 4); // Paper
                result = level.fish$getChunk(x >> 4, z >> 4);
                if (result == null) { // Early return
                    throw new IllegalStateException("Chunk not loaded when requested");
                }

                ChunkAccess chunk;
                if (result.isDone() && (chunk = result.join()) != null) {
                    return AsyncWorldTicking.doGetHighestBlockYAt(chunk, x, z, heightMap);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level.fish$lock.readLock().unlock();
            }

            // If the future was waiting, wait outside the read lock.
            // Most reads will never each this point, this will only happen is the chunks falls to load and has to fallback
            //   and only if the fallback also fails to read the chunk async.
            ChunkAccess chunk = result.join();
            if (chunk == null) { // Early return
                throw new IllegalStateException("Chunk not loaded when requested");
            }

            // Sadly, this means the thread will have to compete for the lock again.
            // As stated above, this scenario is extremely rare.
            if (level.fish$lock.readLock().tryLock()) { // Too verbose :(
                try {
                    return AsyncWorldTicking.doGetHighestBlockYAt(chunk, x, z, heightMap);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    level.fish$lock.readLock().unlock();
                }
            } else {
                WorldTask<Integer> task = new WorldTask<>(() -> AsyncWorldTicking.doGetHighestBlockYAt(chunk, x, z, heightMap));
                level.fish$endOfTickTasks.offer(task);
                return task.get();
            }

        } else {
            WorldTask<Integer> task = new WorldTask<>(() -> {
                CraftWorld.fish$warnUnsafeChunk("getting a faraway chunk", x >> 4, z >> 4); // Paper
                return AsyncWorldTicking.doGetHighestBlockYAt(level.getChunk(x >> 4, z >> 4), x, z, heightMap);
            });
            level.fish$endOfTickTasks.offer(task);
            return task.get();
        }
    }

    private static int doGetHighestBlockYAt(ChunkAccess chunk, int x, int z, HeightMap heightMap) {
        return chunk.getHeight(CraftHeightMap.toNMS(heightMap), x, z);
    }

    /**
     * Prints a stacktrace of the asynchronous access, but does not prevent it
     * <br>
     * The idea of this function is to help server designing by minimizing the number of async accesses if those cannot be avoided.
     * Once the server is done and ready for production, this can be safely disabled.
     * The server owner should be aware of the consequences of the async accesses left on the server.
     * <br>
     * A typical scenario where an async access cannot be avoided is on Random Teleport plugins, unless you're developing your own.
     *
     */
    private static void logAsyncAccess() {
        Thread thread = Thread.currentThread();
        LOGGER.warn("A plugin accessed world/block data asynchronously from thread \"{}\".", thread.getName());
        for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
            LOGGER.warn("\tat {}", stackTraceElement.toString());
        }
    }

}
