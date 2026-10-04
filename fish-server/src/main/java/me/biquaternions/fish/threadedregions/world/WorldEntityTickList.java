package me.biquaternions.fish.threadedregions.world;

import ca.spottedleaf.moonrise.common.util.TickThread;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.jspecify.annotations.NullMarked;
import java.util.function.Consumer;

@NullMarked
public class WorldEntityTickList extends EntityTickList {

    private final ServerLevel level;

    public WorldEntityTickList(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void add(Entity entity) {
        TickThread.ensureTickThread(entity, "Asynchronous entity ticklist addition"); // Paper // SparklyPaper - parallel world ticking (additional concurrency issues logs)
        super.add(entity);
    }

    @Override
    public void remove(Entity entity) {
        TickThread.ensureTickThread(entity, "Asynchronous entity ticklist addition"); // Paper // SparklyPaper - parallel world ticking (additional concurrency issues logs)
        super.remove(entity);
    }

    @Override
    public void forEach(Consumer<Entity> entity) {
        TickThread.ensureTickThread(this.level, "Asynchronous entity ticklist iteration"); // SparklyPaper - parallel world ticking (additional concurrency issues logs)
        super.forEach(entity);
    }

}
