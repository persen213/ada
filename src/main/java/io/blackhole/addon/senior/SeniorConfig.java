package io.blackhole.addon.senior;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Configuration for Black Hole add-on.
 */
public final class SeniorConfig {
    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        var pair = new ForgeConfigSpec.Builder().configure(builder -> {
            COMMON = new Common(builder);
        });
        COMMON_SPEC = pair.getRight();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "blackhole-common.toml");
    }

    public static class Common {
        public final ForgeConfigSpec.IntValue cpCost;
        public final ForgeConfigSpec.IntValue overloadCost;
        public final ForgeConfigSpec.IntValue cooldownSeconds;
        public final ForgeConfigSpec.IntValue range;
        public final ForgeConfigSpec.IntValue maxRadius;
        public final ForgeConfigSpec.IntValue growDurationSeconds;
        public final ForgeConfigSpec.IntValue activeDurationSeconds;
        public final ForgeConfigSpec.FloatValue pullStrength;
        public final ForgeConfigSpec.FloatValue damage;

        Common(ForgeConfigSpec.Builder builder) {
            builder.push("black_hole");
            builder.comment("Black Hole skill settings");
            cpCost = builder.defineInRange("cpCost", 80, 0, 1000);
            overloadCost = builder.defineInRange("overloadCost", 20, 0, 200);
            cooldownSeconds = builder.defineInRange("cooldownSeconds", 10, 0, 7200);
            range = builder.defineInRange("range", 28, 1, 100);
            maxRadius = builder.defineInRange("maxRadius", 12, 1, 64);
            growDurationSeconds = builder.defineInRange("growDurationSeconds", 1, 1, 60);
            activeDurationSeconds = builder.defineInRange("activeDurationSeconds", 5, 1, 60);
            pullStrength = builder.defineInRange("pullStrength", 0.3, 0.0, 2.0);
            damage = builder.defineInRange("damage", 15.0, 0.0, 100.0);
            builder.pop();
        }
    }
}