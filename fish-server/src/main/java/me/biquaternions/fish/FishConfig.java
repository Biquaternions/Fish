package me.biquaternions.fish;

import net.j4c0b3y.api.config.ConfigHandler;
import net.j4c0b3y.api.config.StaticConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    public static final Logger LOGGER = LogManager.getLogger("Fish Sentinel");

    @Ignore
    public static FishConfig INSTANCE;

    public FishConfig() {
        super(new File("fish.yml"), HANDLER);
        INSTANCE = this;

        String configs = System.getProperty("spark.serverconfigs.extra", "");
        System.setProperty("spark.serverconfigs.extra", configs.isBlank() ? "fish.yml" : configs + ",fish.yml");

    }

    @Priority(1)
    @SuppressWarnings("unused")
    public static class INFO {
        public static String VERSION = "1.0";
    }

    @Priority(2)
    @Comment("Async features")
    public static class ASYNC {

        @Comment({
            "\uD83D\uDD03 While it is located here in the async section, this is instead Parallel World Ticking",
            "Every world will tick in a different thread, but each will have to wait until all are done ticking to continue",
            "This is not magical raw performance, the server has to be designed (both technically and psychologically) with this feature in mind",
            "Plugin compatibility is not guaranteed, and extensive testing is needed before even enabling this feature",
            "Known incompatibilities: Citizens, NoCheatPlus, Skript, Denizen and any datapack",
            "Learn more about this feature before using: https://github.com/Biquaternions/Fish/blob/ver/1.21.8/docs/RULES.md"
        })
        public static class WORLD_TICKING {
            public static boolean ENABLED = false;
            @Ignore
            public static boolean _ENABLED = false;

            @Comment({
                "\uD83D\uDD03 Maximum number of threads that can be executed at the same time",
                "Every world will have its own thread, which means 3 worlds = 3 threads",
                "This is the number of threads that can be executed in parallel before having to wait for one to complete its tasks",
                "If the value is set to 0, it automatically uses 1/2 of the number of CPU cores and no less than 1"
            })
            public static int THREADS = 8;
            @Ignore
            public static int _THREADS = 8;

            @Hidden
            @Comment({
                "\uD83D\uDD03 You have agreed to use Fish's Parallel World Ticking rules and the Fish Council has",
                "decided that you're now allowed to use it. Use it responsibly."
            })
            public static boolean I_KNOW_WHAT_I_AM_DOING_I_SWEAR_BY_FISH = false;

            @Hidden
            @Comment({
                "\uD83D\uDD25 Prints an stacktrace when a plugin attempts to access a protected function",
                "Servers with Parallel World Ticking should be designed with that feature in mind, as such, these accesses should be as minimal as possible",
                "Use this option to identify which plugins are accessing these functions, this will require extensive testing to identify most if not all possible scenarios"
            })
            public static boolean LOG_ASYNC_ACCESSES = false;

        }
    }

    @Override
    public void load() {
        super.load();
        if (initialized) init();
        else {
            ASYNC.WORLD_TICKING._ENABLED = ASYNC.WORLD_TICKING.ENABLED;
            ASYNC.WORLD_TICKING._THREADS = Math.max(1, ASYNC.WORLD_TICKING.THREADS);

            if (ASYNC.WORLD_TICKING._ENABLED && !ASYNC.WORLD_TICKING.I_KNOW_WHAT_I_AM_DOING_I_SWEAR_BY_FISH) {
                LOGGER.error("You enabled the Parallel World Ticking feature, but did not agreed to the fish rules of PWT");
                LOGGER.error("Read more: https://github.com/Biquaternions/Fish/blob/ver/1.21.8/docs/RULES.md");
                LOGGER.error("The fish council will determine if you're worthy of using this feature.");
                LOGGER.error("Parallel World Ticking will be disabled until you agree to the rules and restart your server.");
                LOGGER.error("Reject monke, return to fish \uD83D\uDC1F");
            }

        }
    }

    @Ignore
    private static boolean initialized = false;
    public static void init() {
        initialized = true;
    }

}
