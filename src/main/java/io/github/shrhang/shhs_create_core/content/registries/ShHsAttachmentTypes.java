package io.github.shrhang.shhs_create_core.content.registries;

import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.magic.mob_spell_cast.MobCastState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ShHsAttachmentTypes {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ShHsCreateCore.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MobCastState>> MOB_CAST_STATE =
            TYPES.register("mob_cast_state", () -> AttachmentType.builder(MobCastState::new)
                    .serialize(MobCastState.SERIALIZER).build());

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
