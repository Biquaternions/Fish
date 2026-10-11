package me.biquaternions.fish.concurrent;

import net.minecraft.server.level.ServerLevel;
import me.biquaternions.fish.concurrent.thread.WorldTickThread;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.NullMarked;
import java.util.concurrent.ThreadFactory;

@NullMarked
public class WorldExecutorThreadFactory implements ThreadFactory {

    private final Logger logger;
    private final ServerLevel world;

    public WorldExecutorThreadFactory(ServerLevel world) {
        this.world = world;
        this.logger = LogManager.getLogger(String.format("World (%s)", this.world.dimension().identifier()));
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        Thread thread = new WorldTickThread(runnable, this.world);
        thread.setDaemon(false);
        thread.setPriority(Thread.NORM_PRIORITY + 2);
        thread.setUncaughtExceptionHandler((t, e) -> this.logger.fatal("An exception was thrown while ticking {}", t.getName(), e));
        return thread;
    }

}
