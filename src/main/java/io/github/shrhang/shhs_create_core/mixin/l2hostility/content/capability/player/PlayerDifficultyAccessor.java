package io.github.shrhang.shhs_create_core.mixin.l2hostility.content.capability.player;

import dev.xkmc.l2hostility.content.capability.player.PlayerDifficulty;
import dev.xkmc.l2hostility.content.logic.DifficultyLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerDifficulty.class)
public interface PlayerDifficultyAccessor {

    @Accessor("difficulty")
    DifficultyLevel shhsc$getDifficulty();
}
