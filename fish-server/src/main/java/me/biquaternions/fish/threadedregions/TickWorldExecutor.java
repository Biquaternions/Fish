package me.biquaternions.fish.threadedregions;

import ca.spottedleaf.common.time.TickData;
import io.papermc.paper.redstone.RedstoneWireTurbo;
import io.papermc.paper.threadedregions.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.phys.AABB;
import org.bukkit.TreeType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

@NullMarked
public interface TickWorldExecutor {

    /**
     * Tick worlds.
     *
     * @param worlds worlds
     * @param hasTimeLeft time left supplier
     */
    void tickWorlds(final Iterable<ServerLevel> worlds, final BooleanSupplier hasTimeLeft);

    /**
     * Is the scheduler synchronous
     *
     * @return synchronous
     */
    boolean isConcurrentExecutor();

    /**
     * Does the current thread require scheduling execution.
     *
     * @return value
     */
    boolean shouldScheduleExecution();

    /**
     * Does the current thread require scheduling execution.
     *
     * @param level world
     *
     * @return value
     */
    boolean shouldScheduleExecution(final Level level);

    /**
     * Does the current thread require scheduling execution.
     *
     * @param entity entity
     *
     * @return value
     */
    boolean shouldScheduleExecution(final Entity entity);

    /**
     * Implementation-dependant thread check, it might throw on non-main thread access.
     *
     * @param reason reason
     */
    void preferOnlyTickThread(final String reason);

    /**
     * Implementation-dependant thread check, it might throw on non-tick or non-foreign thread accesses.
     *
     * @param world world
     * @param reason reason
     */
    void preferTickThreadOrAsyncThread(final Level world, final String reason);

    /**
     * Implementation-dependant thread check, it might throw on non-owning thread accesses.
     *
     * @param world world
     * @param reason reason
     */
    void preferTickThread(final Level world, final String reason);

    /**
     * Implementation-dependant thread check, it might throw on non-owning thread accesses.
     *
     * @param world world
     * @param pos block position
     * @param reason reason
     */
    void preferTickThread(final Level world, final BlockPos pos, final String reason);

    /**
     * Implementation-dependant thread check, it might throw on non-owning thread accesses.
     *
     * @param world world
     * @param aabb bounding box
     * @param reason reason
     */
    void preferTickThread(final Level world, final AABB aabb, final String reason);

    /**
     * Implementation-dependant thread check, it might throw on non-owning thread accesses.
     *
     * @param world world
     * @param x chunk X coordinate
     * @param z chunk Z coordinate
     * @param reason reason
     */
    void preferTickThread(final Level world, final int x, final int z, final String reason);

    /**
     * Thread check, it will throw on non-owning thread accesses.
     *
     * @param world world
     * @param reason reason
     */
    void ensureTickThread(final Level world, final String reason);

    /**
     * Thread check, it will throw on non-owning thread accesses.
     *
     * @param world world
     * @param x chunk X coordinate
     * @param z chunk Z coordinate
     * @param reason reason
     */
    void ensureTickThread(final Level world, final int x, final int z, final String reason);

    /**
     * Thread check, it will throw on non-owning thread accesses.
     *
     * @param packet packet
     * @param listener packet listener
     * @param world world
     * @param <T> packet listener type
     *
     * @throws RunningOnDifferentThreadException thrown if running on a different thread
     */
    <T extends PacketListener> void ensureRunningOnSameThread(final Packet<T> packet, final T listener, final ServerLevel world) throws RunningOnDifferentThreadException;

    /**
     * World tick MSPT data.
     *
     * @param world world
     *
     * @return mspt data
     */
    TickData.@Nullable MSPTData getMSPTData5s(final ServerLevel world);

    /**
     * Entity scheduler tick list
     *
     * @param world world
     *
     * @return scheduler
     */
    EntityScheduler.EntitySchedulerTickList getEntitySchedulerTickList(final Level world);

    /**
     * Generates a new entity tick list.
     *
     * @param world world
     *
     * @return entity tick list
     */
    EntityTickList generateEntityTickList(final ServerLevel world);

    /**
     * Generates a new waypoint manager.
     *
     * @param world world
     *
     * @return waypoint manager
     */
    ServerWaypointManager generateWaypointManager(final ServerLevel world);

