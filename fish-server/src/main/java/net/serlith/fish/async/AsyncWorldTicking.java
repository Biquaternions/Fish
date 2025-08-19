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
import net.serlith.fish.util.CallableWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public class AsyncWorldTicking {

    private static final Logger LOGGER = LogManager.getLogger("Fish World Ticking");
    private static final Semaphore SEMAPHORE = new Semaphore(FishConfig.ASYNC.WORLD_TICKING._THREADS);
    private static final CompletableFuture<?>[] EMPTY_ARRAY = new CompletableFuture[0];
    private static final Queue<Runnable> END_OF_TICK_TASKS = new ConcurrentLinkedQueue<>();

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
                        AsyncWorldTicking.processScheduledWorldTasks(serverLevel);
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
            AsyncWorldTicking.processScheduledTasks();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void processScheduledWorldTasks(ServerLevel level) {
        Runnable task;
        while ((task = level.fish$endOfTickTasks.poll()) != null) {
            task.run();
        }
    }

    private static void processScheduledTasks() {
        Runnable task;
        while ((task = END_OF_TICK_TASKS.poll()) != null) {
            task.run();
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
            CallableWrapper<T> task = new CallableWrapper<>(callable);
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
     * Used to schedule tasks after all worlds are done ticking.
     * Useful if an async task involves multiple worlds.
     * Possible use for async respawn in practice plugins (shouldn't be async imo, but idk).
     */
    public static void scheduleVoidForEndOfTick(Runnable runnable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        END_OF_TICK_TASKS.offer(runnable);
    }

    /*
     * Some tasks call CraftBlock#getNMS which has a chance of scheduling sync Chunk load, which therefore
     *   has a chance of causing a deadlock (at it will be loaded in the next tick, but will never leave this tick
     *   due to holding the read lock).
     * To prevent this scenario, these tasks will be enqueued directly.
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
        CallableWrapper<T> task = new CallableWrapper<>(callable);
        level.fish$endOfTickTasks.offer(task);
        return task.get();
    }

    /*
     * Some tasks call NMS functions that have been protected from async reads.
     * Internally also call the chunk system, which means this one has to be scheduled directly to
     *   prevent deadlocks.
     *
     * Known scenarios:
     *   1. CraftBlock#setData <- Internally calls Level#setBlock.
     *
     */
    public static void scheduleVoidForEndOfWorldTickDirect(ServerLevel level, Runnable runnable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        level.fish$endOfTickTasks.offer(runnable);
    }

    /*
     * The function below is marked as EXPERIMENTAL and is meant to still provide async access without
     *   risking a potential deadlock.
     * From all the functions guarded from the original Sparkly implementation, this is the only one
     *   that can actually risk data corruption (due to being a write operation) but also with risk
     *   of causing a deadlock (due to calling the chunk system).
     * While I personally don't like this function due to being too verbose and hard to follow, I'll only
     *   keep it here to provide plugin compatibility.
     * HOWEVER, this function must be avoided AT ALL COSTS while using Parallel World Ticking.
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
