package me.biquaternions.fish.async.thread;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;

public class WorldTickThread extends TickThread {

    private final @NonNull ServerLevel tickingWorld;

    public WorldTickThread(Runnable runnable, @NonNull ServerLevel world) {
        super(runnable, String.format("Fish World [%s] Tick Thread", world.serverLevelData.getLevelName()));
        this.tickingWorld = world;
    }

    public @NonNull ServerLevel getTickingWorld() {
        return this.tickingWorld;
    }

}
