package io.blackhole.addon;

import io.blackhole.addon.senior.BlackHoleBehavior;
import io.blackhole.addon.senior.SeniorAbilities;
import io.blackhole.addon.senior.SeniorConfig;
import io.github.pinchan4273.reacademycraft.api.AbilityId;
import io.github.pinchan4273.reacademycraft.api.ActivationKind;
import io.github.pinchan4273.reacademycraft.api.CategoryDefinition;
import io.github.pinchan4273.reacademycraft.api.Presentation;
import io.github.pinchan4273.reacademycraft.api.SkillDefinition;
import io.github.pinchan4273.reacademycraft.api.TreePosition;
import io.github.pinchan4273.reacademycraft.api.forge.RegisterAbilitiesEvent;
import io.github.pinchan4273.reacademycraft.api.mc.SkillBehavior;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.List;

/**
 * Black Hole Add-on for Re:AcademyCraft 1.5.0
 * Based on Toaru Esper addon structure.
 */
@Mod(BlackHoleAddon.MOD_ID)
public final class BlackHoleAddon {
    public static final String MOD_ID = "blackhole";
    static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> BLACKHOLE_FACTOR = ITEMS.register("blackhole_factor",
        () -> new Item(new Item.Properties().stacksTo(16)));

    static AbilityId id(String path) { return AbilityId.parse(MOD_ID + ":" + path); }

    // Category
    static final AbilityId BLACKHOLE_CATEGORY = id("blackhole");

    // Single skill
    static final AbilityId BLACK_HOLE = id("black_hole");

    public BlackHoleAddon() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(this::registerAbilities);
        bus.addListener(this::creativeTab);
        SeniorConfig.register();
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(BLACKHOLE_FACTOR);
    }

    private static Presentation shown(AbilityId skill) {
        return new Presentation(MOD_ID + ".skill." + skill.path(), MOD_ID + ".skill." + skill.path() + ".desc", null);
    }

    private static Presentation catShown() {
        return new Presentation(MOD_ID + ".ability.blackhole", null, null);
    }

    private void registerAbilities(RegisterAbilitiesEvent event) {
        var abilities = event.registrar(MOD_ID);

        // Category
        abilities.category(new CategoryDefinition(
            BLACKHOLE_CATEGORY,
            id("blackhole_factor"),
            List.of(1, 1, 1, 1, 1),
            catShown()
        ));

        // Black Hole skill (level 5, TOGGLE)
        abilities.skill(new SkillDefinition(
            BLACK_HOLE,
            BLACKHOLE_CATEGORY,
            5,
            List.of(),
            ActivationKind.TOGGLE,
            shown(BLACK_HOLE),
            new TreePosition(40, 50, null)
        ));

        // Register behavior via SeniorAbilities (like Toaru)
        SeniorAbilities.register(abilities);
    }
}