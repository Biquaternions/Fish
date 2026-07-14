package me.biquaternions.fish.threadedregions;

import me.biquaternions.fish.async.thread.WorldTickThread;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TickWorldScheduler {

    public static RegionizedWorldData getCurrentRegionizedWorldData() {
        final Thread currentThread = Thread.currentThread();
        if (!(currentThread instanceof WorldTickThread tickThreadRunner)) {
            throw new IllegalStateException("Thread " + currentThread.getName() + " attempted to retrieve world data");
        }
        return tickThreadRunner.getWorldData();
    }

}
