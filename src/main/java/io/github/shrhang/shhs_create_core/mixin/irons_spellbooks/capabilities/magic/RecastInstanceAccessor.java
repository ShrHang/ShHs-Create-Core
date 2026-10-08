package io.github.shrhang.shhs_create_core.mixin.irons_spellbooks.capabilities.magic;

import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecastInstance.class)
public interface RecastInstanceAccessor {
    @Accessor("remainingTicks")
    void shhs$setRemainingTicks(int ticks);
}
