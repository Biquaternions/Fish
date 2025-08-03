package net.serlith.fish.async;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.serlith.fish.FishConfig;
import net.serlith.fish.async.thread.WorldTickThread;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

public class AsyncWorldTicking {

    private static final Semaphore SEMAPHORE = new Semaphore(FishConfig.ASYNC.WORLD_TICKING._THREADS);
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final Condition SAFE_TO_TICK = LOCK.newCondition();
    private static final Condition SAFE_TO_PROCESS = LOCK.newCondition();
    private static volatile boolean IS_TICKING_WORLDS = false;
    private static final AtomicInteger TASKS_TO_WAIT = new AtomicInteger(0);

    @SuppressWarnings("ConstantConditions")
    public static void tickWorlds(Iterable<ServerLevel> worlds, BooleanSupplier hasTimeLeft) {
        Queue<CompletableFuture<Void>> tasks = new ArrayDeque<>();
        AsyncWorldTicking.signalStartTicking();
        try {
            for (ServerLevel serverLevel : worlds) {
                serverLevel.hasPhysicsEvent = org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - BlockPhysicsEvent
                serverLevel.hasEntityMoveEvent = io.papermc.paper.event.entity.EntityMoveEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - Add EntityMoveEvent
                serverLevel.updateLagCompensationTick(); // Paper - lag compensation
                net.minecraft.world.level.block.entity.HopperBlockEntity.skipHopperEvents = serverLevel.paperConfig().hopper.disableMoveEvent || org.bukkit.event.inventory.InventoryMoveItemEvent.getHandlerList().getRegisteredListeners().length == 0; // Paper - Perf: Optimize Hoppers

                SEMAPHORE.acquire();
                tasks.offer(CompletableFuture.runAsync(() -> {
                    try {
                        WorldTickThread currentThread = (WorldTickThread) Thread.currentThread();
                        currentThread.setTickingWorld(serverLevel);

                        long start = Util.getNanos();
                        serverLevel.tick(hasTimeLeft);
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
                        SEMAPHORE.release();
                    }
                }, serverLevel.tickExecutor));
                serverLevel.explosionDensityCache.clear(); // Paper - Optimize explosions
            }
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            AsyncWorldTicking.signalDoneTicking();
        }
    }

    private static void signalStartTicking() {
        LOCK.lock();
        try {
            while(TASKS_TO_WAIT.get() > 0) {
                SAFE_TO_TICK.await();
            }
            IS_TICKING_WORLDS = true;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            LOCK.unlock();
        }
    }

    private static void signalDoneTicking() {
        LOCK.lock();
        try {
            IS_TICKING_WORLDS = false;
            SAFE_TO_PROCESS.signalAll();
        } finally {
            LOCK.unlock();
        }
    }


    public static void signalStartMethodCall() {
        LOCK.lock();
        try {
            while (IS_TICKING_WORLDS) {
                SAFE_TO_PROCESS.await();
            }
            TASKS_TO_WAIT.incrementAndGet();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            LOCK.unlock();
        }
    }

    public static void signalDoneMethodCall() {
        LOCK.lock();
        try {
            TASKS_TO_WAIT.decrementAndGet();
            if (TASKS_TO_WAIT.get() <= 0) {
                SAFE_TO_TICK.signal();
            }
        } finally {
            LOCK.unlock();
        }
    }

}
