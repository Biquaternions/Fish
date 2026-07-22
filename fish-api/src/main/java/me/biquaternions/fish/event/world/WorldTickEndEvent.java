package me.biquaternions.fish.event.world;

import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.world.WorldEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class WorldTickEndEvent extends WorldEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final double tickDuration;
    private final long timeEnd;

    @ApiStatus.Internal
    public WorldTickEndEvent(final World world, final double tickDuration, final long timeRemaining) {
        super(world, false);
        this.tickDuration = tickDuration;
        this.timeEnd = System.nanoTime() + timeRemaining;
    }

    /**
     * @return Time in milliseconds of how long this tick took
     */
    public double getTickDuration() {
        return this.tickDuration;
    }

    /**
     * Amount of nanoseconds remaining before the next tick should start.
     * <p>
     * If this value is negative, then that means the server has exceeded the tick time limit and TPS has been lost.
     * <p>
     * Method will continuously return the updated time remaining value. (return value is not static)
     *
     * @return Amount of nanoseconds remaining before the next tick should start
     */
    public long getTimeRemaining() {
        return this.timeEnd - System.nanoTime();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

}
