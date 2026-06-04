package com.shrhang.shhs_create_core.content.data;

import com.shrhang.shhs_create_core.ShHsCreateCore;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.common.data.SpriteSourceProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

public class ShHsAtlases {
    private static final Set<String> VIRTUAL_FLUIDS = new TreeSet<>();

    public static void addVirtualFluid(String name) {
        VIRTUAL_FLUIDS.add(name);
    }

    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(),
                new Provider(event.getGenerator().getPackOutput(), event.getLookupProvider(), event.getExistingFileHelper()));
    }

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

        private void addFluidSprite(SourceList blocks, String name, String suffix) {
            ResourceLocation sprite = ShHsCreateCore.rl("fluid/" + name + "_" + suffix);
            blocks.addSource(new SingleFile(sprite, Optional.empty()));
        }
    }
}
