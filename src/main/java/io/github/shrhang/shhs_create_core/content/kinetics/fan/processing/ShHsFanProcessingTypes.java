package io.github.shrhang.shhs_create_core.content.kinetics.fan.processing;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;
import io.github.shrhang.shhs_create_core.content.registries.ShHsFluids;
import io.github.shrhang.shhs_create_core.content.registries.ShHsRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

public class ShHsFanProcessingTypes {
    public static final MiracleType MIRACLE = register("miracle", new MiracleType());

    private static <T extends FanProcessingType> T register(String name, T type) {
        return Registry.register(CreateBuiltInRegistries.FAN_PROCESSING_TYPE, ShHsCreateCore.rl(name), type);
    }

    @Internal
    public static void init() {
    }

    public static class MiracleType implements FanProcessingType {
        @Override
        public boolean isValidAt(Level level, BlockPos pos) {
            FluidState state = level.getFluidState(pos);
            Fluid fluid = state.getType();
            return fluid == ShHsFluids.MIRACLE.get() || fluid == ShHsFluids.MIRACLE.getSource();
        }

        @Override
        public int getPriority() {
            return 500;
        }

        @Override
        public boolean canProcess(ItemStack stack, Level level) {
            return ShHsRecipeTypes.MIRACLE.find(new SingleRecipeInput(stack), level)
                    .filter(AllRecipeTypes.CAN_BE_AUTOMATED)
                    .isPresent();
        }

        @Override
        @Nullable
        public List<ItemStack> process(ItemStack stack, Level level) {
            return ShHsRecipeTypes.MIRACLE.find(new SingleRecipeInput(stack), level)
                    .filter(AllRecipeTypes.CAN_BE_AUTOMATED)
                    .map(RecipeHolder::value)
                    .map(recipe -> RecipeApplier.applyRecipeOn(level, stack, recipe, true))
                    .orElse(null);
        }

        @Override
        public void spawnProcessingParticles(Level level, Vec3 pos) {
            if (level.random.nextInt(8) != 0)
                return;
            level.addParticle(
                    new DustParticleOptions(new Vector3f(0.65f, 1.0f, 0.55f), 1.0f),
                    pos.x + (level.random.nextFloat() - .5f) * .5f,
                    pos.y + .5f,
                    pos.z + (level.random.nextFloat() - .5f) * .5f,
                    0,
                    1 / 8f,
                    0);
            if (level.random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + .35f, pos.z, 0, 1 / 16f, 0);
            }
        }

        @Override
        public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
            particleAccess.setColor(random.nextBoolean() ? 0x9BFF74 : 0xD7FF9C);
            particleAccess.setAlpha(1.0f);
            if (random.nextFloat() < 1 / 48f) {
                particleAccess.spawnExtraParticle(ParticleTypes.HAPPY_VILLAGER, .125f);
            }
        }

        @Override
        public void affectEntity(Entity entity, Level level) {
        }
    }
}
