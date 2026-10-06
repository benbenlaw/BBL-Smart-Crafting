package com.benbenlaw.smartcrafting.crafting;

import com.benbenlaw.smartcrafting.util.IngredientUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.*;
import java.util.function.Predicate;

/**
 * Works out which intermediate crafting recipes are needed to craft a target recipe,
 * e.g. logs -> planks -> stairs, using a simulated copy of the available items.
 * Recipe data is cached per recipe map so repeated checks stay cheap.
 */
public class RecursivePlanner {

    public record Group(Ingredient ingredient, int count, Set<Item> items) {
    }

    public record RecipeInfo(RecipeHolder<CraftingRecipe> holder, List<Group> groups, ItemStack result) {

        /** True when every ingredient could be satisfied by at least one of the given items. */
        public boolean possibleWith(Set<Item> available) {
            for (Group group : groups) {
                if (Collections.disjoint(group.items(), available)) {
                    return false;
                }
            }
            return true;
        }
    }

    public record Step(RecipeHolder<CraftingRecipe> holder, int crafts) {
    }

    public record Plan(List<Step> steps, List<ItemStack> intermediates) {
    }

    private static final int MAX_CANDIDATES_PER_INGREDIENT = 6;
    private static final int RECIPE_BUDGET = 150;

    private static RecipeMap indexedMap;
    private static List<RecipeInfo> allInfos = List.of();
    private static Map<Identifier, RecipeInfo> infoById = new HashMap<>();
    private static Map<Item, List<RecipeInfo>> producers = new HashMap<>();
    private static Map<Ingredient, List<RecipeInfo>> candidateCache = new HashMap<>();

    private final int maxDepth;
    private int budget;
    private List<ItemStack> sim;
    private final List<Step> steps = new ArrayList<>();
    private final Set<Identifier> visiting = new HashSet<>();
    // Items that are being crafted further up the chain, they can't be spent making intermediates (stops ingot -> nuggets -> ingot)
    private final Set<Item> forbidden = new HashSet<>();

    private RecursivePlanner(List<ItemStack> inventory, int maxDepth) {
        this.sim = copy(inventory);
        this.maxDepth = maxDepth;
        this.budget = RECIPE_BUDGET;
    }

    public static List<RecipeInfo> infos(ServerLevel level) {
        ensureIndex(level);
        return allInfos;
    }

    public static RecipeInfo info(ServerLevel level, Identifier id) {
        ensureIndex(level);
        return infoById.get(id);
    }

    /**
     * Works out which items could be obtained by crafting (ignoring amounts) in small slices.
     * A cheap over-estimate used to skip recipes that can never be satisfied before running the
     * real (more expensive) planner.
     */
    public static class ReachabilityJob {
        private final List<RecipeInfo> infos;
        private final Set<Item> have;
        private final int layers;
        private int layer = 0;
        private int index = 0;
        private boolean changed = false;

        public ReachabilityJob(ServerLevel level, Set<Item> present, int layers) {
            ensureIndex(level);
            this.infos = allInfos;
            this.have = new HashSet<>(present);
            this.layers = layers;
        }

        /** @return true once finished, false when the deadline was hit first */
        public boolean step(long deadlineNanos) {
            while (layer < layers) {
                while (index < infos.size()) {
                    if (System.nanoTime() >= deadlineNanos) return false;

                    RecipeInfo info = infos.get(index++);
                    Item resultItem = info.result().getItem();
                    if (!have.contains(resultItem) && info.possibleWith(have)) {
                        have.add(resultItem);
                        changed = true;
                    }
                }
                if (!changed) {
                    layer = layers;
                    break;
                }
                layer++;
                index = 0;
                changed = false;
            }
            return true;
        }

        public Set<Item> result() {
            return have;
        }
    }