    /**
     * Generates a new world executor service
     *
     * @return executor service
     */
    @Nullable ExecutorService generateWorldExecutorService(final ServerLevel world);

    /**
     * Generates a new region scheduler
     *
     * @return region scheduler
     */
    RegionScheduler generateRegionScheduler();

    /**
     * Sets ignore block entity updates flag.
     *
     * @param ignore should ignore
     */
    void setIgnoreBlockEntityUpdates(final boolean ignore);

    /**
     * Gets ignore block entity updates flag.
     *
     * @return should ignore
     */
    boolean getIgnoreBlockEntityUpdates();

    /**
     * Sets if redstone should signal
     *
     * @param signal signal
     */
    void setRedstoneShouldSignal(final boolean signal);

    /**
     * Gets if redstone should signal
     *
     * @return signal
     */
    boolean getRedstoneShouldSignal();

    /**
     * Gets redstone wire turbo
     *
     * @return turbo
     */
    RedstoneWireTurbo getRedstoneWireTurbo();

    /**
     * Sets tree type.
     *
     * @param type tree type
     */
    void setTreeType(final TreeType type);

    /**
     * Gets tree type.
     *
     * @return tree type
     */
    @Nullable TreeType getTreeType();

    /**
     * Remove tree type.
     */
    void removeTreeType();

    /**
     * Sets sculk source block override.
     *
     * @param pos source block
     */
    void setSculkSourceBlockOverride(final BlockPos pos);

    /**
     * Gets sculk source block override.
     *
     * @return source block
     */
    @Nullable BlockPos getSculkSourceBlockOverride();

    /**
     * Remove sculk source block override.
     */
    void removeSculkSourceBlockOverride();

    /**
     * Schedules a returning operation.
     * <br>
     * This method competes with the world lock to execute this task inside a world thread.
     * When it wins, the task will be executed at the end of the world tick.
     * When it loses, the task will be executed after all worlds are done ticking.
     * <br>
     * The calling thread will sleep until the task is executed.
     *
     * @param world world
     * @param callable task
     *
     * @return result
     *
     * @param <T> returnable type
     */
    <T> T submitTryAcquireLock(final ServerLevel world, final Callable<T> callable);

    /**
     * Schedules an operation.
     * <br>
     * This method competes with the world lock to execute this task inside a world thread.
     * When it wins, the task will be executed at the end of the world tick.
     * When it loses, the task will be executed after all worlds are done ticking.
     * <br>
     * The calling thread will not sleep.
     *
     * @param world world
     * @param runnable task
     */
    void executeTryAcquireLock(final ServerLevel world, final Runnable runnable);

    /**
     * Schedules a returning operation.
     * <br>
     * This method does not compete for the world lock, it will directly schedule the task
     * to be executed after all worlds are done ticking.
     * <br>
     * The calling thread will sleep until the task is executed.
     *
     * @param callable task
     *
     * @return result
     *
     * @param <T> returnable type
     */
    <T> T submitNonAcquireLock(final Callable<T> callable);

    /**
     * Schedules an operation.
     * <br>
     * This method does not compete for the world lock, it will directly schedule the task
     * to be executed after all worlds are done ticking.
     * <br>
     * The calling thread will not sleep.
     *
     * @param runnable task
     */
    void executeNonAcquireLock(final Runnable runnable);

    /**
     * Schedules a returning operation.
     * <br>
     * This method does not compete for the world lock, it will directly schedule the task
     * to be executed after all worlds are done ticking.
     * <br>
     * The calling thread will sleep until the task is executed.
     *
     * @param world world
     * @param callable task
     *
     * @return result
     *
     * @param <T> returnable type
     */
    <T> T submitNonAcquireLock(final ServerLevel world, final Callable<T> callable);

    /**
     * Schedules an operation.
     * <br>
     * This method does not compete for the world lock, it will directly schedule the task
     * to be executed after all worlds are done ticking.
     * <br>
     * The calling thread will not sleep.
     *
     * @param world world
     * @param runnable task
     */
    void executeNonAcquireLock(final ServerLevel world, final Runnable runnable);

    /**
     * Executes mid-tick tasks.
     */
    void executeMidTickTasks();

}
