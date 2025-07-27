package net.serlith.fish.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.jetbrains.annotations.NotNull;
import java.util.function.Consumer;

public class WorldEntityTickList extends EntityTickList {

    private final ServerLevel level;

    public WorldEntityTickList(ServerLevel level) {
        this.level = level;
    }

    @Override
    public void add(@NotNull Entity entity) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(entity, "Asynchronous entity ticklist addition"); // Paper // SparklyPaper - parallel world ticking (additional concurrency issues logs)
        super.add(entity);
    }

    @Override
    public void remove(@NotNull Entity entity) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(entity, "Asynchronous entity ticklist addition"); // Paper // SparklyPaper - parallel world ticking (additional concurrency issues logs)
        super.remove(entity);
    }

    @Override
    public void forEach(@NotNull Consumer<Entity> entity) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(this.level, "Asynchronous entity ticklist iteration"); // SparklyPaper - parallel world ticking (additional concurrency issues logs)
        super.forEach(entity);
    }

}
