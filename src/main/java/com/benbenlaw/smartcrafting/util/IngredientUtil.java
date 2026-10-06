package com.benbenlaw.smartcrafting.util;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class IngredientUtil {

    /** Works for custom ingredients too, unlike Ingredient#getValues. */
    @SuppressWarnings("deprecation")
    public static List<Holder<Item>> items(Ingredient ingredient) {
        return ingredient.items().toList();
    }

    public static ItemStack firstItem(Ingredient ingredient) {
        List<Holder<Item>> items = items(ingredient);
        return items.isEmpty() ? ItemStack.EMPTY : items.getFirst().value().getDefaultInstance();
    }
}
