package io.github.shrhang.shhs_create_core;

import io.github.shrhang.shhs_create_core.api.registrate.ShHsAtlases;
import io.github.shrhang.shhs_create_core.api.registrate.ShHsRegistrate;
import io.github.shrhang.shhs_create_core.compat.Mods;
import io.github.shrhang.shhs_create_core.compat.create_enchantment_industry.CreateEnchantmentIndustry;
import io.github.shrhang.shhs_create_core.content.data.ShHsLang;
import io.github.shrhang.shhs_create_core.content.data.ShHsTagKey;
import io.github.shrhang.shhs_create_core.content.event.MagicEventHandler;
import io.github.shrhang.shhs_create_core.content.event.ShHsAttackListener;
import io.github.shrhang.shhs_create_core.content.ponder.ShHsPonderPlugin;
import io.github.shrhang.shhs_create_core.content.registries.*;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.xkmc.curseofpandora.init.registrate.CoPAttrs;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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
        ShHsConfig.init(modContainer);

        ShHsBlocks.register();
        ShHsBlockEntityTypes.register();
        ShHsPartialModels.register();
        ShHsItems.register();
        ShHsFluids.register();
        ShHsTraits.register();

        ShHsComponentTypes.register(modEventBus);
        ShHsCreativeTabs.register(modEventBus);
        ShHsMenuTypes.register(modEventBus);
        Mods.CREATE_ENCHANTMENT_INDUSTRY.executeIfInstalled(() -> () -> CreateEnchantmentIndustry.register(modEventBus));

        modEventBus.addListener(ShHsCreateCore::init);
        modEventBus.addListener(ShHsPackets::register);
        modEventBus.addListener(ShHsCreateCore::modifyEntityAttributes);
    }

    /**
     * 通用初始化（服务端 + 客户端）。
     * 在 FMLCommonSetupEvent 中执行，所有注册工作均在此完成。
     */
    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(ShHsInventoryIdentifiers::register);
        event.enqueueWork(ShHsOpenPipeEffects::register);
        if (FMLEnvironment.dist.isClient()) {
            event.enqueueWork(() -> PonderIndex.addPlugin(new ShHsPonderPlugin()));
        }
        MagicEventHandler.init();
        ShHsAttackListener.init();
    }

    /**
     * 为所有实体类型添加 Reality 属性（兼容 Curse of Pandora）。
     */
    public static void modifyEntityAttributes(final EntityAttributeModificationEvent event) {
        event.getTypes().forEach(entityType -> event.add(entityType, CoPAttrs.REALITY));
    }

    /**
     * 收集数据（精灵图、语言、标签）。
     */
    private static void gatherData() {
        ShHsAtlases.init();
        ShHsLang.init();
        ShHsTagKey.init();
    }

    /**
     * 快捷获取本模组的 ResourceLocation。
     */
    public static ResourceLocation rl(String id) {
        return ResourceLocation.fromNamespaceAndPath(MODID, id);
    }
}