package me.biquaternions.fish.threadedregions;

import alternate.current.wire.WireHandler;
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

    public long lastMidTickExecute;
    public long lastMidTickExecuteFailure;
    public boolean shouldSignal = true;

    public RegionizedWorldData(final ServerLevel world) {
        this.world = world;
        this.wireHandler = new WireHandler(world);
        this.turbo = new RedstoneWireTurbo((RedStoneWireBlock) Blocks.REDSTONE_WIRE);
        this.entitySchedulerTickList = new EntityScheduler.EntitySchedulerTickList();
    }

}
