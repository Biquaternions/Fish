package me.biquaternions.fish.threadedregions;

import ca.spottedleaf.common.time.TickData;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

@NullMarked
public interface TickWorldExecutor {

    void tickWorlds(final Iterable<ServerLevel> worlds, final BooleanSupplier hasTimeLeft);
    boolean shouldScheduleExecution();
    boolean shouldScheduleExecution(final Level level);
    void ensureOnlyTickThread(final String reason);
    void ensureTickThreadOrAsyncThread(final Level level, final String reason);
    void ensureTickThread(final Level level, final String reason);
    <T extends PacketListener> void ensureRunningOnSameThread(final Packet<T> packet, final T listener, final ServerLevel level) throws RunningOnDifferentThreadException;
    TickData.@Nullable MSPTData getMSPTData5s(final ServerLevel level);
    <T> T submitTryAcquireLock(final ServerLevel level, final Callable<T> callable);
    void executeTryAcquireLock(final ServerLevel level, final Runnable runnable);
    <T> T submitNonAcquireLock(final Callable<T> callable);
    void executeNonAcquireLock(final Runnable runnable);
    <T> T submitNonAcquireLock(final ServerLevel level, final Callable<T> callable);
    void executeNonAcquireLock(final ServerLevel level, final Runnable runnable);
    void executeMidTickTasks();

}
