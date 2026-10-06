package com.benbenlaw.smartcrafting.compat;

import mezz.jei.api.runtime.IJeiRuntime;
import net.neoforged.fml.ModList;

public class JeiCompat {

    public static boolean isAvailable() {
        return ModList.get().isLoaded("jei") && Impl.runtime != null;
    }

    public static String getFilterText() {
        return isAvailable() ? Impl.runtime.getIngredientFilter().getFilterText() : "";
    }

    public static void setFilterText(String text) {
        if (isAvailable()) {
            Impl.runtime.getIngredientFilter().setFilterText(text);
        }
    }

    static class Impl {
        static IJeiRuntime runtime;
    }
}
