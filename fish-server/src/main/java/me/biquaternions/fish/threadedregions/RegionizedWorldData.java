package me.biquaternions.fish.threadedregions;

import io.papermc.paper.threadedregions.EntityScheduler;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RegionizedWorldData extends AbstractWorldData {

    public final ServerLevel world;
    public final EntityScheduler.EntitySchedulerTickList entitySchedulerTickList;

    public long lastMidTickExecute;
    public long lastMidTickExecuteFailure;

    public RegionizedWorldData(final ServerLevel world) {
        this.world = world;
        this.entitySchedulerTickList = new EntityScheduler.EntitySchedulerTickList();
    }

}
