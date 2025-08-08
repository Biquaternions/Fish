package net.serlith.fish.async;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.serlith.fish.FishConfig;
import net.serlith.fish.async.thread.WorldTickThread;
import net.serlith.fish.util.WorldTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    @SuppressWarnings("ConstantConditions")
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
                    serverLevel._fish_lock.writeLock().lock();
                    try {
                        WorldTickThread currentThread = (WorldTickThread) Thread.currentThread();
                        currentThread.setTickingWorld(serverLevel);

                        long start = Util.getNanos();
                        serverLevel.tick(hasTimeLeft);
                        Runnable task;
                        while ((task = serverLevel._fish_endOfTickTasks.poll()) != null) {
                            task.run();
                        }
                        long duration = Util.getNanos() - start;

                        int tickCount = MinecraftServer.getServer().getTickCount();
                        serverLevel.tickTimes5s._fish_add(tickCount, duration);
                        serverLevel.tickTimes10s._fish_add(tickCount, duration);
                        serverLevel.tickTimes60s._fish_add(tickCount, duration);

                    } catch (Throwable var7) {
                        CrashReport crashReport = CrashReport.forThrowable(var7, "Exception ticking world [" + serverLevel.getWorld().getName() + "]");
                        serverLevel.fillReportDetails(crashReport);
                        throw new ReportedException(crashReport);
                    } finally {
                        serverLevel._fish_lock.writeLock().unlock();
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
        if (level._fish_lock.readLock().tryLock()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level._fish_lock.readLock().unlock();
            }
        } else {
            WorldTask<T> task = new WorldTask<>(callable);
            level._fish_endOfTickTasks.offer(task);
            return task.get();
        }
    }

    public static void scheduleVoidForEndOfWorldTick(ServerLevel level, Runnable runnable) {
        if (FishConfig.ASYNC.WORLD_TICKING.LOG_ASYNC_ACCESSES) AsyncWorldTicking.logAsyncAccess();
        if (level._fish_lock.readLock().tryLock()) {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level._fish_lock.readLock().unlock();
            }
        } else {
            level._fish_endOfTickTasks.offer(runnable);
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
     * Other tasks call events that are meant to be sync anyway, those are (for now) also enqueued.
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
        level._fish_endOfTickTasks.offer(task);
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
        level._fish_endOfTickTasks.offer(runnable);
    }

    private static void logAsyncAccess() {
        Thread thread = Thread.currentThread();
        LOGGER.warn("A plugin accessed world/block data asynchronously from thread \"{}\".", thread.getName());
        for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
            LOGGER.warn("\tat {}", stackTraceElement.toString());
        }
    }

}
