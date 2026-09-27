package io.github.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashSet;
import java.util.List;

public record PortableStockTickerAddresses(List<String> addresses) {
    public static final PortableStockTickerAddresses EMPTY = new PortableStockTickerAddresses(List.of());
    public static final Codec<PortableStockTickerAddresses> CODEC = Codec.STRING.listOf()
            .xmap(PortableStockTickerAddresses::new, PortableStockTickerAddresses::addresses);
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableStockTickerAddresses> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PortableStockTickerAddresses {
        addresses = List.copyOf(new LinkedHashSet<>(addresses));
    }

    public static PortableStockTickerAddresses fromClipboard(DataComponentMap components) {
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        ClipboardEntry.readAll(components).forEach(page -> page.forEach(entry -> {
            String text = entry.text.getString();
            if (entry.checked || !text.startsWith("#") || text.length() == 1) {
                return;
            }

            String address = text.substring(1).trim();
            if (!address.isBlank()) {
                addresses.add(address);
            }
        }));
        return new PortableStockTickerAddresses(List.copyOf(addresses));
    }

    public PortableStockTickerAddresses merge(PortableStockTickerAddresses other) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(addresses);
        merged.addAll(other.addresses);
        return new PortableStockTickerAddresses(List.copyOf(merged));
    }
}
