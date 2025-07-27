package net.serlith.fish;

import net.j4c0b3y.api.config.ConfigHandler;
import net.j4c0b3y.api.config.StaticConfig;
import java.io.File;

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
            "Ticks each world/level in a separate thread",
            "Worlds do not tick independently, they have to wait until all worlds are done with the current tick to continue"
        })
        public static class WORLD_TICKING {
            public static boolean ENABLED = true;
            @Ignore
            public static boolean _ENABLED = true;

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
