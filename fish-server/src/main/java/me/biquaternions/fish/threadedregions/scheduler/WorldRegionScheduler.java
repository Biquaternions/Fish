package me.biquaternions.fish.threadedregions.scheduler;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.server.level.ServerLevel;
import me.biquaternions.fish.async.AsyncWorldTicking;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@NullMarked
@SuppressWarnings("DuplicatedCode")
public final class WorldRegionScheduler implements RegionScheduler {

    private static Runnable wrap(final Plugin plugin, final World world, final int chunkX, final int chunkZ, final Runnable run) {
        return () -> {
            try {
                run.run();
            } catch (final Throwable throwable) {
                plugin.getSLF4JLogger().warn("Location task for {} v{} in world {} at {}, {} generated an exception",
                    plugin.getPluginMeta().getDisplayName(),
                    plugin.getPluginMeta().getVersion(),
                    world, chunkX, chunkZ, throwable);
            }
        };
    }

    @Override
    public void execute(final Plugin plugin, final World world, final int chunkX, final int chunkZ, final Runnable run) {
        Objects.requireNonNull(plugin, "Plugin may not be null");
        Objects.requireNonNull(world, "World may not be null");
        Objects.requireNonNull(run, "Runnable may not be null");

        AsyncWorldTicking.scheduleVoidForEndOfWorldTick(((CraftWorld) world).getHandle(), wrap(plugin, world, chunkX, chunkZ, run));
    }

    @Override
    public ScheduledTask run(final Plugin plugin, final World world, final int chunkX, final int chunkZ, final Consumer<ScheduledTask> task) {
        return this.runDelayed(plugin, world, chunkX, chunkZ, task, 1);
    }

    @Override
    public ScheduledTask runDelayed(final Plugin plugin, final World world, final int chunkX, final int chunkZ,
                                    final Consumer<ScheduledTask> task, final long delayTicks) {
        Objects.requireNonNull(plugin, "Plugin may not be null");
        Objects.requireNonNull(world, "World may not be null");
        org.apache.commons.lang3.Validate.notNull(task, "Task may not be null");
        if (delayTicks <= 0) {
            throw new IllegalArgumentException("Delay ticks may not be <= 0");
        }

        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }

        final LocationScheduledTask ret = new LocationScheduledTask(plugin, world, chunkX, chunkZ, -1, task);
        ((CraftWorld) world).getHandle().fish$scheduler.schedule(ret, delayTicks);

        if (!plugin.isEnabled()) {
            // handle race condition where plugin is disabled asynchronously
            ret.cancel();
        }