    /**
     * @return the plan, or null when the target cannot be satisfied. A plan with no steps means
     * the recipe can be crafted without any intermediate crafting.
     */
    public static Plan plan(ServerLevel level, RecipeInfo target, List<ItemStack> inventory, int maxDepth) {
        ensureIndex(level);

        RecursivePlanner planner = new RecursivePlanner(inventory, maxDepth);
        planner.forbidden.add(target.result().getItem());

        for (Group group : target.groups()) {
            if (!planner.provide(group.ingredient(), group.count(), 0)) {
                return null;
            }
        }

        List<ItemStack> intermediates = new ArrayList<>();
        for (Step step : planner.steps) {
            ItemStack result = infoById.get(step.holder().id().identifier()).result();
            if (intermediates.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, result))) {
                intermediates.add(result.copyWithCount(1));
            }
        }
        return new Plan(List.copyOf(planner.steps), intermediates);
    }

    public static Map<Ingredient, Integer> groupIngredients(CraftingRecipe recipe) {
        Map<Ingredient, Integer> groups = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (!ingredient.isEmpty()) {
                groups.merge(ingredient, 1, Integer::sum);
            }
        }
        return groups;
    }

    private boolean provide(Ingredient ingredient, int count, int depth) {
        Predicate<ItemStack> usable = stack -> ingredient.test(stack) && (depth == 0 || !forbidden.contains(stack.getItem()));

        count -= take(usable, count);
        if (count <= 0) return true;
        if (depth >= maxDepth) return false;

        for (RecipeInfo candidate : candidates(ingredient)) {
            if (budget-- <= 0) return false;

            Identifier id = candidate.holder().id().identifier();
            ItemStack result = candidate.result();
            if (!ingredient.test(result)) continue;
            if (!visiting.add(id)) continue;

            boolean addedForbidden = forbidden.add(result.getItem());
            List<ItemStack> snapshot = copy(sim);
            int stepCount = steps.size();
            int crafts = (int) Math.ceil(count / (double) result.getCount());

            boolean ok = true;
            for (Group group : candidate.groups()) {
                if (!provide(group.ingredient(), group.count() * crafts, depth + 1)) {
                    ok = false;
                    break;
                }
            }
            visiting.remove(id);
            if (addedForbidden) {
                forbidden.remove(result.getItem());
            }

            if (ok) {
                steps.add(new Step(candidate.holder(), crafts));
                sim.add(result.copyWithCount(result.getCount() * crafts));
                take(usable, count);
                return true;
            }

            sim = snapshot;
            while (steps.size() > stepCount) {
                steps.removeLast();
            }
        }
        return false;
    }

    private static List<RecipeInfo> candidates(Ingredient ingredient) {
        return candidateCache.computeIfAbsent(ingredient, key -> {
            List<RecipeInfo> found = new ArrayList<>();
            for (Holder<Item> item : IngredientUtil.items(key)) {
                found.addAll(producers.getOrDefault(item.value(), List.of()));
            }
            found.sort(Comparator
                    .comparingInt((RecipeInfo info) -> info.groups().size())
                    .thenComparing(info -> info.holder().id().identifier().toString()));
            return found.size() > MAX_CANDIDATES_PER_INGREDIENT ? List.copyOf(found.subList(0, MAX_CANDIDATES_PER_INGREDIENT)) : List.copyOf(found);
        });
    }

    private int take(Predicate<ItemStack> matcher, int count) {
        int taken = 0;
        for (ItemStack stack : sim) {
            if (taken >= count) break;
            if (!stack.isEmpty() && matcher.test(stack)) {
                int amount = Math.min(stack.getCount(), count - taken);
                stack.shrink(amount);
                taken += amount;
            }
        }
        sim.removeIf(ItemStack::isEmpty);
        return taken;
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return copy;
    }

    private static void ensureIndex(ServerLevel level) {
        RecipeMap recipeMap = level.recipeAccess().recipeMap();
        if (recipeMap == indexedMap) return;

        List<RecipeInfo> newInfos = new ArrayList<>();
        Map<Identifier, RecipeInfo> newById = new HashMap<>();
        Map<Item, List<RecipeInfo>> newProducers = new HashMap<>();

        for (RecipeHolder<CraftingRecipe> holder : recipeMap.byType(RecipeType.CRAFTING)) {
            ItemStack result = sampleResult(holder.value());
            if (result.isEmpty()) continue;

            List<Group> groups = new ArrayList<>();
            for (Map.Entry<Ingredient, Integer> entry : groupIngredients(holder.value()).entrySet()) {
                Set<Item> items = new HashSet<>();
                for (Holder<Item> item : IngredientUtil.items(entry.getKey())) {
                    items.add(item.value());
                }
                groups.add(new Group(entry.getKey(), entry.getValue(), items));
            }

            RecipeInfo info = new RecipeInfo(holder, List.copyOf(groups), result);
            newInfos.add(info);
            newById.put(holder.id().identifier(), info);
            newProducers.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(info);
        }

        allInfos = List.copyOf(newInfos);
        infoById = newById;
        producers = newProducers;
        candidateCache = new HashMap<>();
        indexedMap = recipeMap;
    }

    private static ItemStack sampleResult(CraftingRecipe recipe) {
        try {
            NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
            List<Ingredient> ingredients = recipe.placementInfo().ingredients();
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    grid.set(i, IngredientUtil.firstItem(ingredient));
                }
            }
            return recipe.assemble(CraftingInput.of(3, 3, grid));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
