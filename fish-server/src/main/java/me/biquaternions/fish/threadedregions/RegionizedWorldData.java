package me.biquaternions.fish.threadedregions;

import alternate.current.wire.WireHandler;
import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.moonrise.common.util.TickThread;
import io.papermc.paper.redstone.RedstoneWireTurbo;
import io.papermc.paper.threadedregions.EntityScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RegionizedWorldData {

    public final ServerLevel world;
    public final WireHandler wireHandler;
    public final RedstoneWireTurbo turbo;
    public final EntityScheduler.EntitySchedulerTickList entitySchedulerTickList;
    public final PrioritisedTaskQueue taskQueue;

    public long lastMidTickExecute;
    public long lastMidTickExecuteFailure;
    public boolean shouldSignal = true;

    public RegionizedWorldData(final ServerLevel world) {
        this.world = world;
        this.wireHandler = new WireHandler(world);
        this.turbo = new RedstoneWireTurbo((RedStoneWireBlock) Blocks.REDSTONE_WIRE);
        this.entitySchedulerTickList = new EntityScheduler.EntitySchedulerTickList();
        this.taskQueue = new PrioritisedTaskQueue();
    }

    public boolean executeWorldThreadTask() {
        TickThread.ensureTickThread(this.world, "Cannot execute main thread task off-main");
        return this.taskQueue.executeTask();
    }

}
