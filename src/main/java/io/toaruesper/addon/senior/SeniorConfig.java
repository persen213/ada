package io.toaruesper.addon.senior;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings file: config/toaruesper-common.toml (created the first time the game starts with the add-on).
 * Changes apply the next time the game or server starts. On a server, the server's file is the one that counts.
 */
public final class SeniorConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue BH_RANGE;
    public static final ForgeConfigSpec.DoubleValue BH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue BH_START_REACH;
    public static final ForgeConfigSpec.DoubleValue BH_DURATION_SECONDS;
    public static final ForgeConfigSpec.DoubleValue BH_PULL;
    public static final ForgeConfigSpec.DoubleValue BH_CRITICAL_BOOST;
    public static final ForgeConfigSpec.DoubleValue BH_DAMAGE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue BH_DAMAGE_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue BH_DAMAGE_RAMP;
    public static final ForgeConfigSpec.DoubleValue BH_FINAL_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BH_KNOCKBACK;
    public static final ForgeConfigSpec.DoubleValue BH_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue BH_CP_COST;
    public static final ForgeConfigSpec.DoubleValue BH_OVERLOAD_COST;
    public static final ForgeConfigSpec.IntValue BH_MAX_TARGETS;
    public static final ForgeConfigSpec.IntValue BH_MAX_OBJECTS;
    public static final ForgeConfigSpec.BooleanValue BH_AFFECT_BOSSES;
    public static final ForgeConfigSpec.BooleanValue BH_ABSORB_PROJECTILES;
    public static final ForgeConfigSpec.BooleanValue BH_PULL_ITEMS;
    public static final ForgeConfigSpec.BooleanValue BH_LINE_OF_SIGHT;

    static {
        var b = new ForgeConfigSpec.Builder();
        b.comment("Black Hole (Senior). The cooldown starts after the hole has ended.").push("black_hole");
        BH_RANGE = b.comment("How far away (blocks) the hole can be placed.")
                .defineInRange("range", 28.0, 5.0, 64.0);
        BH_RADIUS = b.comment("Full radius (blocks) of the pull area and of the final collapse.")
                .defineInRange("radius", 12.0, 3.0, 32.0);
        BH_START_REACH = b.comment("The pull area starts at this fraction of the radius and grows to the full radius over 2 seconds.")
                .defineInRange("start_reach_fraction", 0.55, 0.2, 1.0);
        BH_DURATION_SECONDS = b.comment("How long the hole stays open before it collapses.")
                .defineInRange("duration_seconds", 7.0, 2.0, 30.0);
        BH_PULL = b.comment("Pull strength. Higher drags targets in faster.")
                .defineInRange("pull_strength", 0.22, 0.02, 1.0);
        BH_CRITICAL_BOOST = b.comment("In the last 0.6 seconds before the collapse the hole contracts and the pull is multiplied by this.")
                .defineInRange("critical_pull_multiplier", 2.2, 1.0, 5.0);
        BH_DAMAGE_RADIUS = b.comment("Creatures this close to the centre take damage while the hole is open. Projectiles this close are swallowed.")
                .defineInRange("damage_radius", 4.0, 1.0, 16.0);
        BH_DAMAGE_PER_SECOND = b.comment("Damage once per second near the centre (doubles right at the core). Scales a little with skill experience.")
                .defineInRange("damage_per_second", 4.0, 0.0, 200.0);
        BH_DAMAGE_RAMP = b.comment("The damage per second grows from 1x to this multiple as the hole gets older.")
                .defineInRange("damage_ramp", 2.0, 1.0, 5.0);
        BH_FINAL_DAMAGE = b.comment("Damage of the final collapse (less at the edge of the radius).")
                .defineInRange("final_damage", 24.0, 0.0, 500.0);
        BH_KNOCKBACK = b.comment("How hard survivors and loose items are thrown outward by the final collapse.")
                .defineInRange("knockback", 1.8, 0.0, 6.0);
        BH_COOLDOWN_SECONDS = b.comment("Cooldown in seconds after the hole has ended.")
                .defineInRange("cooldown_seconds", 45.0, 0.0, 1800.0);
        BH_CP_COST = b.comment("CP (Re:AcademyCraft energy) cost. A starting guess; tune it to your server's CP scale.")
                .defineInRange("cp_cost", 800.0, 0.0, 1_000_000.0);
        BH_OVERLOAD_COST = b.comment("Overload added when used.")
                .defineInRange("overload_cost", 150.0, 0.0, 1_000_000.0);
        BH_MAX_TARGETS = b.comment("At most this many creatures are pulled at once (the closest ones). Protects server speed.")
                .defineInRange("max_targets", 40, 1, 200);
        BH_MAX_OBJECTS = b.comment("At most this many projectiles and loose items are pulled at once (the closest ones).")
                .defineInRange("max_objects", 60, 1, 300);
        BH_AFFECT_BOSSES = b.comment("If false, the Ender Dragon and the Wither are not pulled or hurt.")
                .define("affect_bosses", false);
        BH_ABSORB_PROJECTILES = b.comment("If true, arrows and other projectiles (not yours) are bent toward the hole and swallowed near the centre.")
                .define("absorb_projectiles", true);
        BH_PULL_ITEMS = b.comment("If true, loose items are pulled in and thrown out again by the collapse.")
                .define("pull_items", true);
        BH_LINE_OF_SIGHT = b.comment("If true, only things with a clear line to the hole are pulled or hurt (walls protect).")
                .define("require_line_of_sight", true);
        b.pop();
        SPEC = b.build();
    }

    private SeniorConfig() { }
}