        return ret;
    }

    @Override
    public ScheduledTask runAtFixedRate(final Plugin plugin, final World world, final int chunkX, final int chunkZ,
                                        final Consumer<ScheduledTask> task, final long initialDelayTicks, final long periodTicks) {
        Objects.requireNonNull(plugin, "Plugin may not be null");
        Objects.requireNonNull(world, "World may not be null");
        Objects.requireNonNull(task, "Task may not be null");
        if (initialDelayTicks <= 0) {
            throw new IllegalArgumentException("Initial delay ticks may not be <= 0");
        }
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("Period ticks may not be <= 0");
        }

        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register task while disabled");
        }

        final LocationScheduledTask ret = new LocationScheduledTask(plugin, world, chunkX, chunkZ, periodTicks, task);
        ((CraftWorld) world).getHandle().fish$scheduler.schedule(ret, initialDelayTicks);

        if (!plugin.isEnabled()) {
            // handle race condition where plugin is disabled asynchronously
            ret.cancel();
        }

        return ret;
    }

    public void tickWorld(final ServerLevel world) {
        world.fish$scheduler.tick();
    }

    public static final class Scheduler {

        private long tick = 0;

        private final MultiThreadedQueue<Runnable> tasks = new MultiThreadedQueue<>();
        private final ConcurrentMap<Long, MultiThreadedQueue<Runnable>> delayedTasks = new ConcurrentHashMap<>();

        public void tick() {
            ++this.tick;
            this.handleTasks();
            this.handleDelayedTasks();
        }

        public boolean schedule(final Runnable task) {
            return this.tasks.offer(task);
        }

        public boolean schedule(final Runnable task, final long delay) {
            return this.delayedTasks.computeIfAbsent(this.tick + delay, _ -> new MultiThreadedQueue<>()).offer(task);
        }

        private void handleTasks() {
            Runnable task;
            while ((task = this.tasks.poll()) != null) {
                task.run();
            }
        }

        public void handleDelayedTasks() {
            MultiThreadedQueue<Runnable> tasks = this.delayedTasks.remove(this.tick);
            if (tasks == null) {
                return;
            }

            Runnable task;
            while ((task = tasks.poll()) != null) {
                task.run();
            }
        }

    }

    private static final class LocationScheduledTask implements ScheduledTask, Runnable {

        private static final int STATE_IDLE                = 0;
        private static final int STATE_EXECUTING           = 1;
        private static final int STATE_EXECUTING_CANCELLED = 2;
        private static final int STATE_FINISHED            = 3;
        private static final int STATE_CANCELLED           = 4;

        private final Plugin plugin;
        private final int chunkX;
        private final int chunkZ;
        private final long repeatDelay; // in ticks
        private final World world;
        private @Nullable Consumer<ScheduledTask> run;

        @SuppressWarnings("unused")
        private volatile int state;
        private static final VarHandle STATE_HANDLE = ConcurrentUtil.getVarHandle(LocationScheduledTask.class, "state", int.class);

        private LocationScheduledTask(final Plugin plugin, final World world, final int chunkX, final int chunkZ,
                                      final long repeatDelay, final Consumer<ScheduledTask> run) {
            this.plugin = plugin;
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.repeatDelay = repeatDelay;
            this.run = run;
        }

        private int getStateVolatile() {
            return (int)STATE_HANDLE.get(this);
        }

        private int compareAndExchangeStateVolatile(final int expect, final int update) {
            return (int)STATE_HANDLE.compareAndExchange(this, expect, update);
        }

        private void setStateVolatile(final int value) {
            STATE_HANDLE.setVolatile(this, value);
        }

        @Override
        public void run() {
            if (!this.plugin.isEnabled()) {
                // don't execute if the plugin is disabled
                return;
            }

            final boolean repeating = this.isRepeatingTask();
            if (STATE_IDLE != this.compareAndExchangeStateVolatile(STATE_IDLE, STATE_EXECUTING)) {
                // cancelled
                return;
            }

            try {
                this.run.accept(this);
            } catch (final Throwable throwable) {
                this.plugin.getSLF4JLogger().warn("Location task for {} v{} in world {} at {}, {} generated an exception",
                    this.plugin.getPluginMeta().getName(), this.plugin.getPluginMeta().getVersion(),
                    world, chunkX, chunkZ, throwable
                );
            } finally {
                boolean reschedule = false;
                if (!repeating) {
                    this.setStateVolatile(STATE_FINISHED);
                } else if (!this.plugin.isEnabled()) {
                    this.setStateVolatile(STATE_CANCELLED);
                } else if (STATE_EXECUTING == this.compareAndExchangeStateVolatile(STATE_EXECUTING, STATE_IDLE)) {
                    reschedule = true;
                } // else: cancelled repeating task

                if (!reschedule) {
                    this.run = null;
                } else {
                    ((CraftWorld) this.world).getHandle().fish$scheduler.schedule(this, this.repeatDelay);
                }
            }
        }

        @Override
        public Plugin getOwningPlugin() {
            return this.plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return this.repeatDelay > 0;
        }

        @Override
        public CancelledState cancel() {
            for (int curr = this.getStateVolatile();;) {
                switch (curr) {
                    case STATE_IDLE: {
                        if (STATE_IDLE == (curr = this.compareAndExchangeStateVolatile(STATE_IDLE, STATE_CANCELLED))) {
                            this.state = STATE_CANCELLED;
                            this.run = null;
                            return CancelledState.CANCELLED_BY_CALLER;
                        }
                        // try again
                        continue;
                    }
                    case STATE_EXECUTING: {
                        if (!this.isRepeatingTask()) {
                            return CancelledState.RUNNING;
                        }
                        if (STATE_EXECUTING == (curr = this.compareAndExchangeStateVolatile(STATE_EXECUTING, STATE_EXECUTING_CANCELLED))) {
                            return CancelledState.NEXT_RUNS_CANCELLED;
                        }
                        // try again
                        continue;
                    }
                    case STATE_EXECUTING_CANCELLED: {
                        return CancelledState.NEXT_RUNS_CANCELLED_ALREADY;
                    }
                    case STATE_FINISHED: {
                        return CancelledState.ALREADY_EXECUTED;
                    }
                    case STATE_CANCELLED: {
                        return CancelledState.CANCELLED_ALREADY;
                    }
                    default: {
                        throw new IllegalStateException("Unknown state: " + curr);
                    }
                }
            }
        }

        @Override
        public ExecutionState getExecutionState() {
            final int state = this.getStateVolatile();
            return switch (state) {
                case STATE_IDLE -> ExecutionState.IDLE;
                case STATE_EXECUTING -> ExecutionState.RUNNING;
                case STATE_EXECUTING_CANCELLED -> ExecutionState.CANCELLED_RUNNING;
                case STATE_FINISHED -> ExecutionState.FINISHED;
                case STATE_CANCELLED -> ExecutionState.CANCELLED;
                default -> throw new IllegalStateException("Unknown state: " + state);
            };
        }
    }

}
