package net.serlith.fish.async;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.serlith.fish.FishConfig;
import net.serlith.fish.async.thread.WorldTickThread;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public class AsyncWorldTicking {

    private static final Semaphore SEMAPHORE = new Semaphore(FishConfig.ASYNC.WORLD_TICKING._THREADS);

    @SuppressWarnings("ConstantConditions")
    public static void tickWorlds(Iterable<ServerLevel> worlds, BooleanSupplier hasTimeLeft) {
        Queue<CompletableFuture<Void>> tasks = new ConcurrentLinkedQueue<>();
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
        }
    }

}
