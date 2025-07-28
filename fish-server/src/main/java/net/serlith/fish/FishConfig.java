package net.serlith.fish;

import net.j4c0b3y.api.config.ConfigHandler;
import net.j4c0b3y.api.config.StaticConfig;
import java.io.File;

@StaticConfig.Header({
    "",
    "Fish Configuration \uD83D\uDC1F",
    "This project is a meme/playground, here be dragons~",
    "",
    "Configurations marked with:",
    " \uD83D\uDD25 Support hot reload with /fish reload",
    " \uD83D\uDD03 Require a server restart to apply",
    " ⚠️ Preferably use a clean new world unless you know how to properly adapt your current one",
    ""
})
public class FishConfig extends StaticConfig {

    @Ignore
    public static final ConfigHandler HANDLER = new ConfigHandler();

    @Ignore
    public static FishConfig INSTANCE;

    public FishConfig() {
        super(new File("fish.yml"), HANDLER);
        INSTANCE = this;
    }

    @Order(1)
    @SuppressWarnings("unused")
    public static class INFO {
        public static String VERSION = "1.0";
    }

    @Order(2)
    @Comment("Async features")
    public static class ASYNC {

        @Comment({
            "\uD83D\uDD03 Ticks each world/level in a separate thread",
            "Regardless of the name given here, it's actually Parallel World Ticking rather than fully async",
            "Worlds do not tick independently, they have to wait until all worlds are done with the current tick to continue"
        })
        public static class WORLD_TICKING {
            public static boolean ENABLED = true;
            @Ignore
            public static boolean _ENABLED = true;

            @Comment({
                "Each world will have its own thread, which means 20 worlds = 20 threads",
                "This option refers to how many worlds (threads) can work at the same time, to not stress the CPU",
                "If this value is lower than the amount of worlds, the remaining worlds will wait until others are done"
            })
            public static int THREADS = 3;
            @Ignore
            public static int _THREADS = 3;

        }
    }

    @Override
    public void load() {
        super.load();
        if (initialized) init();
        else {
            ASYNC.WORLD_TICKING._ENABLED = ASYNC.WORLD_TICKING.ENABLED;

            ASYNC.WORLD_TICKING._THREADS = Math.max(1, ASYNC.WORLD_TICKING.THREADS);
        }
    }

    @Ignore
    private static boolean initialized = false;
    public static void init() {
        initialized = true;
    }

}
