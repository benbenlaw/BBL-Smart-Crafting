package com.benbenlaw.smartcrafting.compat;

import com.benbenlaw.smartcrafting.SmartCrafting;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

@JeiPlugin
public class SmartCraftingJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return SmartCrafting.identifier("jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiCompat.Impl.runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiCompat.Impl.runtime = null;
    }
}
