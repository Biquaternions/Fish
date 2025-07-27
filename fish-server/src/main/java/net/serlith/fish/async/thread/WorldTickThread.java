package net.serlith.fish.async.thread;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WorldTickThread extends TickThread {

    private @Nullable ServerLevel tickingWorld = null;

    public WorldTickThread(String name) {
        super(name);
    }

    public WorldTickThread(Runnable r, String name) {
        super(r, name);
    }

    public void setTickingWorld(@NotNull ServerLevel tickingWorld) {
        this.tickingWorld = tickingWorld;
    }

    public @Nullable ServerLevel getTickingWorld() {
        return tickingWorld;
    }

}
