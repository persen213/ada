package io.toaruesper.addon;

import io.toaruesper.addon.net.SeniorNetwork;
import io.toaruesper.addon.senior.BlackHoleBehavior;
import io.toaruesper.addon.senior.SeniorAbilities;
import io.toaruesper.addon.senior.SeniorClientConfig;
import io.toaruesper.addon.senior.SeniorConfig;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entry point of the Toaru Esper add-on for Re:AcademyCraft. */
@Mod(ToaruEsper.MOD_ID)
public final class ToaruEsper {
    public static final String MOD_ID = "toaruesper";

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    /** The item that gives the Senior category on a Re:AcademyCraft developer machine. */
    public static final RegistryObject<Item> SENIOR_FACTOR =
            ITEMS.register("senior_factor", () -> new Item(new Item.Properties().stacksTo(16)));

    public ToaruEsper() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        // Re:AcademyCraft fires this event once during common setup; our category and skills are registered inside it.
        bus.addListener(SeniorAbilities::register);
        bus.addListener(this::commonSetup);
        bus.addListener(this::creativeTab);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SeniorConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SeniorClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(BlackHoleBehavior::onServerStopped);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SeniorNetwork::register);
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(SENIOR_FACTOR);
    }
}
