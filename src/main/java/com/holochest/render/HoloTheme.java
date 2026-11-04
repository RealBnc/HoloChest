package com.holochest.render;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;

public class HoloTheme {

    public static final HoloTheme CHEST = new HoloTheme(0xF59E0B, 0x2A1809, 0x140B04, 0x201206, 0x8C5E28);
    public static final HoloTheme BARREL = new HoloTheme(0xD97706, 0x241306, 0x120903, 0x1B0E05, 0x783F04);
    public static final HoloTheme ENDER_CHEST = new HoloTheme(0x2DD4BF, 0x05261F, 0x02130F, 0x031B15, 0x0F766E);
    public static final HoloTheme FURNACE = new HoloTheme(0xF97316, 0x2E1005, 0x180802, 0x220C04, 0x9A3412);
    public static final HoloTheme DEFAULT = new HoloTheme(0x38BDF8, 0x072033, 0x03111C, 0x061929, 0x0369A1);
    private static final HoloTheme[] SHULKER_THEMES = new HoloTheme[16];

    static {
        for (DyeColor dye : DyeColor.values()) {
            int primary = getDyePrimary(dye);
            int r = (primary >> 16) & 0xFF;
            int g = (primary >> 8) & 0xFF;
            int bl = primary & 0xFF;

            int bgTop = (Math.min(r / 6, 42) << 16) | (Math.min(g / 6, 42) << 8) | Math.min(bl / 6, 50);
            int bgBot = (Math.min(r / 10, 24) << 16) | (Math.min(g / 10, 24) << 8) | Math.min(bl / 10, 30);
            int slotBg = (Math.min(r / 8, 32) << 16) | (Math.min(g / 8, 32) << 8) | Math.min(bl / 8, 42);
            int slotBorder = (Math.min(r / 3, 110) << 16) | (Math.min(g / 3, 110) << 8) | Math.min(bl / 3, 140);

            SHULKER_THEMES[dye.ordinal()] = new HoloTheme(primary, bgTop, bgBot, slotBg, slotBorder);
        }
    }

    public final int primaryColor;
    public final int bgTopColor;
    public final int bgBotColor;
    public final int slotBg;
    public final int slotBorder;

    public HoloTheme(int primaryColor, int bgTopColor, int bgBotColor, int slotBg, int slotBorder) {
        this.primaryColor = primaryColor;
        this.bgTopColor = bgTopColor;
        this.bgBotColor = bgBotColor;
        this.slotBg = slotBg;
        this.slotBorder = slotBorder;
    }

    public static HoloTheme getTheme(BlockState state) {
        Block b = state.getBlock();

        if (b instanceof ShulkerBoxBlock shulker) {
            DyeColor dye = shulker.getColor();
            return (dye != null) ? SHULKER_THEMES[dye.ordinal()] : SHULKER_THEMES[DyeColor.PURPLE.ordinal()];
        }

        if (b instanceof ChestBlock) {
            return CHEST;
        }

        if (b instanceof BarrelBlock) {
            return BARREL;
        }

        if (b instanceof EnderChestBlock) {
            return ENDER_CHEST;
        }

        if (b instanceof AbstractFurnaceBlock) {
            return FURNACE;
        }

        return DEFAULT;
    }

    private static int getDyePrimary(DyeColor dye) {
        return switch (dye) {
            case WHITE -> 0xF8FAFC;
            case ORANGE -> 0xFB923C;
            case MAGENTA -> 0xF472B6;
            case LIGHT_BLUE -> 0x38BDF8;
            case YELLOW -> 0xFACC15;
            case LIME -> 0xA3E635;
            case PINK -> 0xFB7185;
            case GRAY -> 0x64748B;
            case LIGHT_GRAY -> 0x94A3B8;
            case CYAN -> 0x22D3EE;
            case PURPLE -> 0xC084FC;
            case BLUE -> 0x60A5FA;
            case BROWN -> 0xA16207;
            case GREEN -> 0x4ADE80;
            case RED -> 0xF87171;
            case BLACK -> 0x475569;
        };
    }
}
