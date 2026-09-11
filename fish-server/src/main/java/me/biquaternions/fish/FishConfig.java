package me.biquaternions.fish;

import de.bsommerfeld.jshepherd.annotation.Comment;
import de.bsommerfeld.jshepherd.annotation.Key;
import de.bsommerfeld.jshepherd.annotation.PostInject;
import de.bsommerfeld.jshepherd.annotation.Section;
import de.bsommerfeld.jshepherd.core.ConfigurablePojo;
import de.bsommerfeld.jshepherd.core.ConfigurationLoader;
import java.nio.file.Path;

@Comment({
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
@SuppressWarnings({"unused", "FieldMayBeFinal", "FieldCanBeLocal"})
public class FishConfig extends ConfigurablePojo<FishConfig> {

    private static FishConfig INSTANCE;
    public static FishConfig getInstance() {
        return INSTANCE;
    }

    private static boolean INITIALIZED = false;
    public static void init() {
        if (INITIALIZED) {
            return;
        }

        INSTANCE = ConfigurationLoader.from(Path.of("fish.yml"))
            .withComments()
            .load(FishConfig::new);
        INSTANCE.save();

        String configs = System.getProperty("spark.serverconfigs.extra", "");
        System.setProperty("spark.serverconfigs.extra", configs.isBlank() ? "fish.yml" : configs + ",fish.yml");

        INITIALIZED = true;
    }

    @Section("info")
    public Info info = new Info();
    public static class Info {

        @Key("version")
        public String version = "1.0";

    }

    @Comment("Async features")
    @Section("async")
    public Async async = new Async();
    public static class Async {

        @Comment({
            "\uD83D\uDD03 While it is located here in the async section, this is instead Parallel World Ticking",
            "Every world will tick in a different thread, but each will have to wait until all are done ticking to continue",
            "This is not magical raw performance, the server has to be designed (both technically and psychologically) with this feature in mind",
            "Plugin compatibility is not guaranteed, and extensive testing is needed before even enabling this feature",
            "Known incompatibilities: Citizens, NoCheatPlus, Skript, Denizen and any datapack",
            "Learn more about this feature before using: https://github.com/Biquaternions/Fish/blob/ver/1.21.8/docs/RULES.md"
        })
        @Section("world-ticking")
        public WorldTicking worldTicking = new WorldTicking();
        public static class WorldTicking {

            @Key("enabled")
            private boolean softEnabled = false;
            public transient boolean enabled = false;

            @Comment({
                "\uD83D\uDD03 Maximum number of threads that can be executed at the same time",
                "Every world will have its own thread, which means 3 worlds = 3 threads",
                "This is the number of threads that can be executed in parallel before having to wait for one to complete its tasks",
                "If the value is set to 0, it automatically uses 1/2 of the number of CPU cores and no less than 1"
            })
            @Key("threads")
            private int softThreads = 8;
            public transient int threads = 8;

        }

    }

    @PostInject
    public void validate() {
        this.async.worldTicking.enabled = this.async.worldTicking.softEnabled;
        this.async.worldTicking.threads = fallbackThreads(this.async.worldTicking.softThreads, 1);
    }

    private static int fallbackThreads(int threads, int shifts) {
        if (threads > 0) return threads;
        int thr = Runtime.getRuntime().availableProcessors() >> shifts;
        return thr > 0 ? thr : 1;
    }

}
