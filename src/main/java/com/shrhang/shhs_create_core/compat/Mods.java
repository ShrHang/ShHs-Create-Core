package com.shrhang.shhs_create_core.compat;

import net.createmod.catnip.lang.Lang;
import net.neoforged.fml.loading.LoadingModList;

import java.util.Optional;
import java.util.function.Supplier;
/**
 * 参考了Create的代码实现。
 * 这里包含了除Create, Iron's Spells & Spellbooks, L2Hostility 以外的模组的兼容。
 */
public enum Mods {
    CREATE_ENCHANTMENT_INDUSTRY
    ;

    private final String id;

    Mods() {
        id = Lang.asId(name());
    }

    public String id() {
        return id;
    }

    public boolean isLoaded() {
        return LoadingModList.get().getModFileById(id) != null;
    }

    public <T> Optional<T> runIfInstalled(Supplier<Supplier<T>> toRun) {
        if (isLoaded())
            return Optional.of(toRun.get().get());
        return Optional.empty();
    }

    public void executeIfInstalled(Supplier<Runnable> toExecute) {
        if (isLoaded()) {
            toExecute.get().run();
        }
    }
}
