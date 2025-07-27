package net.serlith.fish.async.thread;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.ThreadFactory;

public class WorldExecutorThreadFactory implements ThreadFactory {

    private final String worldName;
    private final Logger logger;

    public WorldExecutorThreadFactory(String worldName) {
        this.worldName = worldName;
        this.logger = LogManager.getLogger(String.format("World %s", worldName));
    }

    @Override
    public Thread newThread(@NotNull final Runnable r) {
        Thread tickThread = new WorldTickThread(r, String.format("Fish Level [%s] Tick Thread", worldName));
        tickThread.setDaemon(false);
        tickThread.setPriority(Thread.NORM_PRIORITY);
        tickThread.setUncaughtExceptionHandler((t, e) -> this.logger.fatal("An exception was thrown while ticking {}", t.getName(), e));
        return tickThread;
    }

}
