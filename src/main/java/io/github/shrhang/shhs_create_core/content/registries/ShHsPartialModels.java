package io.github.shrhang.shhs_create_core.content.registries;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import io.github.shrhang.shhs_create_core.ShHsCreateCore;

public class ShHsPartialModels {

    public static final PartialModel SPRAYER_GAUGE = block("sprayer/gauge");
    public static final PartialModel SPRAYER_POINTER = block("sprayer/pointer");

    private static PartialModel block(String path) {
        return PartialModel.of(ShHsCreateCore.rl("block/" + path));
    }

    public static void register() {}
}
