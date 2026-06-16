package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.LvPosRecord;
import com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.PortableStockTickerLink;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ShHsComponentTypes {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ShHsCreateCore.MODID);

    public static final DataComponentType<LvPosRecord> LV_POS = register(
            "stock_ticker_lv_n_pos",
            builder -> builder
                    .persistent(LvPosRecord.CODEC)
                    .networkSynchronized(LvPosRecord.STREAM_CODEC)
    );

    public static final DataComponentType<PortableStockTickerLink> PORTABLE_STOCK_TICKER_LINK = register(
            "link",
            builder -> builder
                    .persistent(PortableStockTickerLink.CODEC)
                    .networkSynchronized(PortableStockTickerLink.STREAM_CODEC)
    );

    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        DataComponentType<T> type = builder.apply(DataComponentType.builder()).build();
        DATA_COMPONENTS.register(name, () -> type);
        return type;
    }

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
