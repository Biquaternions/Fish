package me.biquaternions.fish.threadedregions.executor;

import ca.spottedleaf.common.time.TickData;
import ca.spottedleaf.common.time.TickTime;
import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import me.biquaternions.fish.concurrent.thread.WorldTickThread;
import me.biquaternions.fish.threadedregions.RegionizedWorldData;
import me.biquaternions.fish.threadedregions.TickWorldExecutor;
import me.biquaternions.fish.threadedregions.scheduler.WorldRegionScheduler;
import me.biquaternions.fish.threadedregions.world.WorldEntityTickList;
import me.biquaternions.fish.threadedregions.world.WorldWaypointManager;
import me.biquaternions.fish.util.CallableWrapper;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.TreeType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

@NullMarked
public class ParallelTickWorldExecutor implements TickWorldExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Fish World Ticking");
    private static final CompletableFuture<?>[] EMPTY_ARRAY = new CompletableFuture[0];

    private static final long CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME = 25L * 1000L; // 25us
    private static final long MAX_CHUNK_EXEC_TIME = 1000L; // 1us
    private static final long TASK_EXECUTION_FAILURE_BACKOFF = 5L * 1000L; // 5us

    private final Queue<Runnable> endOfTickTasks = new ConcurrentLinkedQueue<>();
    private final Semaphore semaphore;
    private final ServerTickRateManager tickRateManager;
    private final ThreadLocal<@Nullable TreeType> treeType = new ThreadLocal<>();
    private final ThreadLocal<@Nullable BlockPos> sculkSourceBlockOverride = new ThreadLocal<>();
    private final ThreadLocal<Boolean> ignoreBlockEntityUpdates = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public ParallelTickWorldExecutor(final MinecraftServer server, final int tickets) {
        this.semaphore = new Semaphore(tickets);
        this.tickRateManager = server.tickRateManager();
    }

    @Override
    public void tickWorlds(final Iterable<ServerLevel> worlds, final BooleanSupplier hasTimeLeft) {
        final long tickInterval = this.tickRateManager.isSprinting() ? 0 : this.tickRateManager.nanosecondsPerTick();
        final Queue<CompletableFuture<Void>> tasks = new ArrayDeque<>();
        try {
            for (ServerLevel serverLevel : worlds) {
                serverLevel.hasPhysicsEvent = org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - BlockPhysicsEvent
                serverLevel.hasEntityMoveEvent = io.papermc.paper.event.entity.EntityMoveEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - Add EntityMoveEvent
                serverLevel.updateLagCompensationTick(); // Paper - lag compensation
                serverLevel.fish$worldData.skipHopperEvents = serverLevel.paperConfig().hopper.disableMoveEvent || org.bukkit.event.inventory.InventoryMoveItemEvent.getHandlerList().getRegisteredListeners().length == 0; // Paper - Perf: Optimize Hoppers

                this.semaphore.acquire();
                tasks.offer(CompletableFuture.runAsync(() -> {
                    serverLevel.fish$lock.writeLock().lock();
                    try {
                        serverLevel.fish$currentTickStart = System.nanoTime();
                        serverLevel.fish$tickSchedule.setNextPeriod(serverLevel.fish$currentTickStart, tickInterval);
                        serverLevel.fish$nextTickTimeNanos = serverLevel.fish$tickSchedule.getDeadline(tickInterval);

                        for (io.papermc.paper.threadedregions.EntityScheduler scheduler : serverLevel.fish$worldData.entitySchedulerTickList.getAllSchedulers()) {
                            net.minecraft.world.entity.Entity handle = scheduler.entity.getHandleRaw();
                            if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(handle) || scheduler.isRetired()) {
                                continue;
                            }
                            scheduler.executeTick();
                        }

                        serverLevel.tick(hasTimeLeft);
                        serverLevel.explosionDensityCache.clear(); // Paper - Optimize explosions
                        ((WorldRegionScheduler) Bukkit.getRegionScheduler()).tickWorld(serverLevel);
                        this.recordEndOfTick(serverLevel);

                    } catch (Throwable var7) {
                        final CrashReport crashReport = CrashReport.forThrowable(var7, "Exception ticking world [" + serverLevel.dimension().identifier() + "]");
                        serverLevel.fillReportDetails(crashReport);
                        throw new ReportedException(crashReport);
                    } finally {
                        serverLevel.fish$lock.writeLock().unlock();
                        this.semaphore.release();
                    }
                }, Objects.requireNonNull(serverLevel.fish$tickExecutor)));
            }
            CompletableFuture.allOf(tasks.toArray(EMPTY_ARRAY)).join();
            this.handleScheduledTasks();

        } catch (InterruptedException exception) {
            final CrashReport crashReport = CrashReport.forThrowable(exception, "Interrupted world ticking");
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public boolean shouldScheduleExecution() {
        return !TickThread.isTickThread();
    }

    @Override
    public boolean shouldScheduleExecution(final Level level) {
        return !TickThread.isTickThreadFor(level);
    }

    @Override
    public void preferOnlyTickThread(final String reason) {
        TickThread.ensureOnlyTickThread(reason);
    }

    @Override
    public void preferTickThreadOrAsyncThread(final Level level, final String reason) {
        TickThread.ensureTickThreadOrAsyncThread(level, reason);
    }

    @Override
    public void preferTickThread(final Level level, final String reason) {
        TickThread.ensureTickThread(level, reason);
    }

    @Override
    public void preferTickThread(final Level world, final BlockPos pos, final String reason) {
        TickThread.ensureTickThread(world, pos, reason);
    }

    @Override
    public void preferTickThread(final Level world, final AABB aabb, final String reason) {
        TickThread.ensureTickThread(world, aabb, reason);
    }

    @Override
    public void preferTickThread(final Level world, final int x, final int z, final String reason) {
        TickThread.ensureTickThread(world, x, z, reason);
    }

    @Override
    public void ensureTickThread(final Level level, final String reason) {
        TickThread.ensureTickThread(level, reason);
    }

    @Override
    public void ensureTickThread(final Level level, final int x, final int z, final String reason) {
        TickThread.ensureTickThread(level, x, z, reason);
    }

    @SuppressWarnings("resource")
    @Override
    public <T extends PacketListener> void ensureRunningOnSameThread(final Packet<T> packet, final T listener, final ServerLevel level) throws RunningOnDifferentThreadException {
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThreadFor(level)) {
            level.getServer().packetProcessor().scheduleIfPossible(listener, packet);
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    private void recordEndOfTick(final ServerLevel level) {
        final long prevStart = level.fish$lastTickStart;
        final long currStart = level.fish$currentTickStart;
        level.fish$lastTickStart = level.fish$currentTickStart;
        final long scheduledStart = level.fish$scheduledTickStart;
        level.fish$scheduledTickStart = level.fish$nextTickTimeNanos; // set scheduledStart for next tick

        final long now = Util.getNanos();
        final TickTime time = new TickTime(
            prevStart,
            scheduledStart,
            currStart,
            0L,
            now,
            0L,
            level.fish$taskExecutionTime,
            0L,
            false
        );

        level.fish$taskExecutionTime = 0L;
        this.addTickTime(level, time);
    }

    private void addTickTime(final ServerLevel level, final TickTime time) {
        synchronized (level.fish$statsLock) {
            level.fish$tickTimes5s.addDataFrom(time);
            level.fish$tickTimes10s.addDataFrom(time);
            level.fish$tickTimes15s.addDataFrom(time);
            level.fish$tickTimes60s.addDataFrom(time);
            this.clearTickTimeStatistics(level);
        }
    }

    private void clearTickTimeStatistics(final ServerLevel level) {
        level.fish$msptData5s = null;
    }

    private void handleScheduledTasks() {
        Runnable task;
        while ((task = this.endOfTickTasks.poll()) != null) {
            task.run();
        }
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
    private void logAsyncAccess() {
        final Thread thread = Thread.currentThread();
        LOGGER.warn("A plugin accessed world/block data asynchronously from thread \"{}\".", thread.getName());
        for (StackTraceElement stackTraceElement : thread.getStackTrace()) {
            LOGGER.warn("\tat {}", stackTraceElement);
        }
    }

    /**
     * Ticks mid-tick tasks for a single world.
     * See {@link MinecraftServer#moonrise$executeMidTickTasks()}
     *
     * @param world World to tick mid-tick tasks
     * @return If a task was executed
     */
    private boolean tickMidTickTasks(final ServerLevel world) {
        boolean executed = false;
        long currTime = System.nanoTime();
        if (currTime - world.moonrise$getLastMidTickFailure() <= TASK_EXECUTION_FAILURE_BACKOFF) {
            return false;
        }
        if (!world.getChunkSource().pollTask()) {
            // we need to back off if this fails
            world.moonrise$setLastMidTickFailure(currTime);
        } else {
            executed = true;
        }
        return executed;
    }

    @Override
    public TickData.@Nullable MSPTData getMSPTData5s(final ServerLevel level) {
        synchronized (level.fish$statsLock) {
            if (level.fish$msptData5s == null) {
                level.fish$msptData5s = level.fish$tickTimes5s.getMSPTData(null, this.tickRateManager.nanosecondsPerTick());
            }
            return level.fish$msptData5s;
        }
    }

    @Override
    public EntityTickList generateEntityTickList(final ServerLevel level) {
        return new WorldEntityTickList(level);
    }

    @Override
    public ServerWaypointManager generateWaypointManager(final ServerLevel level) {
        return new WorldWaypointManager(level);
    }

    @Override
    public RegionScheduler generateRegionScheduler() {
        return new WorldRegionScheduler();
    }

    @Override
    public void setIgnoreBlockEntityUpdates(final boolean ignore) {
        this.ignoreBlockEntityUpdates.set(ignore);
    }

    @Override
    public boolean getIgnoreBlockEntityUpdates() {
        return this.ignoreBlockEntityUpdates.get();
    }

    @Override
    public void setTreeType(final TreeType type) {
        this.treeType.set(type);
    }

    @Override
    public @Nullable TreeType getTreeType() {
        return this.treeType.get();
    }

    @Override
    public void removeTreeType() {
        this.treeType.remove();
    }

    @Override
    public void setSculkSourceBlockOverride(final BlockPos pos) {
        this.sculkSourceBlockOverride.set(pos);
    }

    @Override
    public @Nullable BlockPos getSculkSourceBlockOverride() {
        return this.sculkSourceBlockOverride.get();
    }

    @Override
    public void removeSculkSourceBlockOverride() {
        this.sculkSourceBlockOverride.remove();
    }

    @Override
    public <T> T submitTryAcquireLock(final ServerLevel level, final Callable<T> callable) {
        if (MinecraftServer.getServer().isDebugging()) {
            this.logAsyncAccess();
        }
        if (level.fish$lock.readLock().tryLock()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level.fish$lock.readLock().unlock();
            }
        } else {
            final CallableWrapper<T> task = new CallableWrapper<>(callable);
            level.fish$worldData.worldScheduler.schedule(task);
            return task.get();
        }
    }

    @Override
    public void executeTryAcquireLock(final ServerLevel level, final Runnable runnable) {
        if (MinecraftServer.getServer().isDebugging()) {
            this.logAsyncAccess();
        }
        if (level.fish$lock.readLock().tryLock()) {
            try {
                runnable.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                level.fish$lock.readLock().unlock();
            }
        } else {
            level.fish$worldData.worldScheduler.schedule(runnable);
        }
    }

    /*
     * Used to schedule tasks after all worlds are done ticking.
     * Useful if an async task involves multiple worlds.
     * Possible use for teams plugins.
     */
    @Override
    public <T> T submitNonAcquireLock(final Callable<T> callable) {
        if (MinecraftServer.getServer().isDebugging()) {
            this.logAsyncAccess();
        }
        final CallableWrapper<T> task = new CallableWrapper<>(callable);
        this.endOfTickTasks.offer(task);
        return task.get();
    }

    /*
     * Used to schedule tasks after all worlds are done ticking.
     * Useful if an async task involves multiple worlds.
     * Possible use for async respawn in practice plugins (those calls shouldn't be async imo, but IDK).
     */
    @Override
    public void executeNonAcquireLock(final Runnable runnable) {
        if (MinecraftServer.getServer().isDebugging()) {
            this.logAsyncAccess();
        }
        this.endOfTickTasks.offer(runnable);
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
    @Override
    public <T> T submitNonAcquireLock(final ServerLevel level, final Callable<T> callable) {
        if (MinecraftServer.getServer().isDebugging()) {
            this.logAsyncAccess();
        }
        final CallableWrapper<T> task = new CallableWrapper<>(callable);
        level.fish$worldData.worldScheduler.schedule(task);
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
    @Override
    public void executeNonAcquireLock(final ServerLevel level, final Runnable runnable) {
        if (MinecraftServer.getServer().isDebugging()) {
            this.logAsyncAccess();
        }
        level.fish$worldData.worldScheduler.schedule(runnable);
    }

    /**
     * Ticks mid-tick tasks for a single world.
     * The world will use its own mid-tick statistics, which means it will only consider task stats for
     *   itself, completely ignoring the execution stats of the main thread.
     * This will alter the behavior of the first task that gets executed, as the rest will use the
     *   updated stats after the first's success/failure.
     * See {@link MinecraftServer#moonrise$executeMidTickTasks()}
     *
     * @param world World to tick mid-tick tasks
     */
    private void executeMidTickTasks(final ServerLevel world) {
        final RegionizedWorldData worldData = world.fish$worldData;
        final long startTime = System.nanoTime();
        if ((startTime - worldData.lastMidTickExecute) <= CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME || (startTime - worldData.lastMidTickExecuteFailure) <= TASK_EXECUTION_FAILURE_BACKOFF) {
            // it's shown to be bad to constantly hit the queue (chunk loads slow to a crawl), even if no tasks are executed.
            // so, backoff to prevent this
            return;
        }

        for (;;) {
            final boolean moreTasks = this.tickMidTickTasks(world);
            final long currTime = System.nanoTime();
            final long diff = currTime - startTime;

            if (!moreTasks || diff >= MAX_CHUNK_EXEC_TIME) {
                if (!moreTasks) {
                    worldData.lastMidTickExecuteFailure = currTime;
                }

                // note: negative values reduce the time
                long overuse = diff - MAX_CHUNK_EXEC_TIME;
                if (overuse >= (10L * 1000L * 1000L)) { // 10ms
                    // make sure something like a GC or dumb plugin doesn't screw us over...
                    overuse = 10L * 1000L * 1000L; // 10ms
                }

                final double overuseCount = (double)overuse/(double)MAX_CHUNK_EXEC_TIME;
                final long extraSleep = Math.round(overuseCount*CHUNK_TASK_QUEUE_BACKOFF_MIN_TIME);

                worldData.lastMidTickExecute = currTime + extraSleep;
                return;
            }
        }
    }

    @Override
    public void executeMidTickTasks() {
        if (Thread.currentThread() instanceof WorldTickThread worldThread) {
            this.executeMidTickTasks(worldThread.getTickingWorld());
        }
    }

}
