package com.shrhang.shhs_create_core.content.registries;

import com.shrhang.shhs_create_core.content.logistics.brass_ender_chest.BrassEnderChestBlockEntity;
import com.simibubi.create.api.packager.InventoryIdentifier;

public class ShHsInventoryIdentifiers {
    public static void register() {
        InventoryIdentifier.REGISTRY.register(ShHsBlocks.BRASS_ENDER_CHEST.get(), (level, state, face) ->
                level.getBlockEntity(face.getPos()) instanceof BrassEnderChestBlockEntity be ? be.getInvId() : null);
    }
}
