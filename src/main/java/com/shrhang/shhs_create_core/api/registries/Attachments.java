package com.shrhang.shhs_create_core.api.registries;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import com.shrhang.shhs_create_core.content.effect.intangible.IntangibleState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class Attachments {
    private static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ShHsCreateCore.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<IntangibleState>> INTANGIBLE_STATE =
            REGISTER.register("intangible_state", () -> AttachmentType.builder(IntangibleState::new).build());

    public static void register(IEventBus bus) {
        REGISTER.register(bus);
    }
}
