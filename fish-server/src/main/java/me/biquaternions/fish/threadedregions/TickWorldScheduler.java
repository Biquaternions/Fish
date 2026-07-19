package me.biquaternions.fish.threadedregions;

import me.biquaternions.fish.async.thread.WorldTickThread;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TickWorldScheduler {

    public static GlobalServerData getCurrentRegionizedWorldData() {
        final Thread currentThread = Thread.currentThread();
        if (!(currentThread instanceof WorldTickThread tickThreadRunner)) {
            return MinecraftServer.getServer().fish$globalData;
        }
        return tickThreadRunner.getWorldData();
    }

}
