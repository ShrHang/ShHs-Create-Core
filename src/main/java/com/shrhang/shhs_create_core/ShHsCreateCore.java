package com.shrhang.shhs_create_core;

import com.shrhang.shhs_create_core.compat.Mods;
import com.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import com.shrhang.shhs_create_core.content.data.ShHsLang;
import com.shrhang.shhs_create_core.content.data.ShHsRegistrate;
import com.shrhang.shhs_create_core.content.data.ShHsTagKey;
import com.shrhang.shhs_create_core.content.event.IntangibleEventHandler;
import com.shrhang.shhs_create_core.content.event.MagicEventHandler;
import com.shrhang.shhs_create_core.content.event.ShHsAttackListener;
import com.shrhang.shhs_create_core.content.registries.Attachments;
import com.shrhang.shhs_create_core.content.registries.CreativeTabs;
import com.shrhang.shhs_create_core.content.registries.Effects;
import com.shrhang.shhs_create_core.content.registries.Fluids;
import com.shrhang.shhs_create_core.content.registries.Items;
import com.shrhang.shhs_create_core.content.registries.OpenPipeEffects;
import com.shrhang.shhs_create_core.content.registries.Traits;
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
            .defaultCreativeTab(CreativeTabs.DEFAULT.getKey())
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public ShHsCreateCore(IEventBus modEventBus, ModContainer modContainer) {
        gatherData();
        Config.init(modContainer);

        Attachments.register(modEventBus);
        CreativeTabs.register(modEventBus);
        Items.register();
        Effects.register();
        Fluids.register();
        Traits.register();

        modEventBus.addListener(ShHsCreateCore::init);
        modEventBus.addListener(ShHsCreateCore::modifyEntityAttributes);
    }

    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(OpenPipeEffects::register);
        IntangibleEventHandler.init();
        MagicEventHandler.init();
        ShHsAttackListener.init();
        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> CreateEnchantmentIndustry::init);
    }

    public static void modifyEntityAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(entityType -> event.add(entityType, CoPAttrs.REALITY));
    }

    private static void gatherData() {
        ShHsLang.init();
        ShHsTagKey.init();
    }

    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(MODID, id);
    }
}
