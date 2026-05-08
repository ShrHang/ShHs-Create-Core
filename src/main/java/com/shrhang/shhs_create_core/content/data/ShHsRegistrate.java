package com.shrhang.shhs_create_core.content.data;

import com.simibubi.create.api.registrate.CreateRegistrateRegistrationCallback;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.xkmc.l2hostility.content.config.TraitConfig;
import dev.xkmc.l2hostility.content.traits.base.MobTrait;
import dev.xkmc.l2hostility.init.registrate.LHTraits;

public class ShHsRegistrate extends CreateRegistrate {

    protected ShHsRegistrate(String modid) {
            super(modid);
    }
    public static ShHsRegistrate create(String modid) {
        ShHsRegistrate registrate = new ShHsRegistrate(modid);
        CreateRegistrateRegistrationCallback.provideRegistrate(registrate);
        return registrate;
    }

    /**
     * 复刻了l2hostility的trait注册方法，注册时会自动生成对应的tag和物品。
     */
    public final <T extends MobTrait> ShHsTraitBuilder<T> trait(String name, NonNullSupplier<T> sup,TraitConfig config) {
        return entry(name, cb -> new ShHsTraitBuilder<>(this, this, name, cb, sup))
                .dataMap(LHTraits.DATA.reg(), config)
                .item().build();
    }
}
