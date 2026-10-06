package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.smartcrafting.util.MouseUtil;
import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.compat.JeiCompat;
import com.benbenlaw.smartcrafting.config.SmartCraftingConfig;
import com.benbenlaw.smartcrafting.event.VanillaRecipeCache;
import com.benbenlaw.smartcrafting.networking.packets.SyncFavoriteRecipes;
import com.benbenlaw.smartcrafting.networking.packets.SyncRecursive;
import com.benbenlaw.smartcrafting.networking.packets.SyncSortType;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import com.benbenlaw.smartcrafting.util.ClientSettings;
import com.benbenlaw.smartcrafting.util.IngredientUtil;
import com.benbenlaw.smartcrafting.util.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class SmartCraftingScreen extends AbstractContainerScreen<SmartCraftingMenu> {
    private static final Identifier TEXTURE = SmartCrafting.identifier("textures/gui/smart_crafting_table.png");

    private static final int TOOLTIP_SIZE = 62;

    private static final Identifier CRAFTING_TOOLTIP_TEXTURE = SmartCrafting.identifier("smart_crafting_table_render");
    private static final Identifier SMITHING_TOOLTIP_TEXTURE = SmartCrafting.identifier("smart_crafting_table_smithing_render");
    private static final Identifier STONECUTTER_TOOLTIP_TEXTURE = SmartCrafting.identifier("smart_crafting_table_stonecutter_render");
    static final Identifier SCROLL_SPRITE = SmartCrafting.identifier("textures/gui/scroll.png");
    static final Identifier SORT_NAME_ICON = SmartCrafting.identifier("textures/gui/button_sort_name.png");
    static final Identifier SORT_MOD_ICON = SmartCrafting.identifier("textures/gui/button_sort_mod.png");
    static final Identifier RECURSIVE_ICON = SmartCrafting.identifier("textures/gui/button_recursive.png");
    static final Identifier JEI_ICON = SmartCrafting.identifier("textures/gui/button_jei.png");

    private static final int BUTTON_SIZE = 18;
    private static final int BUTTON_X_OFFSET = 175;
    private static final int BUTTON_Y_OFFSET = 31;
    private static final int BUTTON_SPACING = 20;

    private enum Option {
        CRAFTING, STONECUTTING, SMITHING, RECURSIVE, JEI, SORT
    }
    static final Identifier STAR_ICON = SmartCrafting.identifier("textures/gui/favorite.png");

    private List<Recipe<?>> clientRecipes = Collections.emptyList();
    private List<RecipeHolder<?>> clientRecipeHolders = Collections.emptyList();

    private ItemStack getRecipeResult(RecipeHolder<?> holder) {
        if (holder.value() instanceof SmithingRecipe smithing) {
            ItemStack template = smithing.templateIngredient().map(this::firstItem).orElse(ItemStack.EMPTY);
            ItemStack base = smithingBase(holder);
            ItemStack addition = smithing.additionIngredient().map(this::firstItem).orElse(ItemStack.EMPTY);
            if (base.isEmpty()) return ItemStack.EMPTY;
            return smithing.assemble(new SmithingRecipeInput(template, base, addition));
        }
        return getRecipeResult(holder.value());
    }

    private ItemStack smithingBase(RecipeHolder<?> holder) {
        ItemStack base = VanillaRecipeCache.smithingBases.get(holder.id().identifier());
        if (base != null && !base.isEmpty()) return base;
        return holder.value() instanceof SmithingRecipe smithing ? firstItem(smithing.baseIngredient()) : ItemStack.EMPTY;
    }

    private ItemStack getRecipeResult(Recipe<?> recipe) {

        if (recipe instanceof CraftingRecipe craftingRecipe) {

            NonNullList<ItemStack> dummyGrid = NonNullList.withSize(9, ItemStack.EMPTY);
            List<Ingredient> ingredients = craftingRecipe.placementInfo().ingredients();
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    dummyGrid.set(i, firstItem(ingredient));
                }
            }

            CraftingInput input = CraftingInput.of(3, 3, dummyGrid);
            return craftingRecipe.assemble(input);

        }

        else if (recipe instanceof SingleItemRecipe single) {
            // Stonecutter / single-item recipes
            Ingredient ingredient = single.placementInfo().ingredients().getFirst();
            ItemStack dummyInput = ItemStack.EMPTY;

            if (ingredient != null) {
                dummyInput = firstItem(ingredient);
            }

            SingleRecipeInput input = new SingleRecipeInput(dummyInput);
            return single.assemble(input);
        }

        else if (recipe instanceof SmithingRecipe smithing) {
            ItemStack template = smithing.templateIngredient().map(this::firstItem).orElse(ItemStack.EMPTY);
            ItemStack base = firstItem(smithing.baseIngredient());
            ItemStack addition = smithing.additionIngredient().map(this::firstItem).orElse(ItemStack.EMPTY);
            if (base.isEmpty()) return ItemStack.EMPTY;
            return smithing.assemble(new SmithingRecipeInput(template, base, addition));
        }

        return ItemStack.EMPTY;
    }

    private ItemStack firstItem(Ingredient ingredient) {
        return IngredientUtil.firstItem(ingredient);
    }

    public void setClientRecipes(Collection<? extends RecipeHolder<? extends Recipe<?>>> recipes) {
        // Keep holders
        this.clientRecipeHolders = recipes.stream()
                .filter(r -> !getRecipeResult(r).isEmpty())
                .sorted(Comparator.comparing(r -> r.id().identifier().getNamespace())) // sort by mod ID
                .collect(Collectors.toList());

        // Extract underlying Recipe<?> for display
        this.clientRecipes = clientRecipeHolders.stream()
                .map(RecipeHolder::value)
                .collect(Collectors.toList());

        updateFilteredRecipes();
        moveSelectedRecipeToFront();
        scrollOffset = 0;
    }

    private List<RecipeHolder<?>> stagedRecipes = new ArrayList<>();
    private boolean staging = false;

    /**
     * Handles a batch from the server. On first open the list fills in as batches arrive. When a list is already
     * showing (eg after crafting) the new one is built up quietly and swapped in once complete, so recipes
     * don't shift around under the cursor while the server is still working.
     */
    public void receiveRecipes(Collection<? extends RecipeHolder<? extends Recipe<?>>> recipes, boolean reset, boolean last) {
        if (reset) {
            staging = !clientRecipeHolders.isEmpty();
            stagedRecipes = new ArrayList<>();
        }

        if (!staging) {
            if (reset) {
                setClientRecipes(recipes);
            } else {
                addClientRecipes(recipes);
            }
            return;
        }

        recipes.stream()
                .filter(r -> !getRecipeResult(r).isEmpty())
                .forEach(stagedRecipes::add);

        if (last) {
            stagedRecipes.sort(Comparator.comparing(r -> r.id().identifier().getNamespace()));
            this.clientRecipeHolders = stagedRecipes;
            stagedRecipes = new ArrayList<>();
            staging = false;
            updateFilteredRecipes();
        }
    }

    /** Adds a batch of recipes to the list without resetting the scroll position. */
    public void addClientRecipes(Collection<? extends RecipeHolder<? extends Recipe<?>>> recipes) {
        List<RecipeHolder<?>> combined = new ArrayList<>(clientRecipeHolders);
        recipes.stream()
                .filter(r -> !getRecipeResult(r).isEmpty())
                .forEach(combined::add);
        combined.sort(Comparator.comparing(r -> r.id().identifier().getNamespace()));

        this.clientRecipeHolders = combined;
        updateFilteredRecipes();
    }

    public void toggleFavorite(Identifier recipeId) {
        Set<String> favorites = PlayerData.getFavorites(menu.player);

        if (!favorites.remove(recipeId.toString())) {
            favorites.add(recipeId.toString());
        }

        PlayerData.setFavorites(menu.player, favorites);
        ClientPacketDistributor.sendToServer(new SyncFavoriteRecipes(new ArrayList<>(favorites)));
    }

    // Slot size and layout
    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 17;
    private static final int VISIBLE_ROWS = 3;
    private static final int VISIBLE_COLS = 8;

    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 54;
    private static final int SCROLLBAR_X_OFFSET = 154;
    private static final int SCROLLBAR_Y_OFFSET = 29;

    private int hoveredRecipeIndex = -1;
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private Identifier selectedRecipeId = null;

    private static final int COLUMNS = 8;
    public static int visibleRows = 3;
    private final int itemsPerPage = COLUMNS * visibleRows;

    private EditBox searchBox;
    private String lastSearchText = "";
    private List<RecipeHolder<?>> filteredRecipes = Collections.emptyList();
    private int dragOffsetY = 0;

    private SortType currentSortType = SortType.NAME;

    public static List<RecipeHolder<? extends Recipe<?>>> pendingRecipes = null;

    public SmartCraftingScreen(SmartCraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, 180);
    }

    @Override
    protected void init() {
        super.init();

        if (pendingRecipes != null) {
            setClientRecipes(pendingRecipes);
            pendingRecipes = null;
        }

        if (PlayerData.getSortType(this.menu.player).equals("mod")) {
            currentSortType = SortType.MOD;
        } else {
            currentSortType = SortType.NAME;

        }

        int x = (width - imageWidth) / 2 + 8;
        int y = (height - imageHeight) / 2 + 16; // below the title

        searchBox = new EditBox(font, x, y, 141, 12, Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchTextChanged);
        searchBox.setFocused(true);
        searchBox.setBordered(true);
        searchBox.setVisible(true);

        addRenderableWidget(searchBox);

        updateFilteredRecipes();
    }

    private List<RecipeHolder<?>> enabledRecipes() {
        return clientRecipeHolders.stream().filter(this::isTypeEnabled).toList();
    }

    private boolean isTypeEnabled(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        if (recipe instanceof CraftingRecipe) return ClientSettings.showCrafting;
        if (recipe instanceof StonecutterRecipe) return ClientSettings.showStonecutting;
        if (recipe instanceof SmithingRecipe) return ClientSettings.showSmithing;
        return true;
    }

    private void onSearchTextChanged(String text) {
        lastSearchText = text.toLowerCase();
        updateFilteredRecipes();

        if (ClientSettings.jeiSync && JeiCompat.isAvailable() && !JeiCompat.getFilterText().equals(text)) {
            JeiCompat.setFilterText(text);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (ClientSettings.jeiSync && JeiCompat.isAvailable()) {
            String jeiText = JeiCompat.getFilterText();
            if (!jeiText.equals(searchBox.getValue())) {
                searchBox.setValue(jeiText);
            }
        }
    }

    private void updateFilteredRecipes() {
        // Filter by search text
        if (lastSearchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(enabledRecipes()); // keep holders
        } else if (lastSearchText.startsWith("@")) {
            String modID = lastSearchText.substring(1);
            filteredRecipes = enabledRecipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().toLowerCase(Locale.ROOT).contains(modID))
                    .toList();
        } else {
            filteredRecipes = enabledRecipes().stream()
                    .filter(holder -> {
                        ItemStack result = getRecipeResult(holder);
                        String name = result.getHoverName().getString();
                        return name.toLowerCase(Locale.ROOT).contains(lastSearchText);
                    })
                    .toList();
        }

        // Apply sorting
        if (currentSortType == SortType.MOD) {
            filteredRecipes = filteredRecipes.stream()
                    .sorted(Comparator.comparing(r -> r.id().identifier().getNamespace()))
                    .toList();

            if (selectedRecipeId != null) {
                String selectedModId = selectedRecipeId.getNamespace();

                List<RecipeHolder<?>> selectedModRecipes = new ArrayList<>();
                List<RecipeHolder<?>> otherModRecipes = new ArrayList<>();

                for (RecipeHolder<?> recipe : filteredRecipes) {
                    String recipeModId = recipe.id().identifier().getNamespace();
                    if (recipeModId.equalsIgnoreCase(selectedModId)) {
                        selectedModRecipes.add(recipe);
                    } else {
                        otherModRecipes.add(recipe);
                    }
                }

                List<RecipeHolder<?>> combined = new ArrayList<>();
                combined.addAll(selectedModRecipes);
                combined.addAll(otherModRecipes);

                filteredRecipes = combined;
            }
        } else if (currentSortType == SortType.NAME) {
            filteredRecipes = filteredRecipes.stream()
                    .sorted(Comparator.comparing(recipe -> {
                        ItemStack result = getRecipeResult(recipe);
                        return result.getHoverName().getString().toLowerCase(Locale.ROOT);
                    }))
                    .toList();
        }

        // Sort with selected recipe at top, then favorites, then others
        Set<String> favoriteIds = PlayerData.getFavorites(menu.player);

        RecipeHolder<?> selectedRecipe = null;
        List<RecipeHolder<?>> favoriteRecipes = new ArrayList<>();
        List<RecipeHolder<?>> otherRecipes = new ArrayList<>();

        for (RecipeHolder<?> recipe : filteredRecipes) {
            String idStr = recipe.id().identifier().toString();
            if (selectedRecipeId != null && recipe.id().identifier().equals(selectedRecipeId)) {
                selectedRecipe = recipe;
            } else if (favoriteIds.contains(idStr)) {
                favoriteRecipes.add(recipe);
            } else {
                otherRecipes.add(recipe);
            }
        }

        // Final assembly: selected -> favorites -> others
        filteredRecipes = new ArrayList<>();
        if (selectedRecipe != null) {
            filteredRecipes.add(selectedRecipe);
        }
        filteredRecipes.addAll(favoriteRecipes);
        filteredRecipes.addAll(otherRecipes);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return searchBox.charTyped(event) || super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event);
        }

        if (searchBox.isFocused()) {
            if (searchBox.keyPressed(event) || searchBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(event);
    }


    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {

        extractRecipeIngredients(guiGraphics, mouseX, mouseY);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);

        extractRecipeIcons(guiGraphics, mouseX, mouseY);

        extractOptionButtons(guiGraphics, mouseX, mouseY);
    }

    private void extractRecipeIcons(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        List<RecipeHolder<?>> recipes = this.filteredRecipes;

        int xStart = (width - imageWidth) / 2 + 11;
        int yStart = (height - imageHeight) / 2 + 31;

        int maxRows = (int) Math.ceil(recipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(scrollOffset, 0);
        hoveredRecipeIndex = -1;

        // Load favorites from player data
        Set<String> favoriteIds = PlayerData.getFavorites(menu.player);

        for (int i = 0; i < recipes.size(); i++) {
            int row = i / VISIBLE_COLS;
            int col = i % VISIBLE_COLS;

            if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                continue;
            }

            RecipeHolder<?> recipe = recipes.get(i);
            assert Minecraft.getInstance().level != null;

            ItemStack resultStack = getRecipeResult(recipe);

            int iconX = xStart + col * ICON_SPACING;
            int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

            // Highlight selected recipe
            if (recipe.id().identifier().equals(selectedRecipeId)) {
                guiGraphics.fill(
                        iconX - 1, iconY - 1,
                        iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1,
                        0xAAFFFF00
                );
            }

            List<ItemStack> intermediates = VanillaRecipeCache.intermediates.getOrDefault(recipe.id().identifier(), List.of());

            // Render item and decorations
            guiGraphics.item(resultStack, iconX, iconY);
            guiGraphics.itemDecorations(font, resultStack, iconX, iconY);
            if (!intermediates.isEmpty()) {
                guiGraphics.fill(iconX, iconY, iconX + 4, iconY + 4, 0xFF000000);
                guiGraphics.fill(iconX + 1, iconY + 1, iconX + 3, iconY + 3, 0xFF55FFFF);
            }

            // ✅ Render star on top if favorite
            if (favoriteIds.contains(recipe.id().identifier().toString())) {
                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        STAR_ICON,
                        iconX + ICON_SIZE - 4,
                        iconY,
                        0, 0,
                        4, 4,
                        4, 4
                );
            }

            // Tooltip on hover
            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                    mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                hoveredRecipeIndex = i;

                List<Component> tooltip = new ArrayList<>(getTooltipFromItem(minecraft, resultStack));

                if (minecraft.options.advancedItemTooltips) {
                    tooltip.add(Component.literal("Recipe ID: " + recipe.id().identifier()).withStyle(ChatFormatting.DARK_GRAY));
                }
                if (!intermediates.isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.smartcrafting.requires_crafting").withStyle(ChatFormatting.AQUA));
                    for (ItemStack intermediate : intermediates) {
                        tooltip.add(Component.literal(" ").append(intermediate.getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
                    }
                }
                tooltip.add(Component.translatable("tooltip.smartcrafting.click_craft").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.smartcrafting.shift_craft").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
                tooltip.add(Component.translatable("tooltip.smartcrafting.favorite").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));

                guiGraphics.setTooltipForNextFrame(font, tooltip, resultStack.getTooltipImage(), resultStack, mouseX, mouseY);
            }
        }

        // Draw the scroll bar
        int guiX = (width - imageWidth) / 2;
        int guiY = (height - imageHeight) / 2;


        if (maxScroll > 0) {
            float scrollPercent = scrollOffset / (float) maxScroll;

            int knobHeight = 15;
            int trackHeight = SCROLLBAR_HEIGHT - knobHeight;
            int knobY = (int)(scrollPercent * trackHeight);

            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    SCROLL_SPRITE,
                    guiX + SCROLLBAR_X_OFFSET,
                    guiY + SCROLLBAR_Y_OFFSET + knobY,
                    0, 0,
                    12, knobHeight,
                    12, 15
            );
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            int scrollbarY = topPos + SCROLLBAR_Y_OFFSET;
            int handleHeight = 15;

            int totalRows = (int) Math.ceil((double) filteredRecipes.size() / VISIBLE_COLS);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);

            int relativeY = (int) event.y() - scrollbarY - dragOffsetY;
            float percent = Mth.clamp(relativeY / (float)(SCROLLBAR_HEIGHT - handleHeight), 0.0F, 1.0F);

            // scrollOffset is in rows
            scrollOffset = Mth.clamp(Math.round(percent * maxScroll), 0, maxScroll);
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    private void moveSelectedRecipeToFront() {
        if (selectedRecipeId == null) return;

        int index = -1;
        for (int i = 0; i < filteredRecipes.size(); i++) {
            if (filteredRecipes.get(i).id().identifier().equals(selectedRecipeId)) {
                index = i;
                break;
            }
        }
        if (index > 0) {
            // Move the selected recipe to the front
            List<RecipeHolder<?>> newList = new ArrayList<>(filteredRecipes);
            RecipeHolder<?> selectedRecipe = newList.remove(index);
            newList.add(0, selectedRecipe);
            filteredRecipes = List.copyOf(newList);
        }
    }

    private void extractRecipeIngredients(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int indexToRender = hoveredRecipeIndex;

        // If no recipe is hovered, try to render the selected recipe
        if (indexToRender == -1 && selectedRecipeId != null) {
            // Find the selected recipe index
            for (int i = 0; i < filteredRecipes.size(); i++) {
                if (filteredRecipes.get(i).id().identifier().equals(selectedRecipeId)) {
                    indexToRender = i;
                    break;
                }
            }
        }

        if (indexToRender == -1) return; // Nothing to render

        if (filteredRecipes.isEmpty() || indexToRender < 0 || indexToRender >= filteredRecipes.size()) {
            return;
        }

        RecipeHolder<?> recipeHolder = filteredRecipes.get(indexToRender);
        Recipe<?> recipe = recipeHolder.value();

        List<Ingredient> ingredients = recipe.placementInfo().ingredients();
        int gridSize = 3;
        int iconSize = 18;  // spacing between icons

        int tooltipX = this.leftPos - 58;
        int tooltipY = this.topPos;

        int recipeWidth = 3;
        int recipeHeight = 3;

        int offsetX = 0;
        int offsetY = 0;

        Identifier tooltipTexture;

        if (recipe instanceof ShapedRecipe shaped) {
            recipeWidth = shaped.getWidth();
            recipeHeight = shaped.getHeight();
            offsetX = (gridSize - recipeWidth) / 2;
            offsetY = (gridSize - recipeHeight) / 2;

            tooltipTexture = CRAFTING_TOOLTIP_TEXTURE;
        } else if (recipe instanceof SmithingRecipe smithing) {

            guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SMITHING_TOOLTIP_TEXTURE, 62, 62, 0, 0, tooltipX, tooltipY, 62, 62);

            ItemStack template = smithing.templateIngredient().map(this::previewStack).orElse(ItemStack.EMPTY);
            ItemStack base = smithingBase(recipeHolder);
            ItemStack addition = smithing.additionIngredient().map(this::previewStack).orElse(ItemStack.EMPTY);

            extractPreviewItem(guiGraphics, template, tooltipX + 5, tooltipY + 5, mouseX, mouseY);
            extractPreviewItem(guiGraphics, base, tooltipX + iconSize + 5, tooltipY + 5, mouseX, mouseY);
            extractPreviewItem(guiGraphics, addition, tooltipX + 2 * iconSize + 5, tooltipY + 5, mouseX, mouseY);
            extractPreviewItem(guiGraphics, new ItemStack(Items.SMITHING_TABLE), tooltipX + iconSize + 5, tooltipY + 2 * iconSize + 5, mouseX, mouseY);

            return;
        } else if (recipe instanceof StonecutterRecipe) {

            tooltipTexture = STONECUTTER_TOOLTIP_TEXTURE;

            // Draw stonecutter input and output explicitly
            guiGraphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    tooltipTexture,
                    62,
                    62,
                    0,
                    0,
                    tooltipX,
                    tooltipY,
                    62,
                    62
            );

            ItemStack inputStack = ItemStack.EMPTY;
            if (!ingredients.isEmpty() && !ingredients.getFirst().isEmpty()) {
                inputStack = firstItem(ingredients.getFirst());
            }
            int inputX = tooltipX + 5;
            int inputY = tooltipY + iconSize + 5;
            extractPreviewItem(guiGraphics, inputStack, inputX, inputY, mouseX, mouseY);

            int outputX = tooltipX + 2 * iconSize + 8;
            int outputY = tooltipY + iconSize + 5;
            extractPreviewItem(guiGraphics, new ItemStack(Items.STONECUTTER), outputX, outputY, mouseX, mouseY);

            return;
        } else {
            tooltipTexture = CRAFTING_TOOLTIP_TEXTURE;
        }

        guiGraphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                tooltipTexture,
                62,
                62,
                0,
                0,
                tooltipX,
                tooltipY,
                62,
                62
        );

        // Draw ingredients with proper mapping for non-stonecutter recipes
        for (int slotY = 0; slotY < gridSize; slotY++) {
            for (int slotX = 0; slotX < gridSize; slotX++) {
                int ingredientX = slotX - offsetX;
                int ingredientY = slotY - offsetY;

                ItemStack stack = ItemStack.EMPTY;

                // Only draw if inside recipe bounds


                if (ingredientX >= 0 && ingredientX < recipeWidth && ingredientY >= 0 && ingredientY < recipeHeight) {
                    int ingredientIndex = ingredientY * recipeWidth + ingredientX;
                    if (recipe instanceof ShapedRecipe shapedRecipe) {
                        List<Optional<Ingredient>> pattern = shapedRecipe.getIngredients();
                        if (ingredientIndex < pattern.size() && pattern.get(ingredientIndex).isPresent()) {
                            stack = previewStack(pattern.get(ingredientIndex).get());
                        }
                    } else if (ingredientIndex < ingredients.size()) {
                        Ingredient ing = ingredients.get(ingredientIndex);
                        if (!ing.isEmpty()) {
                            stack = previewStack(ing);
                        }
                    }
                }

                int x = tooltipX + slotX * iconSize + 5;
                int y = tooltipY + slotY * iconSize + 5;

                extractPreviewItem(guiGraphics, stack, x, y, mouseX, mouseY);
            }
        }
    }

    private ItemStack previewStack(Ingredient ingredient) {
        ItemStack first = firstItem(ingredient);
        if (first.isEmpty() || minecraft.player == null) return first;

        for (ItemStack inventoryStack : minecraft.player.getInventory().getNonEquipmentItems()) {
            if (!inventoryStack.isEmpty() && ingredient.test(inventoryStack)) {
                return inventoryStack.copyWithCount(1);
            }
        }
        return first;
    }

    private void extractPreviewItem(GuiGraphicsExtractor guiGraphics, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;

        guiGraphics.item(stack, x, y);
        guiGraphics.itemDecorations(font, stack, x, y);

        if (mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE) {
            guiGraphics.setTooltipForNextFrame(font, getTooltipFromItem(minecraft, stack), stack.getTooltipImage(), stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        boolean clickedOnSearchBox = searchBox.isMouseOver(mouseX, mouseY);

        //Option buttons
        if (button == 0) {
            List<Option> options = visibleOptions();
            for (int i = 0; i < options.size(); i++) {
                if (MouseUtil.isMouseOver(mouseX, mouseY, leftPos + BUTTON_X_OFFSET, topPos + BUTTON_Y_OFFSET + i * BUTTON_SPACING, BUTTON_SIZE, BUTTON_SIZE)) {
                    onOptionClicked(options.get(i));
                    return true;
                }
            }
        }

        if (!clickedOnSearchBox) {
            // Clicked outside the search box, unfocus it
            searchBox.setFocused(false);
        }

        // Existing mouseClicked logic below
        if (button == 0) {
            int xStart = (width - imageWidth) / 2 + 11;
            int yStart = (height - imageHeight) / 2 + 31;

            for (int i = 0; i < filteredRecipes.size(); i++) {
                int row = i / VISIBLE_COLS;
                int col = i % VISIBLE_COLS;

                if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                    continue;
                }

                int iconX = xStart + col * ICON_SPACING;
                int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

                if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                        mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {

                    selectedRecipeId = filteredRecipes.get(i).id().identifier();

                    moveSelectedRecipeToFront();
                    scrollOffset = 0;  // Scroll to top

                    assert Minecraft.getInstance().player != null;
                    boolean isShiftClick = Minecraft.getInstance().hasShiftDown();
                    ClientPacketDistributor.sendToServer(new SmartCraftingRecipeClickPayload(selectedRecipeId, isShiftClick));
                    return true;
                }
            }
        }

        if (button == 1) {
            if (clickedOnSearchBox) {
                searchBox.setValue("");
                onSearchTextChanged("");
                return true;
            }

            int xStart = (width - imageWidth) / 2 + 11;
            int yStart = (height - imageHeight) / 2 + 31;

            for (int i = 0; i < filteredRecipes.size(); i++) {
                int row = i / VISIBLE_COLS;
                int col = i % VISIBLE_COLS;

                if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) continue;

                int iconX = xStart + col * ICON_SPACING;
                int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

                if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                        mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {

                    toggleFavorite(filteredRecipes.get(i).id().identifier());
                    updateFilteredRecipes(); // Re-sort
                    return true;
                }
            }
        }

        // Scrollbar logic
        int scrollbarX = leftPos + SCROLLBAR_X_OFFSET;
        int scrollbarY = topPos + SCROLLBAR_Y_OFFSET;
        int handleHeight = 15;
        int totalRows = (int) Math.ceil((double) filteredRecipes.size() / VISIBLE_COLS);
        int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        if (maxScroll > 0) {
            float scrollPercent = scrollOffset / (float) maxScroll;
            int handleY = scrollbarY + (int) ((SCROLLBAR_HEIGHT - handleHeight) * scrollPercent);

            if (MouseUtil.isMouseOver(mouseX, mouseY, scrollbarX, handleY, SCROLLBAR_WIDTH, handleHeight)) {
                isDraggingScrollbar = true;
                dragOffsetY = (int) mouseY - handleY;
                return true;
            } else {
                isDraggingScrollbar = false;
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int maxRows = (int) Math.ceil(filteredRecipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        if (deltaY < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
        } else if (deltaY > 0 && scrollOffset > 0) {
            scrollOffset--;
        }

        return true;
    }

    private List<Option> visibleOptions() {
        List<Option> options = new ArrayList<>(List.of(Option.CRAFTING, Option.STONECUTTING, Option.SMITHING));
        if (SmartCraftingConfig.recursiveCraftingEnabled.get()) {
            options.add(Option.RECURSIVE);
        }
        if (JeiCompat.isAvailable()) {
            options.add(Option.JEI);
        }
        options.add(Option.SORT);
        return options;
    }

    private boolean isOptionEnabled(Option option) {
        return switch (option) {
            case CRAFTING -> ClientSettings.showCrafting;
            case STONECUTTING -> ClientSettings.showStonecutting;
            case SMITHING -> ClientSettings.showSmithing;
            case RECURSIVE -> PlayerData.isRecursive(menu.player);
            case JEI -> ClientSettings.jeiSync;
            case SORT -> true;
        };
    }

    private void onOptionClicked(Option option) {
        switch (option) {
            case CRAFTING -> ClientSettings.showCrafting = !ClientSettings.showCrafting;
            case STONECUTTING -> ClientSettings.showStonecutting = !ClientSettings.showStonecutting;
            case SMITHING -> ClientSettings.showSmithing = !ClientSettings.showSmithing;
            case RECURSIVE -> {
                boolean recursive = !PlayerData.isRecursive(menu.player);
                PlayerData.setRecursive(menu.player, recursive);
                ClientPacketDistributor.sendToServer(new SyncRecursive(recursive));
            }
            case JEI -> {
                ClientSettings.jeiSync = !ClientSettings.jeiSync;
                if (ClientSettings.jeiSync && JeiCompat.isAvailable()) {
                    searchBox.setValue(JeiCompat.getFilterText());
                }
            }
            case SORT -> {
                if (currentSortType == SortType.NAME) {
                    currentSortType = SortType.MOD;
                    ClientPacketDistributor.sendToServer(new SyncSortType("mod"));
                    PlayerData.setSortType(this.menu.player, "mod");
                } else {
                    currentSortType = SortType.NAME;
                    ClientPacketDistributor.sendToServer(new SyncSortType("name"));
                    PlayerData.setSortType(this.menu.player, "name");
                }
            }
        }

        updateFilteredRecipes();
        moveSelectedRecipeToFront();
        scrollOffset = 0;
    }

    private void extractOptionButtons(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        List<Option> options = visibleOptions();

        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int x = leftPos + BUTTON_X_OFFSET;
            int y = topPos + BUTTON_Y_OFFSET + i * BUTTON_SPACING;
            boolean enabled = isOptionEnabled(option);
            boolean hovered = MouseUtil.isMouseOver(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);

            // Vanilla style bevelled button, raised when enabled and pressed in when disabled
            guiGraphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFF000000);
            guiGraphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, enabled ? 0xFFC6C6C6 : 0xFF8B8B8B);
            if (enabled) {
                guiGraphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + 2, 0xFFFFFFFF);
                guiGraphics.fill(x + 1, y + 1, x + 2, y + BUTTON_SIZE - 1, 0xFFFFFFFF);
                guiGraphics.fill(x + 1, y + BUTTON_SIZE - 2, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, 0xFF555555);
                guiGraphics.fill(x + BUTTON_SIZE - 2, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, 0xFF555555);
            }

            switch (option) {
                case CRAFTING -> guiGraphics.item(new ItemStack(Items.CRAFTING_TABLE), x + 1, y + 1);
                case STONECUTTING -> guiGraphics.item(new ItemStack(Items.STONECUTTER), x + 1, y + 1);
                case SMITHING -> guiGraphics.item(new ItemStack(Items.SMITHING_TABLE), x + 1, y + 1);
                case RECURSIVE -> guiGraphics.blit(RenderPipelines.GUI_TEXTURED, RECURSIVE_ICON, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
                case JEI -> guiGraphics.blit(RenderPipelines.GUI_TEXTURED, JEI_ICON, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
                case SORT -> guiGraphics.blit(RenderPipelines.GUI_TEXTURED,
                        currentSortType == SortType.NAME ? SORT_NAME_ICON : SORT_MOD_ICON, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
            }

            if (!enabled) {
                guiGraphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, 0x88555555);
            }
            if (hovered) {
                guiGraphics.fill(x + 1, y + 1, x + BUTTON_SIZE - 1, y + BUTTON_SIZE - 1, 0x40FFFFFF);
                guiGraphics.setComponentTooltipForNextFrame(font, optionTooltip(option, enabled), mouseX, mouseY);
            }
        }
    }

    private List<Component> optionTooltip(Option option, boolean enabled) {
        List<Component> lines = new ArrayList<>();
        switch (option) {
            case CRAFTING -> lines.add(Component.translatable("tooltip.smartcrafting.option.crafting"));
            case STONECUTTING -> lines.add(Component.translatable("tooltip.smartcrafting.option.stonecutting"));
            case SMITHING -> lines.add(Component.translatable("tooltip.smartcrafting.option.smithing"));
            case RECURSIVE -> {
                lines.add(Component.translatable("tooltip.smartcrafting.option.recursive"));
                lines.add(Component.translatable("tooltip.smartcrafting.option.recursive_info").withStyle(ChatFormatting.GRAY));
            }
            case JEI -> lines.add(Component.translatable("tooltip.smartcrafting.option.jei_sync"));
            case SORT -> {
                lines.add(Component.translatable("tooltip.smartcrafting.option.sort"));
                lines.add(Component.translatable(currentSortType == SortType.NAME
                        ? "tooltip.smartcrafting.option.sort_name" : "tooltip.smartcrafting.option.sort_mod").withStyle(ChatFormatting.YELLOW));
                return lines;
            }
        }
        lines.add(Component.translatable(enabled ? "tooltip.smartcrafting.option.enabled" : "tooltip.smartcrafting.option.disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
        return lines;
    }
}
