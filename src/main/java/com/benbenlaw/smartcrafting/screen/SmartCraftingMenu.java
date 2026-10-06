package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.compat.FTBChunksCompat;
import com.benbenlaw.smartcrafting.config.SmartCraftingConfig;
import com.benbenlaw.smartcrafting.crafting.RecursivePlanner;
import com.benbenlaw.smartcrafting.networking.packets.SyncFavoriteRecipesClient;
import com.benbenlaw.smartcrafting.networking.packets.SyncRecursiveClient;
import com.benbenlaw.smartcrafting.networking.packets.SyncSortTypeClient;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipePayload;
import com.benbenlaw.smartcrafting.util.PlayerData;
import com.benbenlaw.smartcrafting.util.SmartCraftingTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

public class SmartCraftingMenu extends AbstractContainerMenu {

    protected Level level;
    protected ContainerData data;
    protected Player player;
    protected BlockPos blockPos;
    public String sortType;
    public int handlers;
    private RefreshJob refreshJob;

    public SmartCraftingMenu(int containerID, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerID, inventory, extraData.readBlockPos(), new SimpleContainerData(2));
    }

    public SmartCraftingMenu(int containerID, Inventory inventory, BlockPos blockPos, ContainerData data) {
        super(SmartCraftingMenus.SMART_CRAFTING_MENU.get(), containerID);
        this.player = inventory.player;
        this.blockPos = blockPos;
        this.level = inventory.player.level();
        this.data = data;
        this.sortType = PlayerData.getSortType(player);

        if (!level.isClientSide()) {
            ServerPlayer serverPlayer = (ServerPlayer) inventory.player;
            PacketDistributor.sendToPlayer(serverPlayer, new SyncSortTypeClient(sortType));
            PacketDistributor.sendToPlayer(serverPlayer, new SyncRecursiveClient(PlayerData.isRecursive(player)));
            PacketDistributor.sendToPlayer(serverPlayer, new SyncFavoriteRecipesClient(new ArrayList<>(PlayerData.getFavorites(player))));
            updateValidRecipes();
        }

        checkContainerSize(inventory, 2);
        addPlayerInventory(inventory);
        addPlayerHotbar(inventory);
        addDataSlots(data);
    }

    /** Starts working out the craftable recipes, the results are sent to the client a few at a time. */
    public void updateValidRecipes() {
        if (level.isClientSide()) return;

        refreshJob = new RefreshJob((ServerLevel) level);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (refreshJob != null && player instanceof ServerPlayer serverPlayer) {
            boolean finished = refreshJob.run(System.nanoTime() + REFRESH_BUDGET_NANOS);
            refreshJob.flush(serverPlayer, finished);
            if (finished) {
                refreshJob = null;
            }
        }
    }

    private static final long REFRESH_BUDGET_NANOS = 4_000_000L;

    private class RefreshJob {
        private static final int SETUP = 0, CRAFTING = 1, STONECUTTING = 2, SMITHING = 3, REACHABLE = 4, RECURSIVE = 5, DONE = 6;

        private final ServerLevel serverLevel;
        private final boolean recursive = isRecursiveEnabled();
        private final int maxDepth = SmartCraftingConfig.recursiveCraftingMaxDepth.get();

        private int phase = SETUP;
        private int index = 0;

        private Container inv;
        private List<ItemStack> inventoryStacks;
        private Set<Item> present;
        private List<RecursivePlanner.RecipeInfo> infos;
        private List<RecipeHolder<StonecutterRecipe>> stonecutterRecipes;
        private List<RecipeHolder<SmithingRecipe>> smithingRecipes;
        private RecursivePlanner.ReachabilityJob reachability;
        private final Set<Identifier> directlyCraftable = new HashSet<>();

        private boolean sentFirst = false;
        private final List<Identifier> outIds = new ArrayList<>();
        private final List<ItemStack> outBases = new ArrayList<>();
        private final List<List<ItemStack>> outIntermediates = new ArrayList<>();

        RefreshJob(ServerLevel serverLevel) {
            this.serverLevel = serverLevel;
        }

        private void add(Identifier id, ItemStack base, List<ItemStack> intermediates) {
            outIds.add(id);
            outBases.add(base);
            outIntermediates.add(intermediates);
        }

        /** @return true when everything has been checked */
        boolean run(long deadline) {
            while (phase != DONE && System.nanoTime() < deadline) {
                switch (phase) {
                    case SETUP -> {
                        invalidateHandlerCache();
                        inv = buildCombinedInventory();
                        inventoryStacks = containerStacks(inv);
                        present = new HashSet<>();
                        for (ItemStack stack : inventoryStacks) {
                            present.add(stack.getItem());
                        }
                        infos = RecursivePlanner.infos(serverLevel);
                        RecipeMap recipeMap = serverLevel.recipeAccess().recipeMap();
                        stonecutterRecipes = isStonecutterNearby(player) ? new ArrayList<>(recipeMap.byType(RecipeType.STONECUTTING)) : List.of();
                        smithingRecipes = isSmithingTableNearby(player) ? new ArrayList<>(recipeMap.byType(RecipeType.SMITHING)) : List.of();
                        phase = CRAFTING;
                    }
                    case CRAFTING -> {
                        if (index >= infos.size()) {
                            next(STONECUTTING);
                            break;
                        }
                        RecursivePlanner.RecipeInfo info = infos.get(index++);
                        if (info.possibleWith(present) && canCraftFromInventory(info.holder().value(), inv)) {
                            Identifier id = info.holder().id().identifier();
                            directlyCraftable.add(id);
                            add(id, ItemStack.EMPTY, List.of());
                        }
                    }
                    case STONECUTTING -> {
                        if (index >= stonecutterRecipes.size()) {
                            next(SMITHING);
                            break;
                        }
                        RecipeHolder<StonecutterRecipe> holder = stonecutterRecipes.get(index++);
                        if (recipeHasMatchingIngredients(holder.value(), inv) && canCraftStonecutterFromInventory(holder.value(), inv)) {
                            add(holder.id().identifier(), ItemStack.EMPTY, List.of());
                        }
                    }
                    case SMITHING -> {
                        if (index >= smithingRecipes.size()) {
                            if (recursive) {
                                reachability = new RecursivePlanner.ReachabilityJob(serverLevel, present, maxDepth);
                                next(REACHABLE);
                            } else {
                                next(DONE);
                            }
                            break;
                        }
                        RecipeHolder<SmithingRecipe> holder = smithingRecipes.get(index++);
                        SmithingRecipeInput input = buildSmithingInput(holder.value(), inv);
                        if (input != null && holder.value().matches(input, level) && !holder.value().assemble(input).isEmpty()) {
                            add(holder.id().identifier(), input.base(), List.of());
                        }
                    }
                    case REACHABLE -> {
                        if (reachability.step(deadline)) {
                            next(RECURSIVE);
                        }
                    }
                    case RECURSIVE -> {
                        if (index >= infos.size()) {
                            next(DONE);
                            break;
                        }
                        RecursivePlanner.RecipeInfo info = infos.get(index++);
                        Identifier id = info.holder().id().identifier();
                        if (!directlyCraftable.contains(id) && info.possibleWith(reachability.result())) {
                            RecursivePlanner.Plan plan = RecursivePlanner.plan(serverLevel, info, inventoryStacks, maxDepth);
                            if (plan != null && !plan.steps().isEmpty()) {
                                add(id, ItemStack.EMPTY, plan.intermediates());
                            }
                        }
                    }
                    default -> phase = DONE;
                }
            }
            return phase == DONE;
        }

        private void next(int nextPhase) {
            phase = nextPhase;
            index = 0;
        }

        /** Sends what has been found since the last flush, the first batch replaces the client's list. */
        void flush(ServerPlayer serverPlayer, boolean finished) {
            if (sentFirst && outIds.isEmpty() && !finished) return;

            PacketDistributor.sendToPlayer(serverPlayer, new SmartCraftingRecipePayload(
                    List.copyOf(outIds), List.copyOf(outBases), List.copyOf(outIntermediates), !sentFirst, finished));
            sentFirst = true;
            outIds.clear();
            outBases.clear();
            outIntermediates.clear();
        }
    }

    private List<ResourceHandler<ItemResource>> handlerCache;

    private void invalidateHandlerCache() {
        handlerCache = null;
    }

    private List<ResourceHandler<ItemResource>> findConnectedItemHandlers() {
        if (handlerCache != null) return handlerCache;

        List<ResourceHandler<ItemResource>> itemHandlers = new ArrayList<>();
        final int radius = SmartCraftingConfig.storageRangeCheck.get();

        BlockPos.betweenClosedStream(
                blockPos.offset(-radius, -radius / 2, -radius),
                blockPos.offset(radius, radius / 2, radius)
        ).forEach(mutablePos -> {
            BlockPos pos = mutablePos.immutable();
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && (level.getBlockState(pos).is(SmartCraftingTags.Blocks.WHITELISTED_STORAGE) || isAllowedViaConfig(level.getBlockState(pos).getBlock())) && canAccessBlock(pos)) {

                if (be instanceof ChestBlockEntity chest) {

                    ChestType type = chest.getBlockState().getValue(ChestBlock.TYPE);
                    if (type != ChestType.SINGLE) {
                        Direction dir = ChestBlock.getConnectedDirection(chest.getBlockState());
                        BlockPos otherPos = pos.relative(dir);
                        if (otherPos.compareTo(pos) < 0) {
                            return;
                        }
                    }
                }

                ResourceHandler<ItemResource> handler = Capabilities.Item.BLOCK.getCapability(level, pos, level.getBlockState(pos), be, null);
                if (handler != null) {
                    itemHandlers.add(handler);
                }
            }
        });

        handlers = itemHandlers.size();
        handlerCache = itemHandlers;

        return itemHandlers;
    }

    private static boolean isAllowedViaConfig(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);

        for (String id : SmartCraftingConfig.validStorageBlocks.get()) {
            if (blockId.toString().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean canAccessBlock(BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return FTBChunksCompat.canAccess(serverPlayer, pos);
    }

    private Container buildCombinedInventory() {
        List<ItemStack> combinedStacks = new ArrayList<>();

        //Player Inventory
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
                combinedStacks.add(stack.copy());
            }
        }

        //Nearby Storage
        for (ResourceHandler<ItemResource> handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.size(); i++) {
                ItemResource resource = handler.getResource(i);
                int amount = handler.getAmountAsInt(i);
                if (!resource.isEmpty() && amount > 0) {
                    combinedStacks.add(resource.toStack(amount));
                }
            }
        }

        SimpleContainer combined = new SimpleContainer(combinedStacks.size());
        for (int i = 0; i < combinedStacks.size(); i++) {
            combined.setItem(i, combinedStacks.get(i));
        }

        return combined;
    }

    private boolean consumeIngredientFromAll(Predicate<ItemStack> ingredient, int count) {
        int remaining = count;

        // Player Inventory first
        Inventory playerInventory = player.getInventory();
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (!stack.isEmpty() && ingredient.test(stack)) {
                int toTake = Math.min(stack.getCount(), remaining);
                stack.shrink(toTake);
                if (stack.isEmpty()) {
                    playerInventory.setItem(i, ItemStack.EMPTY);
                }
                remaining -= toTake;
                if (remaining <= 0) return true;
            }
        }

        // Nearby Storage
        for (ResourceHandler<ItemResource> handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.size(); i++) {
                ItemResource resource = handler.getResource(i);
                if (resource.isEmpty() || !ingredient.test(resource.toStack(1))) continue;

                int toTake = Math.min(handler.getAmountAsInt(i), remaining);
                if (toTake <= 0) continue;

                try (Transaction transaction = Transaction.openRoot()) {
                    int extracted = handler.extract(i, resource, toTake, transaction);
                    if (extracted > 0) {
                        transaction.commit();
                        remaining -= extracted;
                    }
                }
                if (remaining <= 0) return true;
            }
        }

        return remaining <= 0;
    }

    private boolean isStonecutterNearby(Player player) {
        return isBlockNearby(player, Blocks.STONECUTTER, SmartCraftingConfig.stonecutterRangeCheck.get());
    }

    private boolean isSmithingTableNearby(Player player) {
        return isBlockNearby(player, Blocks.SMITHING_TABLE, SmartCraftingConfig.smithingTableRangeCheck.get());
    }

    private boolean isBlockNearby(Player player, Block block, int radius) {
        BlockPos playerPos = player.blockPosition();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = playerPos.offset(dx, dy, dz);
                    if (level.getBlockState(checkPos).is(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean canCraftFromInventory(CraftingRecipe recipe, Container inv) {
        CraftingInput input = buildCraftingInputForRecipe(recipe, inv);
        return recipe.matches(input, level);
    }

    private ItemStack findStackFor(Ingredient ingredient, Container inv, int[] usedSlots) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && usedSlots[i] < stack.getCount() && ingredient.test(stack)) {
                usedSlots[i]++;
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private SmithingRecipeInput buildSmithingInput(SmithingRecipe recipe, Container inv) {
        int[] usedSlots = new int[inv.getContainerSize()];
        ItemStack template = ItemStack.EMPTY;
        ItemStack addition = ItemStack.EMPTY;

        if (recipe.templateIngredient().isPresent()) {
            template = findStackFor(recipe.templateIngredient().get(), inv, usedSlots);
            if (template.isEmpty()) return null;
        }

        ItemStack base = findStackFor(recipe.baseIngredient(), inv, usedSlots);
        if (base.isEmpty()) return null;

        if (recipe.additionIngredient().isPresent()) {
            addition = findStackFor(recipe.additionIngredient().get(), inv, usedSlots);
            if (addition.isEmpty()) return null;
        }

        return new SmithingRecipeInput(template, base, addition);
    }

    private boolean canCraftSmithingFromInventory(SmithingRecipe recipe, Container inv) {
        SmithingRecipeInput input = buildSmithingInput(recipe, inv);
        return input != null && recipe.matches(input, level) && !recipe.assemble(input).isEmpty();
    }

    private boolean consumeExact(ItemStack sample) {
        return !sample.isEmpty() && consumeIngredientFromAll(stack -> ItemStack.isSameItemSameComponents(stack, sample), 1);
    }

    private void playCraftSound(SoundEvent sound, float volume) {
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, 1.0F);
    }

    private boolean canCraftStonecutterFromInventory(StonecutterRecipe recipe, Container inv) {
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            boolean found = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private CraftingInput buildCraftingInputForRecipe(CraftingRecipe recipe, Container inv) {
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        List<Ingredient> ingredients = recipe.placementInfo().ingredients();
        int[] usedSlots = new int[inv.getContainerSize()];

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            // placementInfo drops the empty slots of a shaped recipe, so use the real pattern
            List<Optional<Ingredient>> pattern = shaped.getIngredients();

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int recipeIndex = row * width + col;
                    int gridIndex = row * 3 + col;

                    if (recipeIndex >= pattern.size() || pattern.get(recipeIndex).isEmpty()) continue;
                    Ingredient ing = pattern.get(recipeIndex).get();
                    if (ing.isEmpty()) continue;

                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack stack = inv.getItem(i);
                        if (stack.isEmpty() || usedSlots[i] >= stack.getCount()) continue;

                        if (ing.test(stack)) {
                            ItemStack copy = stack.copy();
                            copy.setCount(1);
                            grid.set(gridIndex, copy);
                            usedSlots[i]++;
                            break;
                        }
                    }
                }
            }
        } else {
            int placed = 0;
            for (Ingredient ing : ingredients) {
                if (ing.isEmpty()) continue;

                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (stack.isEmpty() || usedSlots[i] >= stack.getCount()) continue;

                    if (ing.test(stack)) {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        grid.set(placed, copy);
                        usedSlots[i]++;
                        placed++;
                        break;
                    }
                }
            }
        }

        return CraftingInput.ofPositioned(3, 3, grid).input();
    }

    public void craftRecipeById(Identifier recipeId, boolean shiftClick) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        invalidateHandlerCache();

        Optional<RecipeHolder<?>> optionalRecipe = serverLevel.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, recipeId));
        if (optionalRecipe.isEmpty()) return;

        Recipe<?> recipe = optionalRecipe.get().value();

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            boolean recursive = isRecursiveEnabled();
            int maxCrafts = shiftClick ? (recursive ? 64 : getMaxCraftableAmount(craftingRecipe)) : 1;
            ItemStack lastResult = ItemStack.EMPTY;

            for (int i = 0; i < maxCrafts; i++) {
                if (!lastResult.isEmpty() && getFreeSpaceForItem(lastResult) < lastResult.getCount()) break;

                ItemStack made = craftOnce(craftingRecipe);
                if (made.isEmpty() && recursive) {
                    if (!craftPrerequisites(serverLevel, optionalRecipe.get().id().identifier())) break;
                    made = craftOnce(craftingRecipe);
                }
                if (made.isEmpty()) break;
                lastResult = made;
            }

            playCraftSound(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0F);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
            return;
        }

        if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
            int maxCrafts = shiftClick ? getMaxCraftableAmountStonecutter(stonecutterRecipe) : 1;

            for (int i = 0; i < maxCrafts; i++) {
                Container combinedInv = buildCombinedInventory();
                if (!canCraftStonecutterFromInventory(stonecutterRecipe, combinedInv)) break;

                ItemStack resultToGive = stonecutterRecipe.assemble(new SingleRecipeInput(ItemStack.EMPTY)).copy();

                if (!player.getInventory().add(resultToGive)) {
                    player.drop(resultToGive, false);
                }

                if (!consumeIngredientFromAll(stonecutterRecipe.input(), 1)) break;
            }

            playCraftSound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.0F);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
            return;
        }

        if (recipe instanceof SmithingRecipe smithingRecipe) {
            int maxCrafts = shiftClick ? 64 : 1;
            int crafted = 0;

            for (int i = 0; i < maxCrafts; i++) {
                Container combinedInv = buildCombinedInventory();
                SmithingRecipeInput input = buildSmithingInput(smithingRecipe, combinedInv);
                if (input == null || !smithingRecipe.matches(input, level)) break;

                ItemStack result = smithingRecipe.assemble(input).copy();
                if (result.isEmpty()) break;
                if (i > 0 && getFreeSpaceForItem(result) < result.getCount()) break;

                if (!consumeExact(input.template()) && !input.template().isEmpty()) break;
                if (!consumeExact(input.base())) break;
                if (!consumeExact(input.addition()) && !input.addition().isEmpty()) break;

                if (!player.getInventory().add(result)) {
                    player.drop(result, false);
                }
                crafted++;
            }

            if (crafted > 0) {
                playCraftSound(SoundEvents.ANVIL_USE, 0.6F);
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            updateValidRecipes();
        }
    }

    private boolean isRecursiveEnabled() {
        return SmartCraftingConfig.recursiveCraftingEnabled.get() && PlayerData.isRecursive(player);
    }

    private static List<ItemStack> containerStacks(Container container) {
        List<ItemStack> stacks = new ArrayList<>(container.getContainerSize());
        for (int i = 0; i < container.getContainerSize(); i++) {
            stacks.add(container.getItem(i));
        }
        return stacks;
    }

    /** Crafts the recipe once from the player's items and nearby storage, returns the result or empty if it failed. */
    private ItemStack craftOnce(CraftingRecipe craftingRecipe) {
        Container combinedInv = buildCombinedInventory();
        CraftingInput input = buildCraftingInputForRecipe(craftingRecipe, combinedInv);
        if (!craftingRecipe.matches(input, level)) return ItemStack.EMPTY;

        ItemStack result = craftingRecipe.assemble(input).copy();
        if (result.isEmpty()) return ItemStack.EMPTY;

        for (Map.Entry<Ingredient, Integer> entry : RecursivePlanner.groupIngredients(craftingRecipe).entrySet()) {
            if (!consumeIngredientFromAll(entry.getKey(), entry.getValue())) {
                return ItemStack.EMPTY;
            }
        }

        if (!player.getInventory().add(result.copy())) {
            player.drop(result.copy(), false);
        }

        for (ItemStack remainder : craftingRecipe.getRemainingItems(input)) {
            if (!remainder.isEmpty() && !player.getInventory().add(remainder)) {
                player.drop(remainder, false);
            }
        }

        return result;
    }

    /** Crafts the intermediate items (eg logs -> planks) that the recipe needs. */
    private boolean craftPrerequisites(ServerLevel serverLevel, Identifier recipeId) {
        RecursivePlanner.RecipeInfo info = RecursivePlanner.info(serverLevel, recipeId);
        if (info == null) return false;

        RecursivePlanner.Plan plan = RecursivePlanner.plan(serverLevel, info, containerStacks(buildCombinedInventory()),
                SmartCraftingConfig.recursiveCraftingMaxDepth.get());
        if (plan == null) return false;

        for (RecursivePlanner.Step step : plan.steps()) {
            for (int i = 0; i < step.crafts(); i++) {
                if (craftOnce(step.holder().value()).isEmpty()) return false;
            }
        }
        return true;
    }

    private boolean recipeHasMatchingIngredients(Recipe<?> recipe, Container inv) {
        if (!(recipe instanceof CraftingRecipe) && !(recipe instanceof StonecutterRecipe)) return false;

        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (ingredient.isEmpty()) continue;
            boolean found = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private int getMaxCraftableAmount(CraftingRecipe recipe) {
        Container inv = buildCombinedInventory();

        Map<Ingredient, Integer> ingredientCounts = new HashMap<>();
        for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
            if (!ingredient.isEmpty()) {
                ingredientCounts.merge(ingredient, 1, Integer::sum);
            }
        }
        if (ingredientCounts.isEmpty()) return 0;

        int max = Integer.MAX_VALUE;
        for (Map.Entry<Ingredient, Integer> entry : ingredientCounts.entrySet()) {
            int count = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty() && entry.getKey().test(stack)) {
                    count += stack.getCount();
                }
            }
            max = Math.min(max, count / entry.getValue());
        }

        max = Math.clamp(max, 0, 64);
        if (max <= 0) return 0;

        CraftingInput input = buildCraftingInputForRecipe(recipe, inv);
        if (recipe.matches(input, level)) {
            ItemStack sampleResult = recipe.assemble(input);
            if (!sampleResult.isEmpty()) {
                int resultCountPerCraft = Math.max(1, sampleResult.getCount());
                max = Math.min(max, getFreeSpaceForItem(sampleResult) / resultCountPerCraft);
            }
        }

        return Math.max(0, max);
    }

    private int getFreeSpaceForItem(ItemStack sample) {
        int space = 0;
        int maxStackSize = sample.getMaxStackSize();

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                space += maxStackSize;
            } else if (ItemStack.isSameItemSameComponents(stack, sample)) {
                space += Math.max(0, maxStackSize - stack.getCount());
            }
        }

        return space;
    }

    private int getMaxCraftableAmountStonecutter(StonecutterRecipe recipe) {
        Ingredient ingredient = recipe.input();
        if (ingredient.isEmpty()) return 0;

        int count = 0;

        for (ResourceHandler<ItemResource> handler : findConnectedItemHandlers()) {
            for (int i = 0; i < handler.size(); i++) {
                ItemResource resource = handler.getResource(i);
                if (!resource.isEmpty() && ingredient.test(resource.toStack(1))) {
                    count += handler.getAmountAsInt(i);
                }
            }
        }

        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                count += stack.getCount();
            }
        }

        int max = Math.clamp(count, 0, 64);
        if (max <= 0) return 0;

        ItemStack sampleResult = recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
        if (!sampleResult.isEmpty()) {
            int resultCountPerCraft = Math.max(1, sampleResult.getCount());
            max = Math.min(max, getFreeSpaceForItem(sampleResult) / resultCountPerCraft);
        }

        return Math.max(0, max);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 98 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 156));
        }
    }
}
