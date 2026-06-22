package com.shrhang.shhs_create_core.content.logistics.portable_stock_ticker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsNetwork;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 表示服务端观测到的物流网络状态，用于客户端判断。
 */
public enum LogisticsNetworkStatus {
    INACCESSIBLE,
    AVAILABLE,
    NO_NETWORK,
    UNLOADED;

    public static LogisticsNetworkStatus resolve(Player player, UUID networkId) {
        LogisticsNetwork network = Create.LOGISTICS.logisticsNetworks.get(networkId);
        if (network == null) return NO_NETWORK;
        if (!Create.LOGISTICS.mayInteract(networkId, player)) return INACCESSIBLE;
        if (LogisticallyLinkedBehaviour.getAllPresent(networkId, false).isEmpty()) return UNLOADED;
        return AVAILABLE;
    }
}
