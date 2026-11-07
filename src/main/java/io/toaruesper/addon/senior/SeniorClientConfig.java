package io.toaruesper.addon.senior;

import net.minecraftforge.common.ForgeConfigSpec;

/** Settings only for your own game: config/toaruesper-client.toml */
public final class SeniorClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SCREEN_EFFECT;

    static {
        var b = new ForgeConfigSpec.Builder();
        SCREEN_EFFECT = b.comment("Dark edges and a flash on your screen when you are close to a black hole. Set to false to turn it off.")
                .define("screen_effect", true);
        SPEC = b.build();
    }

    private SeniorClientConfig() { }
}
