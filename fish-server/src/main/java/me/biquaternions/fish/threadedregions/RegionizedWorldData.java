package me.biquaternions.fish.threadedregions;

import io.papermc.paper.threadedregions.EntityScheduler;
import me.biquaternions.fish.threadedregions.scheduler.WorldRegionScheduler;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RegionizedWorldData extends AbstractWorldData {

    public final EntityScheduler.EntitySchedulerTickList entitySchedulerTickList;
    public final WorldRegionScheduler.Scheduler worldScheduler;

    public long lastMidTickExecute;
    public long lastMidTickExecuteFailure;

    public RegionizedWorldData() {
        this.entitySchedulerTickList = new EntityScheduler.EntitySchedulerTickList();
        this.worldScheduler = new WorldRegionScheduler.Scheduler();
    }

}
