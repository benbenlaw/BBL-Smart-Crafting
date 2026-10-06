package com.benbenlaw.smartcrafting.util;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class PlayerData {

    public static final String FAVORITES_TAG = "smart_crafting_favorites";
    public static final String SORT_TYPE_TAG = "smart_crafting_sort_type";
    public static final String RECURSIVE_TAG = "smart_crafting_recursive";

    public static String getSortType(Player player) {
        return player.getPersistentData().getStringOr(SORT_TYPE_TAG, "");
    }

    public static void setSortType(Player player, String sortType) {
        player.getPersistentData().putString(SORT_TYPE_TAG, sortType);
    }

    public static boolean isRecursive(Player player) {
        return player.getPersistentData().getBooleanOr(RECURSIVE_TAG, false);
    }

    public static void setRecursive(Player player, boolean recursive) {
        player.getPersistentData().putBoolean(RECURSIVE_TAG, recursive);
    }

    public static Set<String> getFavorites(Player player) {
        Set<String> favorites = new LinkedHashSet<>();
        for (var tag : player.getPersistentData().getListOrEmpty(FAVORITES_TAG)) {
            tag.asString().ifPresent(favorites::add);
        }
        return favorites;
    }

    public static void setFavorites(Player player, Collection<String> favorites) {
        ListTag list = new ListTag();
        for (String favorite : favorites) {
            list.add(StringTag.valueOf(favorite));
        }
        player.getPersistentData().put(FAVORITES_TAG, list);
    }
}
