package me.biquaternions.fish.flare.collectors;

import co.technove.flare.live.CollectorData;
import co.technove.flare.live.LiveCollector;
import co.technove.flare.live.formatter.SuffixFormatter;
import gg.pufferfish.pufferfish.flare.CustomCategories;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.World;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorldTpsCollector extends LiveCollector {

    private final Map<Key, CollectorData> msptData;

    private WorldTpsCollector(Map<Key, CollectorData> msptData) {
        super(msptData.values().toArray(CollectorData[]::new));
        this.msptData = msptData;
        this.interval = Duration.ofSeconds(5);
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            final CollectorData data = msptData.get(world.key());
            if (data == null) {
                continue;
            }

            double mspt = Math.max(0.0, world.getAverageTickTime());
            this.report(data, Math.round(mspt * 100d) / 100d);
        }
    }

    public static WorldTpsCollector create() {
        List<World> allWorlds = Bukkit.getWorlds();
        allWorlds.sort((a, b) -> (int) Math.round(b.getAverageTickTime() - a.getAverageTickTime()));

        List<World> worlds = allWorlds.subList(0, Math.min(10, allWorlds.size()));
        Map<Key, CollectorData> msptData = new HashMap<>();
        for (World world : worlds) {
            Key key = world.key();
            String msptId = String.format("flare:perf:world[%s]:mspt", key.asString().replace(":", "$"));
            msptData.put(key, new CollectorData(
                msptId,
                "MSPT",
                "Milliseconds per tick, which can show how well this world is performing. This value should always be under 50mspt.",
                SuffixFormatter.of("mspt"), CustomCategories.MC_PERF)
            );
        }

        return new WorldTpsCollector(msptData);
    }

}
