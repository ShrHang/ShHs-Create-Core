package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker.LogisticsNetworkLink;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ShHsComponentTypes {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ShHsCreateCore.MODID);

    public static final DataComponentType<LogisticsNetworkLink> LOGISTICS_NETWORK_LINK = register(
            "freq_id",
            builder -> builder
                    .persistent(LogisticsNetworkLink.CODEC)
                    .networkSynchronized(LogisticsNetworkLink.STREAM_CODEC)
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
