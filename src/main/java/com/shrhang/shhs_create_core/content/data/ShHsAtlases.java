package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import com.tterrag.registrate.providers.ProviderType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SpriteSourceProvider;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import static com.shrhang.shhs_create_core.ShHsCreateCore.REGISTRATE;

public class ShHsAtlases {
    /**
     * 需要加入 {@code minecraft:blocks} 图集的虚拟流体名称。
     * <p>
     * 使用 {@link TreeSet} 是为了让 datagen 输出顺序稳定，避免每次生成文件顺序随机变化。
     */
    private static final Set<String> VIRTUAL_FLUIDS = new TreeSet<>();

    /**
     * 记录一个使用默认贴图路径的虚拟流体。
     * <p>
     * 该方法由 {@link ShHsRegistrate#virtualFluid(String)} 自动调用。这里只接收流体名，
     * 因此约定对应贴图一定是 {@code fluid/<name>_still} 和 {@code fluid/<name>_flow}。
     */
    public static void addVirtualFluid(String name) {
        VIRTUAL_FLUIDS.add(name);
    }

    /**
     * 将 atlas 生成器接入 Registrate 的客户端 datagen。
     * <p>
     * {@link SpriteSourceProvider} 是 NeoForge 的普通 {@code DataProvider}，不是
     * Registrate 的 {@code RegistrateProvider}，所以不能直接作为 {@code ProviderType}
     * 注册。这里通过 {@link ProviderType#GENERIC_CLIENT} 这个桥接 provider，把普通
     * {@code DataProvider} 挂进 Registrate 的 datagen 流程。
     */
    public static void init() {
        REGISTRATE.addDataGenerator(ProviderType.GENERIC_CLIENT, provider ->
                provider.add(data -> new Provider(data.output(), data.registries(), data.existingFileHelper())));
    }

    /**
     * 实际生成 {@code assets/minecraft/atlases/blocks.json} 的 provider。
     */
    private static class Provider extends SpriteSourceProvider {
        public Provider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                        ExistingFileHelper existingFileHelper) {
            super(output, lookupProvider, ShHsCreateCore.MODID, existingFileHelper);
        }

        @Override
        protected void gather() {
            SourceList blocks = atlas(BLOCKS_ATLAS);
            VIRTUAL_FLUIDS.forEach(name -> {
                addFluidSprite(blocks, name, "flow");
                addFluidSprite(blocks, name, "still");
            });
        }

        /**
         * 向 {@code minecraft:blocks} 图集中添加一个单文件 sprite。
         * <p>
         * 例如 {@code name = "hostility"}, {@code suffix = "still"} 时，
         * 会生成 {@code shhs_create_core:fluid/hostility_still}。
         */
        private void addFluidSprite(SourceList blocks, String name, String suffix) {
            ResourceLocation sprite = ShHsCreateCore.rl("fluid/" + name + "_" + suffix);
            blocks.addSource(new SingleFile(sprite, Optional.empty()));
        }
    }
}
