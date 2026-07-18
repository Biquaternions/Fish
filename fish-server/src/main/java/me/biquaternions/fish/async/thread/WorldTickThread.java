package me.biquaternions.fish.async.thread;

import ca.spottedleaf.moonrise.common.util.TickThread;
import me.biquaternions.fish.threadedregions.RegionizedWorldData;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class WorldTickThread extends TickThread {

    private final ServerLevel tickingWorld;

    public WorldTickThread(Runnable runnable, ServerLevel world) {
        super(runnable, String.format("Fish World [%s] Tick Thread", world.serverLevelData.getLevelName()));
        this.tickingWorld = world;
    }

    public ServerLevel getTickingWorld() {
        return this.tickingWorld;
    }

    public RegionizedWorldData getWorldData() {
        return this.tickingWorld.fish$worldData;
    }

    public static boolean isWorldTickThread() {
        return Thread.currentThread() instanceof WorldTickThread;
    }

}
