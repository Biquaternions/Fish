package me.biquaternions.fish.threadedregions;

import me.biquaternions.fish.concurrent.thread.WorldTickThread;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TickWorldScheduler {

    public static AbstractWorldData getCurrentRegionizedWorldData() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof WorldTickThread tickThreadRunner) {
            return tickThreadRunner.getWorldData();
        }
        return MinecraftServer.getServer().fish$globalData;
    }

}
