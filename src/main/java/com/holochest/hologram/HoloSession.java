package com.holochest.hologram;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector2f;

public class HoloSession {

    public static final long OPEN_DURATION_MS = 460;
    public static final long CLOSE_DURATION_MS = 460;

    private final BlockPos pos;
    private final BlockPos partnerPos;
    private final String title;
    private Text titleText;
    private Text previewTitleText;
    private final int titleColor;
    private final SoundEvent openSound;
    private final SoundEvent closeSound;
    private int cachedSize;
    private int cols;
    private int rows;

    private final DefaultedList<ItemStack> cachedStacks;
    private long openStartTime;
    private long closeStartTime;
    private boolean closing = false;

    private float minX = 0;
    private float minY = 0;
    private float maxX = 0;
    private float maxY = 0;

    private float startX = 0;
    private float startY = 0;
    private float layoutScale = 1.0f;
    private float padX = 7f;
    private float padTop = 20f;
    private float slotSize = 18f;
    private float slotGap = 2f;

    private int previewSlot = -1;
    private DefaultedList<ItemStack> previewStacks = null;
    private String previewTitle = "";
    private float previewMinX = 0;
    private float previewMinY = 0;
    private float previewMaxX = 0;
    private float previewMaxY = 0;

    private boolean furnace = false;
    private float furnaceLitProgress = 0f;
    private float furnaceCookProgress = 0f;

    private boolean sortButtonHovered = false;
    private long lastSortTime = 0;

    public HoloSession(BlockPos pos, BlockPos partnerPos, Text titleText, int titleColor, int size, SoundEvent openSound, SoundEvent closeSound) {
        this.pos = pos;
        this.partnerPos = partnerPos;
        this.titleText = titleText;
        this.title = titleText != null ? titleText.getString() : "";
        this.titleColor = titleColor;
        this.cachedSize = size;
        this.openSound = openSound;
        this.closeSound = closeSound;
        calculateGridDimensions(size);
        this.cachedStacks = DefaultedList.ofSize(Math.max(54, size), ItemStack.EMPTY);
        this.openStartTime = System.currentTimeMillis();
    }

    private void calculateGridDimensions(int size) {
        if (furnace) {
            this.cols = 3;
            this.rows = 2;
            return;
        }
        if (size <= 3) {
            this.cols = Math.max(1, size);
            this.rows = 1;
        } else if (size <= 5) {
            this.cols = 5;
            this.rows = 1;
        } else if (size == 9) {
            this.cols = 3;
            this.rows = 3;
        } else {
            this.cols = 9;
            this.rows = Math.max(1, (size + 8) / 9);
        }
    }

    public boolean isFurnace() {
        return furnace;
    }

    public void setFurnace(boolean furnace) {
        this.furnace = furnace;
        if (furnace) {
            this.cols = 3;
            this.rows = 2;
        }
    }

    public float getFurnaceLitProgress() {
        return furnaceLitProgress;
    }

    public void setFurnaceLitProgress(float progress) {
        this.furnaceLitProgress = progress;
    }

    public float getFurnaceCookProgress() {
        return furnaceCookProgress;
    }

    public void setFurnaceCookProgress(float progress) {
        this.furnaceCookProgress = progress;
    }

    public boolean isSortButtonHovered() {
        return sortButtonHovered;
    }

    public void setSortButtonHovered(boolean hovered) {
        this.sortButtonHovered = hovered;
    }

    public void triggerSortAnimation() {
        this.lastSortTime = System.currentTimeMillis();
    }

    public long getLastSortTime() {
        return lastSortTime;
    }

