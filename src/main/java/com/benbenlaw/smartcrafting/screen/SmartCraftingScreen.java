package com.benbenlaw.smartcrafting.screen;

import com.benbenlaw.core.util.MouseUtil;
import com.benbenlaw.smartcrafting.SmartCrafting;
import com.benbenlaw.smartcrafting.event.VanillaRecipeCache;
import com.benbenlaw.smartcrafting.networking.packets.SyncFavoriteRecipes;
import com.benbenlaw.smartcrafting.networking.packets.SyncSortType;
import com.benbenlaw.smartcrafting.networking.payload.SmartCraftingRecipeClickPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
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
    private static final Identifier STONECUTTER_TOOLTIP_TEXTURE = SmartCrafting.identifier("smart_crafting_table_stonecutter_render");
    static final Identifier SCROLL_SPRITE = SmartCrafting.identifier("textures/gui/scroll.png");
    static final Identifier MODE_BUTTON_SPRITE = SmartCrafting.identifier("textures/gui/mode_button.png");
    static final Identifier STAR_ICON = SmartCrafting.identifier("textures/gui/favorite.png");

    public static final String FAVORITES_TAG = "smart_crafting_favorites";
    private List<Recipe<?>> clientRecipes = Collections.emptyList();
    private List<RecipeHolder<?>> clientRecipeHolders = Collections.emptyList();

    private ItemStack getRecipeResult(Recipe<?> recipe) {

        if (recipe instanceof CraftingRecipe craftingRecipe) {

            NonNullList<ItemStack> dummyGrid = NonNullList.withSize(9, ItemStack.EMPTY);
            List<Ingredient> ingredients = craftingRecipe.placementInfo().ingredients();
            for (int i = 0; i < ingredients.size(); i++) {
                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    HolderSet<Item> matchingStacks = ingredient.getValues();
                    if (matchingStacks.size() > 0) {
                        dummyGrid.set(i, matchingStacks.get(0).value().getDefaultInstance());
                    }
                }
            }

            CraftingInput input = CraftingInput.of(3, 3, dummyGrid);
            return craftingRecipe.assemble(input, Minecraft.getInstance().level.registryAccess());

        }

        else if (recipe instanceof SingleItemRecipe single) {
            // Stonecutter / single-item recipes
            Ingredient ingredient = single.placementInfo().ingredients().getFirst();
            ItemStack dummyInput = ItemStack.EMPTY;

            if (ingredient != null) {
                HolderSet<Item> matchingItems = ingredient.getValues();
                if (matchingItems.size() > 0) {
                    dummyInput = matchingItems.iterator().next().value().getDefaultInstance();
                }
            }

            SingleRecipeInput input = new SingleRecipeInput(dummyInput);
            return single.assemble(input, Minecraft.getInstance().level.registryAccess());
        }

        return ItemStack.EMPTY;
    }

    public void setClientRecipes(Collection<? extends RecipeHolder<? extends Recipe<?>>> recipes) {
        // Keep holders
        this.clientRecipeHolders = recipes.stream()
                .filter(r -> !getRecipeResult(r.value()).isEmpty())
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

    public void toggleFavorite(Identifier recipeId) {
        ListTag list = menu.player.getPersistentData().getListOrEmpty(FAVORITES_TAG);
        Set<String> favorites = list.stream()
                .map(Tag::asString)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        if (favorites.contains(recipeId.toString())) {
            favorites.remove(recipeId.toString());
        } else {
            favorites.add(recipeId.toString());
        }

        ListTag newList = new ListTag();
        for (String id : favorites) {
            newList.add(StringTag.valueOf(id));
        }
        menu.player.getPersistentData().put(FAVORITES_TAG, newList);

        List<String> favList = new ArrayList<>(favorites);
        ClientPacketDistributor.sendToServer(new SyncFavoriteRecipes(favList));
    }

    // Slot size and layout
    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 17;
    private static final int VISIBLE_ROWS = 3;
    private static final int VISIBLE_COLS = 8;

    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_HEIGHT = 54;
    private static final int SCROLLBAR_X_OFFSET = 154;
    private static final int SCROLLBAR_Y_OFFSET = 15;

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
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        if (pendingRecipes != null) {
            setClientRecipes(pendingRecipes);
            pendingRecipes = null;
        }

        if (this.menu.player.getPersistentData().getString("smart_crafting_sort_type").equals("mod")) {
            currentSortType = SortType.MOD;
        } else {
            currentSortType = SortType.NAME;

        }

        int x = (width - imageWidth) / 2 + 8;
        int y = (height - imageHeight) / 2 - 14; // position above GUI

        searchBox = new EditBox(font, x, y, 141, 15, Component.literal("Search..."));
        searchBox.setMaxLength(50);
        searchBox.setResponder(this::onSearchTextChanged);
        searchBox.setFocused(true);
        searchBox.setBordered(true);
        searchBox.setVisible(true);

        addRenderableWidget(searchBox);

        updateFilteredRecipes();
    }

    private void onSearchTextChanged(String text) {
        lastSearchText = text.toLowerCase();
        updateFilteredRecipes();
    }

    private void updateFilteredRecipes() {
        // Filter by search text
        if (lastSearchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(clientRecipeHolders); // keep holders
        } else if (lastSearchText.startsWith("@")) {
            String modID = lastSearchText.substring(1);
            filteredRecipes = clientRecipeHolders.stream()
                    .filter(holder -> holder.id().identifier().getNamespace().toLowerCase(Locale.ROOT).contains(modID))
                    .toList();
        } else {
            filteredRecipes = clientRecipeHolders.stream()
                    .filter(holder -> {
                        ItemStack result = getRecipeResult(holder.value());
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
                        ItemStack result = getRecipeResult(recipe.value());
                        return result.getHoverName().getString().toLowerCase(Locale.ROOT);
                    }))
                    .toList();
        }

        // Sort with selected recipe at top, then favorites, then others
        Set<String> favoriteIds = menu.player.getPersistentData()
                .getList("smart_crafting_favorites")
                .stream()
                .map(ListTag::toString)
                .collect(Collectors.toSet());

        RecipeHolder<?> selectedRecipe = null;
        List<RecipeHolder<?>> favoriteRecipes = new ArrayList<>();
        List<RecipeHolder<?>> otherRecipes = new ArrayList<>();

        for (RecipeHolder<?> recipe : filteredRecipes) {
            String idStr = recipe.id().toString();
            if (selectedRecipeId != null && recipe.id().equals(selectedRecipeId)) {
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
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {

        if (filteredRecipes.isEmpty() && !clientRecipeHolders.isEmpty() && lastSearchText.isEmpty()) {
            filteredRecipes = clientRecipeHolders;
        }

        renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        renderRecipeIngredients(guiGraphics, mouseX, mouseY);
        renderTooltip(guiGraphics, mouseX, mouseY);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        renderRecipeIcons(guiGraphics, mouseX, mouseY);

        renderButtonTooltip(guiGraphics, mouseX, mouseY);
        guiGraphics.blit(MODE_BUTTON_SPRITE, leftPos + 153, topPos - 14, 0, 0, 14, 14, 14, 14);
    }
    private void renderRecipeIcons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<RecipeHolder<?>> recipes = this.filteredRecipes;

        int xStart = (width - imageWidth) / 2 + 11;
        int yStart = (height - imageHeight) / 2 + 17;

        int maxRows = (int) Math.ceil(recipes.size() / (float) VISIBLE_COLS);
        int maxScroll = Math.max(0, maxRows - VISIBLE_ROWS);

        scrollOffset = Math.min(scrollOffset, maxScroll);
        scrollOffset = Math.max(scrollOffset, 0);
        hoveredRecipeIndex = -1;

        // Load favorites from player data
        Set<String> favoriteIds = menu.player.getPersistentData()
                .getList("smart_crafting_favorites")
                .stream()
                .map(Tag::asString)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        for (int i = 0; i < recipes.size(); i++) {
            int row = i / VISIBLE_COLS;
            int col = i % VISIBLE_COLS;

            if (row < scrollOffset || row >= scrollOffset + VISIBLE_ROWS) {
                continue;
            }

            RecipeHolder<?> recipe = recipes.get(i);
            assert Minecraft.getInstance().level != null;

            ItemStack resultStack = getRecipeResult(recipe.value());

            int iconX = xStart + col * ICON_SPACING;
            int iconY = yStart + (row - scrollOffset) * ICON_SPACING;

            // Highlight selected recipe
            if (recipe.id().equals(selectedRecipeId)) {
                guiGraphics.fill(
                        iconX - 1, iconY - 1,
                        iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1,
                        0xAAFFFF00
                );
            }

            // Render item and decorations
            guiGraphics.renderItem(resultStack, iconX, iconY);
            guiGraphics.renderItemDecorations(minecraft.font, resultStack, iconX, iconY);

            // ✅ Render star on top if favorite
            if (favoriteIds.contains(recipe.id().toString())) {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(0, 0); // ensure it renders in front
                guiGraphics.blit(
                        STAR_ICON,
                        iconX + ICON_SIZE - 4,
                        iconY,
                        0, 0,
                        4, 4,
                        4, 4
                );
                guiGraphics.pose().popMatrix();
            }

            // Tooltip on hover
            if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE &&
                    mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
                hoveredRecipeIndex = i;

                List<ClientTooltipComponent> tooltip = new ArrayList<>();
                //tooltip.add(resultStack.getHoverName());

                Component text = Component.literal("Right Click to Favorite").withStyle(ChatFormatting.YELLOW);

                if (Minecraft.getInstance().options.advancedItemTooltips) {
                    text = Component.literal("Recipe ID: " + recipe.id()).withStyle(ChatFormatting.DARK_GRAY);
                }

                if (Minecraft.getInstance().hasShiftDown()) {
                    text = Component.literal("SHIFT to craft as many as possible!").withStyle(ChatFormatting.RED);
                }

                FormattedCharSequence sequence = text.getVisualOrderText();
                List<ClientTooltipComponent> tooltipLines = List.of(ClientTooltipComponent.create(sequence));
                guiGraphics.renderTooltip(font, tooltipLines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null );
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
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (isDraggingScrollbar) {
            int scrollbarY = topPos + SCROLLBAR_Y_OFFSET;
            int handleHeight = 15;

            int totalRows = (int) Math.ceil((double) filteredRecipes.size() / VISIBLE_COLS);
            int maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);

            int relativeY = (int) mouseY - scrollbarY - dragOffsetY;
            float percent = Mth.clamp(relativeY / (float)(SCROLLBAR_HEIGHT - handleHeight), 0.0F, 1.0F);

            // scrollOffset is in rows
            scrollOffset = Mth.clamp(Math.round(percent * maxScroll), 0, maxScroll);
            return true;
        }

        return super.mouseDragged(event, mouseX, mouseY);
    }

    private void moveSelectedRecipeToFront() {
        if (selectedRecipeId == null) return;

        int index = -1;
        for (int i = 0; i < filteredRecipes.size(); i++) {
            if (filteredRecipes.get(i).id().equals(selectedRecipeId)) {
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

    private void renderRecipeIngredients(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int indexToRender = hoveredRecipeIndex;

        // If no recipe is hovered, try to render the selected recipe
        if (indexToRender == -1 && selectedRecipeId != null) {
            // Find the selected recipe index
            for (int i = 0; i < filteredRecipes.size(); i++) {
                if (filteredRecipes.get(i).id().equals(selectedRecipeId)) {
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
        } else if (recipe instanceof StonecutterRecipe) {

            tooltipTexture = STONECUTTER_TOOLTIP_TEXTURE;

            // Draw stonecutter input and output explicitly
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(0, 0);

            //RenderSystem.sh(0, tooltipTexture);
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
                ItemStack matchingStacks = ingredients.getFirst().getValues().get(0).value().getDefaultInstance();
                inputStack = matchingStacks;

            }
            int inputX = tooltipX + 5;
            int inputY = tooltipY + iconSize + 5;
            guiGraphics.renderItem(inputStack, inputX, inputY);
            if (!inputStack.isEmpty()) {
                guiGraphics.renderItemDecorations(minecraft.font, inputStack, inputX, inputY);
            }

            int outputX = tooltipX + 2 * iconSize + 8;
            int outputY = tooltipY + iconSize + 5;
            guiGraphics.renderItem(new ItemStack(Items.STONECUTTER), outputX, outputY);
            guiGraphics.pose().popMatrix();

            return;
        } else {
            tooltipTexture = CRAFTING_TOOLTIP_TEXTURE;
        }

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0, 0);

        //RenderSystem.setShaderTexture(0, tooltipTexture);
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
                    if (ingredientIndex < ingredients.size()) {
                        Ingredient ing = ingredients.get(ingredientIndex);
                        if (!ing.isEmpty()) {
                            ItemStack matchedStack = ItemStack.EMPTY;

                            // Get the first matching ItemStack
                            ItemStack[] matchingStacks = ing.getValues().stream()
                                    .map(entry -> entry.value().getDefaultInstance())
                                    .toArray(ItemStack[]::new);

                            if (matchingStacks.length > 0) {
                                assert Minecraft.getInstance().player != null;
                                for (ItemStack inventoryStack : Minecraft.getInstance().player.getInventory().getNonEquipmentItems()) {
                                    if (inventoryStack.isEmpty()) continue;
                                    for (ItemStack candidate : matchingStacks) {
                                        if (ItemStack.isSameItem(inventoryStack, candidate)) {
                                            matchedStack = inventoryStack.copy();
                                            matchedStack.setCount(1); // Display single item
                                            break;
                                        }
                                    }
                                    if (!matchedStack.isEmpty()) break;
                                }

                                if (matchedStack.isEmpty()) {
                                    matchedStack = matchingStacks[0];
                                }

                                stack = matchedStack;
                            }
                        }
                    }
                }




                int x = tooltipX + slotX * iconSize + 5;
                int y = tooltipY + slotY * iconSize + 5;

                guiGraphics.renderItem(stack, x, y);
                if (!stack.isEmpty()) {
                    guiGraphics.renderItemDecorations(minecraft.font, stack, x, y);
                }
            }
        }

        guiGraphics.pose().popMatrix();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false);
        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        boolean clickedOnSearchBox = searchBox.isMouseOver(mouseX, mouseY);

        //Button Press
        if (MouseUtil.isMouseOver(mouseX, mouseY, leftPos + 153, topPos - 14, 14, 14)) {
            if (currentSortType == SortType.NAME) {
                currentSortType = SortType.MOD;
                ClientPacketDistributor.sendToServer(new SyncSortType("mod"));
                this.menu.player.getPersistentData().putString("smart_crafting_sort_type", "mod");
            } else {
                currentSortType = SortType.NAME;
                ClientPacketDistributor.sendToServer(new SyncSortType("name"));
                this.menu.player.getPersistentData().putString("smart_crafting_sort_type", "name");
            }

            updateFilteredRecipes();
            moveSelectedRecipeToFront();
            scrollOffset = 0;  // Reset scroll to top
            return true;
        }

        if (!clickedOnSearchBox) {
            // Clicked outside the search box, unfocus it
            searchBox.setFocused(false);
        }

        // Existing mouseClicked logic below
        if (button == 0) {
            int xStart = (width - imageWidth) / 2 + 11;
            int yStart = (height - imageHeight) / 2 + 17;

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

                    ResourceKey<Recipe<?>> recipeId = filteredRecipes.get(i).id();
                    selectedRecipeId = recipeId.identifier();

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
            int yStart = (height - imageHeight) / 2 + 17;

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

    public void renderButtonTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (MouseUtil.isMouseOver(mouseX, mouseY, leftPos + 153, topPos - 14, 14, 14)) {

            String typeString = this.menu.player.getPersistentData().getStringOr("smart_crafting_sort_type", "");
            Component text;

            if (typeString.equals("name")) {
                text = Component.translatable("block.smartcrafting.smart_crafting_table.sort_by_name").withStyle(ChatFormatting.WHITE);

            } else {
                text = Component.translatable("block.smartcrafting.smart_crafting_table.sort_by_mod").withStyle(ChatFormatting.WHITE);

            }

            FormattedCharSequence sequence = text.getVisualOrderText();
            List<ClientTooltipComponent> tooltipLines = List.of(ClientTooltipComponent.create(sequence));
            guiGraphics.renderTooltip(font, tooltipLines, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null );

        }
    }
}