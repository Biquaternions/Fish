package me.biquaternions.fish.threadedregions.executor;

import ca.spottedleaf.common.time.TickData;
import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.redstone.RedstoneWireTurbo;
import io.papermc.paper.threadedregions.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.FallbackRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import me.biquaternions.fish.threadedregions.TickWorldExecutor;
import me.biquaternions.fish.util.CallableWrapper;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.AABB;
import org.bukkit.TreeType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

@NullMarked
public class SynchronousTickWorldExecutor implements TickWorldExecutor {

    private final MinecraftServer server;

    private final Queue<Runnable> endOfTickTasks = new ConcurrentLinkedQueue<>();
    private @Nullable TreeType treeType = null;
    private @Nullable BlockPos sculkSourceBlockOverride = null;
    private boolean ignoreBlockEntityUpdates = false;

    private final RedstoneWireTurbo redstoneTurbo = new RedstoneWireTurbo((RedstoneWireBlock) Blocks.REDSTONE_WIRE);
    private boolean redstoneShouldSignal = false;

    public SynchronousTickWorldExecutor(final MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void tickWorlds(final Iterable<ServerLevel> worlds, final BooleanSupplier hasTimeLeft) {
        ProfilerFiller profiler = Profiler.get();
        for (ServerLevel level : worlds) {
            level.hasPhysicsEvent = org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - BlockPhysicsEvent
            level.hasEntityMoveEvent = io.papermc.paper.event.entity.EntityMoveEvent.getHandlerList().getRegisteredListeners().length > 0; // Paper - Add EntityMoveEvent
            level.updateLagCompensationTick(); // Paper - lag compensation
            this.server.fish$globalData.skipHopperEvents = level.paperConfig().hopper.disableMoveEvent || org.bukkit.event.inventory.InventoryMoveItemEvent.getHandlerList().getRegisteredListeners().length == 0; // Paper - Perf: Optimize Hoppers // Fish - Handle Hopper Events Optimizations
            profiler.push(() -> level + " " + level.dimension().identifier());
            profiler.push("tick");

            try {
                level.tick(hasTimeLeft);
            } catch (Throwable t) {
                CrashReport report = CrashReport.forThrowable(t, "Exception ticking world");
                level.fillReportDetails(report);
                throw new ReportedException(report);
            }

            profiler.pop();
            profiler.pop();
            level.explosionDensityCache.clear(); // Paper - Optimize explosions
        }
        this.handleScheduledTasks();
    }

    @Override
    public boolean isConcurrentExecutor() {
        return false;
    }

    private void handleScheduledTasks() {
        Runnable task;
        while ((task = this.endOfTickTasks.poll()) != null) {
            task.run();
        }
    }

    @Override
    public boolean shouldScheduleExecution() {
        return false;
    }

    @Override
    public boolean shouldScheduleExecution(final Level level) {
        return false;
    }

    @Override
    public boolean shouldScheduleExecution(final Entity entity) {
        return false;
    }

    @Override
    public void preferOnlyTickThread(final String reason) {
    }

    @Override
    public void preferTickThreadOrAsyncThread(final Level level, final String reason) {
    }

    @Override
    public void preferTickThread(final Level level, final String reason) {
    }

    @Override
    public void preferTickThread(final Level world, final BlockPos pos, final String reason) {
    }

    @Override
    public void preferTickThread(final Level world, final AABB aabb, final String reason) {
    }

    @Override
    public void preferTickThread(final Level world, final int x, final int z, final String reason) {
    }

    @Override
    public void ensureTickThread(final Level level, final String reason) {
        TickThread.ensureTickThread(level, reason);
    }

    @Override
    public void ensureTickThread(final Level level, final int x, final int z, final String reason) {
        TickThread.ensureTickThread(level, x, z, reason);
    }

    @Override
    public <T extends PacketListener> void ensureRunningOnSameThread(final Packet<T> packet, final T listener, final ServerLevel level) throws RunningOnDifferentThreadException {
        PacketUtils.ensureRunningOnSameThread(packet, listener, level.getServer().packetProcessor());
    }

    @Override
    public TickData.@Nullable MSPTData getMSPTData5s(final ServerLevel level) {
        return this.server.getMSPTData5s();
    }

    @Override
    public EntityScheduler.EntitySchedulerTickList getEntitySchedulerTickList(final Level world) {
        return MinecraftServer.getServer().entitySchedulerTickList;
    }

    @Override
    public EntityTickList generateEntityTickList(final ServerLevel level) {
        return new EntityTickList();
    }

    @Override
    public ServerWaypointManager generateWaypointManager(final ServerLevel level) {
        return new ServerWaypointManager(level);
    }

    @Override
    public @Nullable ExecutorService generateWorldExecutorService(final ServerLevel world) {
        return null;
    }

    @Override
    public RegionScheduler generateRegionScheduler() {
        return new FallbackRegionScheduler();
    }

    @Override
    public void setIgnoreBlockEntityUpdates(final boolean ignore) {
        this.ignoreBlockEntityUpdates = ignore;
    }

    @Override
    public boolean getIgnoreBlockEntityUpdates() {
        return this.ignoreBlockEntityUpdates;
    }

    @Override
    public void setRedstoneShouldSignal(final boolean signal) {
        this.redstoneShouldSignal = signal;
    }

    @Override
    public boolean getRedstoneShouldSignal() {
        return this.redstoneShouldSignal;
    }

    @Override
    public RedstoneWireTurbo getRedstoneWireTurbo() {
        return this.redstoneTurbo;
    }

    @Override
    public void setTreeType(final TreeType type) {
        this.treeType = type;
    }

    @Override
    public @Nullable TreeType getTreeType() {
        return this.treeType;
    }

    @Override
    public void removeTreeType() {
        this.treeType = null;
    }

    @Override
    public void setSculkSourceBlockOverride(final BlockPos pos) {
        this.sculkSourceBlockOverride = pos;
    }

    @Override
    public @Nullable BlockPos getSculkSourceBlockOverride() {
        return this.sculkSourceBlockOverride;
    }

    @Override
    public void removeSculkSourceBlockOverride() {
        this.sculkSourceBlockOverride = null;
    }

    @Override
    public <T> T submitTryAcquireLock(final ServerLevel level, final Callable<T> callable) {
        final CallableWrapper<T> task = new CallableWrapper<>(callable);
        this.endOfTickTasks.offer(task);
        return task.get();
    }

    @Override
    public void executeTryAcquireLock(final ServerLevel level, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T submitNonAcquireLock(final Callable<T> callable) {
        final CallableWrapper<T> task = new CallableWrapper<>(callable);
        this.endOfTickTasks.offer(task);
        return task.get();
    }

    @Override
    public void executeNonAcquireLock(final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T submitNonAcquireLock(final ServerLevel level, final Callable<T> callable) {
        final CallableWrapper<T> task = new CallableWrapper<>(callable);
        this.endOfTickTasks.offer(task);
        return task.get();
    }

    @Override
    public void executeNonAcquireLock(final ServerLevel level, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void executeMidTickTasks() {
        this.server.fish$executeMidTickTasks();
    }

}