    public void updateSize(int newSize) {
        this.cachedSize = newSize;
        calculateGridDimensions(newSize);
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockPos getPartnerPos() {
        return partnerPos;
    }

    public SoundEvent getOpenSound() {
        return openSound;
    }

    public SoundEvent getCloseSound() {
        return closeSound;
    }

    public boolean isMatchingPos(BlockPos checkPos) {
        if (checkPos == null) return false;
        if (pos.equals(checkPos)) return true;
        return partnerPos != null && partnerPos.equals(checkPos);
    }

    public String getTitle() {
        if (titleText != null) {
            return titleText.getString();
        }
        return title;
    }

    public int getTitleColor() {
        return titleColor;
    }

    public long getOpenStartTime() {
        return openStartTime;
    }

    public int getCachedSize() {
        return cachedSize;
    }

    public boolean isDoubleChest() {
        return this.rows > 3 || this.cachedSize > 27;
    }

    public float getScaleModifier() {
        return isDoubleChest() ? 0.72f : 1.0f;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public DefaultedList<ItemStack> getCachedStacks() {
        return cachedStacks;
    }

    public ItemStack getStack(int index) {
        if (index >= 0 && index < cachedStacks.size()) {
            return cachedStacks.get(index);
        }
        return ItemStack.EMPTY;
    }

    public void setStack(int index, ItemStack stack) {
        if (index >= 0 && index < cachedStacks.size()) {
            cachedStacks.set(index, stack);
        }
    }

    public boolean isClosing() {
        return closing;
    }

    public void startClosing() {
        if (!closing) {
            float cur = getOpenProgress();
            closing = true;
            long offset = (long) ((1.0f - cur) * CLOSE_DURATION_MS);
            closeStartTime = System.currentTimeMillis() - offset;
        }
    }

    public void reopen() {
        if (closing) {
            float cur = getOpenProgress();
            closing = false;
            long offset = (long) (cur * OPEN_DURATION_MS);
            openStartTime = System.currentTimeMillis() - offset;
        }
    }

    public boolean isFullyClosed() {
        return closing && (System.currentTimeMillis() - closeStartTime >= CLOSE_DURATION_MS);
    }

    public float getOpenProgress() {
        if (closing) {
            long elapsed = System.currentTimeMillis() - closeStartTime;
            float p = 1.0f - (float) elapsed / (float) CLOSE_DURATION_MS;
            return Math.clamp(p, 0f, 1f);
        } else {
            long elapsed = System.currentTimeMillis() - openStartTime;
            float p = (float) elapsed / (float) OPEN_DURATION_MS;
            return Math.clamp(p, 0f, 1f);
        }
    }

    public void setBounds(float minX, float minY, float maxX, float maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public boolean containsScreenPoint(float x, float y) {
        if (x >= minX && x <= maxX && y >= minY && y <= maxY) {
            return true;
        }
        if (previewSlot != -1 && x >= previewMinX && x <= previewMaxX && y >= previewMinY && y <= previewMaxY) {
            return true;
        }
        return false;
    }

    public void setLayout(float startX, float startY, float scale, float padX, float padTop, float slotSize, float slotGap) {
        this.startX = startX;
        this.startY = startY;
        this.layoutScale = scale;
        this.padX = padX;
        this.padTop = padTop;
        this.slotSize = slotSize;
        this.slotGap = slotGap;
    }

        public void setSlotScreenPos(int slot, Vector2f pos) {}

    public void setPreviewSlotScreenPos(int slot, Vector2f pos) {}

    public Vector2f getSlotScreenPos(int slot) {
        if (furnace) {
            if (slot == 0) return new Vector2f(startX + 16f * layoutScale + 9f * layoutScale, startY + 18f * layoutScale + 9f * layoutScale);
            if (slot == 1) return new Vector2f(startX + 16f * layoutScale + 9f * layoutScale, startY + 48f * layoutScale + 9f * layoutScale);
            if (slot == 2) return new Vector2f(startX + 58f * layoutScale + 11f * layoutScale, startY + 29f * layoutScale + 11f * layoutScale);
            return new Vector2f(startX, startY);
        }
        int c = (cols > 0) ? (slot % cols) : 0;
        int r = (cols > 0) ? (slot / cols) : 0;
        float x = startX + padX + c * (slotSize + slotGap) + slotSize / 2f;
        float y = startY + padTop + r * (slotSize + slotGap) + slotSize / 2f;
        return new Vector2f(x, y);
    }

    public boolean isShulkerPreviewOpen() {
        return previewSlot != -1 && previewStacks != null;
    }

    public int getPreviewSlot() {
        return previewSlot;
    }

    public String getPreviewTitle() {
        if (previewTitleText != null) {
            return previewTitleText.getString();
        }
        return Text.translatable("container.shulkerBox").getString();
    }

    public DefaultedList<ItemStack> getPreviewStacks() {
        return previewStacks;
    }

    public void setPreviewBounds(float minX, float minY, float maxX, float maxY) {
        this.previewMinX = minX;
        this.previewMinY = minY;
        this.previewMaxX = maxX;
        this.previewMaxY = maxY;
    }

    public Vector2f getPreviewSlotScreenPos(int slot) {
        int c = slot % 9;
        int r = slot / 9;
        float pSlotSize = 16f * layoutScale;
        float pSlotGap = 2f * layoutScale;
        float pPad = 5f * layoutScale;
        float x = previewMinX + pPad + c * (pSlotSize + pSlotGap) + pSlotSize / 2f;
        float y = previewMinY + (10f * layoutScale) + pPad + r * (pSlotSize + pSlotGap) + pSlotSize / 2f;
        return new Vector2f(x, y);
    }

    public boolean toggleShulkerPreview(int slotIndex) {
        if (previewSlot == slotIndex) {
            closePreview();
            return false;
        }

        if (slotIndex < 0 || slotIndex >= cachedSize) return false;
        ItemStack stack = getStack(slotIndex);
        if (stack.isEmpty()) return false;

        boolean isShulker = (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock)
                || stack.getItem().getTranslationKey().contains("shulker_box");

        if (!isShulker) return false;

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
        if (container != null) {
            container.copyTo(items);
        }

        this.previewSlot = slotIndex;
        this.previewStacks = items;
        this.previewTitle = stack.getName().getString();
        return true;
    }

    public void closePreview() {
        this.previewSlot = -1;
        this.previewStacks = null;
        this.previewTitle = "";
    }
}
