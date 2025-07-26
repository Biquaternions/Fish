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

    @Override
    public void load() {
        super.load();
        if (initialized) init();
    }

    @Ignore
    private static boolean initialized = false;
    public static void init() {
        initialized = true;
    }

}
