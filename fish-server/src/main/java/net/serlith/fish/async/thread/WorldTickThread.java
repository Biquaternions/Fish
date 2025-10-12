package net.serlith.fish.async.thread;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class WorldTickThread extends TickThread {

    private final @NotNull ServerLevel tickingWorld;

    public WorldTickThread(Runnable runnable, @NotNull ServerLevel world) {
        super(runnable, String.format("Fish World [%s] Tick Thread", world.serverLevelData.getLevelName()));
        this.tickingWorld = world;
    }

    public @NotNull ServerLevel getTickingWorld() {
        return this.tickingWorld;
    }

}
