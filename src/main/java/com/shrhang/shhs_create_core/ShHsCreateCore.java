package com.shrhang.shhs_create_core;

import com.shrhang.shhs_create_core.compat.Mods;
import com.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import com.shrhang.shhs_create_core.content.data.ShHsAtlases;
import com.shrhang.shhs_create_core.content.data.ShHsLang;
import com.shrhang.shhs_create_core.content.data.ShHsRegistrate;
import com.shrhang.shhs_create_core.content.data.ShHsTagKey;
import com.shrhang.shhs_create_core.content.event.effect.IntangibleEventHandler;
import com.shrhang.shhs_create_core.content.event.MagicEventHandler;
import com.shrhang.shhs_create_core.content.event.ShHsAttackListener;
import com.shrhang.shhs_create_core.api.registries.ShHsAttachments;
import com.shrhang.shhs_create_core.api.registries.ShHsCreativeTabs;
import com.shrhang.shhs_create_core.api.registries.ShHsEffects;
import com.shrhang.shhs_create_core.api.registries.ShHsFluids;
import com.shrhang.shhs_create_core.api.registries.ShHsItems;
import com.shrhang.shhs_create_core.api.registries.ShHsOpenPipeEffects;
import com.shrhang.shhs_create_core.api.registries.ShHsPotions;
import com.shrhang.shhs_create_core.api.registries.ShHsTraits;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

@Mod(ShHsCreateCore.MODID)
public class ShHsCreateCore {
    public static final String MODID = "shhs_create_core";
    public static final ShHsRegistrate REGISTRATE = (ShHsRegistrate) ShHsRegistrate.create(MODID)
            .defaultCreativeTab(ShHsCreativeTabs.DEFAULT.getKey())
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public ShHsCreateCore(IEventBus modEventBus, ModContainer modContainer) {
        gatherData();
        Config.init(modContainer);

        ShHsAttachments.register(modEventBus);
        ShHsCreativeTabs.register(modEventBus);
        ShHsItems.register();
        ShHsEffects.register();
        ShHsFluids.register();
        ShHsPotions.register();
        ShHsTraits.register();

        modEventBus.addListener(ShHsCreateCore::init);
        modEventBus.addListener(ShHsCreateCore::modifyEntityAttributes);
    }

    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(ShHsOpenPipeEffects::register);
        IntangibleEventHandler.init();
        MagicEventHandler.init();
        ShHsAttackListener.init();
        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> CreateEnchantmentIndustry::init);
    }

    public static void modifyEntityAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(entityType -> event.add(entityType, CoPAttrs.REALITY));
    }

    private static void gatherData() {
        ShHsAtlases.init();
        ShHsLang.init();
        ShHsTagKey.init();
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(MODID, id);
    }
}
