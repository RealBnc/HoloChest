package com.holochest.render;

import com.holochest.hologram.FlyingItem;
import com.holochest.hologram.HoloChestManager;
import com.holochest.hologram.HoloSession;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.*;

public class HoloHudRenderer {

    private static class SessionRenderData {
        HoloSession session;
        HoloTheme theme;
        Vec3 emitterPos;
        HoloWorldProjector.ScreenPos projEmitter;
        HoloWorldProjector.ScreenPos projPanel;
        float distance3D;
        float scale;
        float slotSize;
        float slotGap;
        float padX;
        float padTop;
        float padBottom;
        float totalW;
        float totalH;
        float startX;
        float startY;
        float bottomY;
        float distToCrosshair;
        boolean isHovered = false;
    }

    private static ItemStack previewHoverStack = null;
    private static int previewHoverSlot = -1;
    private static Vector2f previewHoverPos = null;
    private static float previewHoverSize = 18f;

    private static ItemStack lastTooltipStack = ItemStack.EMPTY;
    private static List<Component> cachedTooltipLines = null;

    private static final java.lang.invoke.MethodHandle SCREEN_GETTER = resolveScreenGetter();

    private static java.lang.invoke.MethodHandle resolveScreenGetter() {
        try {
            java.lang.reflect.Method m = net.minecraft.client.gui.Gui.class.getMethod("screen");
            return java.lang.invoke.MethodHandles.lookup().unreflect(m);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Field f = Minecraft.class.getField("screen");
                return java.lang.invoke.MethodHandles.lookup().unreflectGetter(f);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    private static boolean isAnyScreenOpen(Minecraft client) {
        if (client == null) return false;
        try {
            if (SCREEN_GETTER != null) {
                Object target = (SCREEN_GETTER.type().parameterType(0) == Minecraft.class) ? client : client.gui;
                if (target != null) {
                    return SCREEN_GETTER.invoke(target) != null;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void render(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null || client.getWindow() == null) return;

        previewHoverStack = null;
        previewHoverSlot = -1;
        previewHoverPos = null;

        HoloChestManager manager = HoloChestManager.getInstance();

        boolean isScreenOpen = isAnyScreenOpen(client);
        if (isScreenOpen) {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
        }

        renderFlyingItems(extractor, client);

        if (!manager.hasActiveSessions()) {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
            return;
        }

        Camera camera = HoloWorldProjector.getCamera(client.gameRenderer);
        if (camera == null) return;

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        float crossX = sw / 2f;
        float crossY = sh / 2f;

        List<SessionRenderData> visibleEntries = new ArrayList<>();

        for (HoloSession session : manager.getActiveSessions()) {
            BlockPos pos = session.getPos();
            float progress = session.getOpenProgress();
            if (progress <= 0.001f) continue;

            float smooth = progress * progress * (3.0f - 2.0f * progress);

            double centerX = pos.getX() + 0.5;
            double centerZ = pos.getZ() + 0.5;

            if (session.getPartnerPos() != null) {
                BlockPos p = session.getPartnerPos();
                centerX = (pos.getX() + p.getX() + 1.0) / 2.0;
                centerZ = (pos.getZ() + p.getZ() + 1.0) / 2.0;
            }

            BlockState state = client.level.getBlockState(pos);
            Block b = state.getBlock();

            double emitterY = (b instanceof AbstractChestBlock) ? pos.getY() + 0.875 : pos.getY() + 1.0;
            Vec3 emitterPos = new Vec3(centerX, emitterY, centerZ);

            double panelY = emitterY + 0.50 + (smooth * 0.35);
            Vec3 panelPos = new Vec3(centerX, panelY, centerZ);

            HoloWorldProjector.ScreenPos projEmitter = HoloWorldProjector.project(emitterPos);
            HoloWorldProjector.ScreenPos projPanel = HoloWorldProjector.project(panelPos);
            if (!projPanel.visible()) continue;

            SessionRenderData data = new SessionRenderData();
            data.session = session;
            data.theme = HoloTheme.getTheme(state);
            data.emitterPos = emitterPos;
            data.projEmitter = projEmitter;
            data.projPanel = projPanel;

            data.distance3D = (float) camera.position().distanceTo(panelPos);
            float clampedDist = Math.clamp(data.distance3D, 2.0f, 40.0f);
            float baseScale = Math.clamp(4.8f / clampedDist, 0.75f, 1.15f);
            data.scale = baseScale * session.getScaleModifier() * (0.35f + 0.65f * smooth);

            data.slotSize = 18f * data.scale;
            data.slotGap = 2f * data.scale;
            data.padX = 7f * data.scale;
            data.padTop = 20f * data.scale;
            data.padBottom = 6f * data.scale;

            if (session.isFurnace()) {
                data.padTop = 18f * data.scale;
                data.totalW = 90f * data.scale;
                data.totalH = 74f * data.scale;
            } else {
                int cols = session.getCols();
                int rows = session.getRows();
                float gridW = (cols * data.slotSize) + ((cols - 1) * data.slotGap);
                float gridH = (rows * data.slotSize) + ((rows - 1) * data.slotGap);

                data.totalW = gridW + (data.padX * 2);
                data.totalH = gridH + data.padTop + data.padBottom;
            }

            data.bottomY = projPanel.y() - (16f * data.scale);
            data.startY = data.bottomY - data.totalH;

            visibleEntries.add(data);
        }

        if (visibleEntries.isEmpty()) {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
            return;
        }

        for (SessionRenderData d : visibleEntries) {
            float panelCenterX = d.projPanel.x();
            float panelBottomY = d.projPanel.y() - (16f * d.scale);
            d.startX = panelCenterX - (d.totalW / 2f);
            d.bottomY = panelBottomY;
            d.startY = d.bottomY - d.totalH;
            d.distToCrosshair = (float) Math.hypot(crossX - panelCenterX, crossY - (d.startY + d.totalH / 2f));
            d.session.setBounds(d.startX - 3, d.startY - 3, d.startX + d.totalW + 3, d.bottomY + (22f * d.scale));
            d.isHovered = !isScreenOpen && d.session.containsScreenPoint(crossX, crossY);
        }

        if (visibleEntries.size() > 1) {
            visibleEntries.sort((a, b) -> Float.compare(b.distance3D, a.distance3D));
        }

        boolean anyPanelAim = false;
        HoloSession bestHoveredSession = null;
        int bestHoveredSlot = -1;
        ItemStack bestHoveredStack = ItemStack.EMPTY;
        Vector2f bestHoveredSlotPos = null;
        float bestHoveredSlotSize = 18f;
        HoloTheme bestHoveredTheme = null;

        for (SessionRenderData d : visibleEntries) {
            HoloSession session = d.session;
            HoloTheme theme = d.theme;
            float progress = session.getOpenProgress();
            boolean closing = session.isClosing();

            float smooth = progress * progress * (3.0f - 2.0f * progress);

            boolean isSessionHovered = d.isHovered;
            boolean isFocused = isSessionHovered || manager.getActiveSessions().size() == 1;
            if (isSessionHovered) {
                anyPanelAim = true;
            }

            int baseAlpha = (int) (smooth * 255);
            int alpha = baseAlpha;

            float tx = d.projEmitter != null ? d.projEmitter.x() : d.projPanel.x();
            float ty = d.projEmitter != null ? d.projEmitter.y() - 1f : d.projPanel.y() + 30f;

            float targetBottomY = (d.projPanel.y() - 16f * d.scale);
            float panelCenterX = d.projPanel.x();

            float riseProgress = closing ? (progress * progress) : (1.0f - (float) Math.pow(1.0f - progress, 3));
            float currentBottomY = Mth.lerp(riseProgress, ty, targetBottomY);

            float unfold = closing ? progress : Math.clamp(progress / 0.65f, 0.08f, 1.0f);
            float currentH = d.totalH * unfold;
            float currentStartY = currentBottomY - currentH;

            long now = System.currentTimeMillis();
            int drawAlpha = alpha;

            if (!closing && progress < 0.88f) {
                long tStep = now / 35;
                if ((tStep % 4) == 0) {
                    drawAlpha = (int) (alpha * 0.72f);
                }
            }

            float startX = panelCenterX - (d.totalW / 2f);
            float startY = currentStartY;
            float bottomY = currentBottomY;
            float totalW = d.totalW;
            float scale = d.scale;

            float tailBaseHalf = 16f * scale;
            float bx1 = panelCenterX - tailBaseHalf;
            float bx2 = panelCenterX + tailBaseHalf;

            session.setBounds(startX - 3, startY - 3, startX + totalW + 3, bottomY + (22f * scale));
            session.setLayout(startX, startY, scale, d.padX, d.padTop, d.slotSize, d.slotGap);

            int bgTopColor = (Math.min(drawAlpha, 195) << 24) | theme.bgTopColor;
            int bgBotColor = (Math.min(drawAlpha, 225) << 24) | theme.bgBotColor;
            int borderColor = (drawAlpha << 24) | (isSessionHovered ? 0xFFFFFF : theme.primaryColor);
            int beamColor = (Math.min(drawAlpha, 130) << 24) | theme.primaryColor;

            int emitterR = (int) (7f * scale);
            if (!closing && progress < 0.88f) {
                float pulse = 1.0f + (1.0f - progress / 0.88f) * 0.7f;
                int pulseR = (int) (emitterR * pulse);
                extractor.fill((int) (tx - pulseR), (int) ty - 1, (int) (tx + pulseR), (int) ty + 1, (Math.min(drawAlpha, 240) << 24) | 0xFFFFFF);
            } else {
                extractor.fill((int) (tx - emitterR), (int) ty - 1, (int) (tx + emitterR), (int) ty + 1, beamColor);
                extractor.fill((int) (tx - emitterR * 0.5f), (int) ty - 2, (int) (tx + emitterR * 0.5f), (int) ty + 2, borderColor);
            }

            drawConnectedTail(extractor, bx1, bx2, bottomY, tx, ty, bgBotColor, borderColor, beamColor);

            int backingAlpha = isFocused ? Math.min(drawAlpha, 252) : Math.min(drawAlpha, 85);
            extractor.fill((int) startX, (int) startY, (int) (startX + totalW), (int) bottomY, (backingAlpha << 24) | 0x060913);

            extractor.fillGradient((int) startX, (int) startY, (int) (startX + totalW), (int) bottomY, bgTopColor, bgBotColor);

            int scanlineColor = (Math.min(drawAlpha, 25) << 24) | theme.primaryColor;
            for (int y = (int) startY + 2; y < (int) bottomY; y += 3) {
                extractor.fill((int) startX + 1, y, (int) (startX + totalW) - 1, y + 1, scanlineColor);
            }

            float sweep = (now % 2400) / 2400f;
            int sweepY = (int) (startY + sweep * currentH);
            if (sweepY < bottomY) {
                int sweepColor = (Math.min(drawAlpha, 50) << 24) | 0xFFFFFF;
                extractor.fill((int) startX + 2, sweepY, (int) (startX + totalW) - 2, sweepY + 2, sweepColor);
            }

            if (!closing && progress < 0.88f) {
                int flash1 = (int) (startY + ((now * 4) % (int) Math.max(2, currentH)));
                int flash2 = (int) (startY + (((now * 9) + 33) % (int) Math.max(2, currentH)));
                extractor.fill((int) (startX - 4), flash1, (int) (startX + totalW + 4), flash1 + 2, (Math.min(drawAlpha, 235) << 24) | 0xFFFFFF);
                extractor.fill((int) (startX - 2), flash2, (int) (startX + totalW + 2), flash2 + 1, (Math.min(drawAlpha, 180) << 24) | theme.primaryColor);
            }

            drawBubbleBorders(extractor, (int) startX, (int) startY, (int) (startX + totalW), (int) bottomY, (int) bx1, (int) bx2, borderColor);

            Font font = client.font;
            String titleText = session.getTitle();
            int titleColor = (drawAlpha << 24) | theme.primaryColor;

            if (currentH >= d.padTop * 0.75f) {
                float fontScale = 1.18f * scale;
                float availW = (totalW - (session.isFurnace() ? 12f : 28f) * scale) / fontScale;
                String displayTitle = titleText;
                if (font.width(displayTitle) > availW) {
                    displayTitle = font.plainSubstrByWidth(displayTitle, (int) Math.max(10, availW - 6)) + "...";
                }

                extractor.pose().pushMatrix();
                extractor.pose().translate(startX + 6f * scale, startY + 4.5f * scale);
                extractor.pose().scale(fontScale, fontScale);
                extractor.text(font, displayTitle, 0, 0, titleColor, true);
                extractor.pose().popMatrix();

                if (!session.isFurnace()) {
                    float btnW = 16f * scale;
                    float btnH = 11f * scale;
                    float btnX = (startX + totalW) - btnW - 6f * scale;
                    float btnY = startY + 4f * scale;

                    boolean isBtnHovered = isSessionHovered && (crossX >= btnX && crossX <= btnX + btnW && crossY >= btnY && crossY <= btnY + btnH);
                    session.setSortButtonHovered(isBtnHovered);

                    int btnBg = isBtnHovered ? ((Math.min(drawAlpha, 210) << 24) | theme.primaryColor) : ((Math.min(drawAlpha, 110) << 24) | 0x060913);
                    int btnBorder = isBtnHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 150) << 24) | theme.primaryColor);

                    extractor.fill((int) btnX, (int) btnY, (int) (btnX + btnW), (int) (btnY + btnH), btnBg);
                    drawBorder(extractor, (int) btnX, (int) btnY, (int) (btnX + btnW), (int) (btnY + btnH), btnBorder);

                    extractor.pose().pushMatrix();
                    extractor.pose().translate(btnX + 3.8f * scale, btnY + 1.2f * scale);
                    float iconScale = 0.95f * scale;
                    extractor.pose().scale(iconScale, iconScale);
                    int iconColor = isBtnHovered ? 0xFFFFFFFF : ((drawAlpha << 24) | 0xE0F7FA);
                    extractor.text(font, "⇅", 0, 0, iconColor, true);
                    extractor.pose().popMatrix();

                    if (isBtnHovered) {
                        String sortTip = Component.translatable("holochest.sort_tip").getString();
                        int tipW = font.width(sortTip);
                        int tipX = (int) (btnX + btnW / 2f - tipW / 2f);
                        int tipY = (int) (startY - 15f * scale);
                        if (tipY < 4) {
                            tipY = (int) (btnY + btnH + 4f * scale);
                        }
                        int badgeBg = (Math.min(drawAlpha, 230) << 24) | 0x060913;
                        int badgeBorder = (drawAlpha << 24) | 0x80D8FF;
                        extractor.fill(tipX - 4, tipY - 2, tipX + tipW + 4, tipY + 10, badgeBg);
                        drawBorder(extractor, tipX - 4, tipY - 2, tipX + tipW + 4, tipY + 10, badgeBorder);
                        extractor.text(font, sortTip, tipX, tipY, 0xFFFFFFFF, true);
                    }
                } else {
                    session.setSortButtonHovered(false);
                }
            } else {
                session.setSortButtonHovered(false);
            }

            if (session.isFurnace()) {
                float slot0X = startX + 16f * scale;
                float slot0Y = startY + 18f * scale;
                float slot1X = startX + 16f * scale;
                float slot1Y = startY + 48f * scale;
                float slot2X = startX + 58f * scale;
                float slot2Y = startY + 29f * scale;
                float normalSlotSize = 18f * scale;
                float resultSlotSize = 22f * scale;

                float flameX = startX + 19f * scale;
                float flameY = startY + 37f * scale;
                float flameW = 12f * scale;
                float flameH = 10f * scale;
                float litProg = session.getFurnaceLitProgress();

                drawFlameIcon(extractor, flameX, flameY, flameW, flameH, (Math.min(drawAlpha, 60) << 24) | 0x442200, 1.0f);
                if (litProg > 0.01f) {
                    drawFlameIcon(extractor, flameX, flameY, flameW, flameH, (Math.min(drawAlpha, 235) << 24) | 0xFFA500, litProg);
                }

                float arrowX = startX + 38f * scale;
                float arrowY = startY + 36f * scale;
                float arrowW = 16f * scale;
                float arrowH = 8f * scale;
                float cookProg = session.getFurnaceCookProgress();

                drawCookArrow(extractor, arrowX, arrowY, arrowW, arrowH, (Math.min(drawAlpha, 70) << 24) | 0x444455, 1.0f);
                if (cookProg > 0.01f) {
                    drawCookArrow(extractor, arrowX, arrowY, arrowW, arrowH, (Math.min(drawAlpha, 240) << 24) | 0x00E5FF, cookProg);
                }

                float[][] slotCoords = new float[][]{
                    {slot0X, slot0Y, normalSlotSize},
                    {slot1X, slot1Y, normalSlotSize},
                    {slot2X, slot2Y, resultSlotSize}
                };

                for (int i = 0; i < 3; i++) {
                    float sx = slotCoords[i][0];
                    float sy = slotCoords[i][1];
                    float sSize = slotCoords[i][2];

                    if (sy + sSize > bottomY + 2) continue;

                    boolean isSlotHovered = isSessionHovered && (crossX >= sx && crossX < sx + sSize && crossY >= sy && crossY < sy + sSize);
                    if (isSlotHovered) {
                        bestHoveredSession = session;
                        bestHoveredSlot = i;
                        bestHoveredStack = session.getStack(i);
                        bestHoveredSlotPos = new Vector2f(sx + sSize / 2f, sy + sSize / 2f);
                        bestHoveredSlotSize = sSize;
                        bestHoveredTheme = theme;
                    }

                    ItemStack stack = session.getStack(i);
                    boolean isEnchanted = isItemEnchanted(stack);

                    if (isEnchanted) {
                        drawEnchantedSlotAura(extractor, sx, sy, sSize, drawAlpha, isSlotHovered, now, i);
                    } else {
                        int slotBg = isSlotHovered ? ((Math.min(drawAlpha, 210) << 24) | theme.primaryColor) : ((Math.min(drawAlpha, 85) << 24) | theme.slotBg);
                        int slotBorder = isSlotHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 125) << 24) | theme.slotBorder);

                        extractor.fill((int) sx, (int) sy, (int) (sx + sSize), (int) (sy + sSize), slotBg);
                        drawBorder(extractor, (int) sx, (int) sy, (int) (sx + sSize), (int) (sy + sSize), slotBorder);
                    }

                    if (i == 2) {
                        drawBorder(extractor, (int) sx + 1, (int) sy + 1, (int) (sx + sSize) - 1, (int) (sy + sSize) - 1, (Math.min(drawAlpha, 80) << 24) | theme.primaryColor);
                    }

                    if (i == 1 && session.getStack(1).isEmpty()) {
                        drawFlameIcon(extractor, sx + 4f * scale, sy + 4f * scale, 10f * scale, 10f * scale, (Math.min(drawAlpha, 45) << 24) | 0xFFA500, 1.0f);
                    }

                    if (!stack.isEmpty()) {
                        extractor.pose().pushMatrix();
                        extractor.pose().translate(sx + sSize / 2f, sy + sSize / 2f);

                        float itemScale = (isSlotHovered ? 1.22f : 1.0f) * (sSize / 18f);
                        extractor.pose().scale(itemScale, itemScale);

                        extractor.item(stack, -8, -8);
                        int holoWash;
                        if (isEnchanted) {
                            float pulse = (float) Math.sin((now + i * 210) / 280.0) * 0.5f + 0.5f;
                            int washAlpha = (int) (38 + 25 * pulse);
                            holoWash = (washAlpha << 24) | 0xC084FC;
                        } else {
                            holoWash = isSlotHovered ? 0x3DE0FFFF : 0x2280D8FF;
                        }
                        extractor.fill(-8, -8, 8, 8, holoWash);
                        extractor.itemDecorations(font, stack, -8, -8);

                        extractor.pose().popMatrix();
                    }
                }
            } else {
                int totalSlots = session.getCachedSize();
                int cols = session.getCols();

                for (int i = 0; i < totalSlots; i++) {
                    int col = i % cols;
                    int row = i / cols;

                    float sx = startX + d.padX + col * (d.slotSize + d.slotGap);
                    float sy = startY + d.padTop + row * (d.slotSize + d.slotGap);

                    if (sy + d.slotSize > bottomY + 2) continue;

                    boolean isSlotHovered = isSessionHovered && (crossX >= sx && crossX < sx + d.slotSize && crossY >= sy && crossY < sy + d.slotSize);
                    if (isSlotHovered) {
                        bestHoveredSession = session;
                        bestHoveredSlot = i;
                        bestHoveredStack = session.getStack(i);
                        bestHoveredSlotPos = new Vector2f(sx + d.slotSize / 2f, sy + d.slotSize / 2f);
                        bestHoveredSlotSize = d.slotSize;
                        bestHoveredTheme = theme;
                    }

                    ItemStack stack = session.getStack(i);
                    boolean isEnchanted = isItemEnchanted(stack);

                    if (isEnchanted) {
                        drawEnchantedSlotAura(extractor, sx, sy, d.slotSize, drawAlpha, isSlotHovered, now, i);
                    } else {
                        int slotBg = isSlotHovered ? ((Math.min(drawAlpha, 210) << 24) | theme.primaryColor) : ((Math.min(drawAlpha, 85) << 24) | theme.slotBg);
                        int slotBorder = isSlotHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 125) << 24) | theme.slotBorder);

                        extractor.fill((int) sx, (int) sy, (int) (sx + d.slotSize), (int) (sy + d.slotSize), slotBg);
                        drawBorder(extractor, (int) sx, (int) sy, (int) (sx + d.slotSize), (int) (sy + d.slotSize), slotBorder);
                    }

                    long sortElapsed = now - session.getLastSortTime();
                    if (sortElapsed < 450) {
                        float sortProg = sortElapsed / 450.0f;
                        float slotDiag = (col + row) / (float) Math.max(1, (cols + session.getRows()));
                        float diff = Math.abs(sortProg - slotDiag);
                        if (diff < 0.20f) {
                            float waveStrength = 1.0f - (diff / 0.20f);
                            int waveAlpha = (int) (waveStrength * Math.min(drawAlpha, 180));
                            extractor.fill((int) sx, (int) sy, (int) (sx + d.slotSize), (int) (sy + d.slotSize), (waveAlpha << 24) | 0x80D8FF);
                        }
                    }

                    if (!stack.isEmpty()) {
                        extractor.pose().pushMatrix();
                        extractor.pose().translate(sx + d.slotSize / 2f, sy + d.slotSize / 2f);

                        float itemScale = (isSlotHovered ? 1.22f : 1.0f) * (d.slotSize / 18f);
                        extractor.pose().scale(itemScale, itemScale);

                        extractor.item(stack, -8, -8);
                        int holoWash;
                        if (isEnchanted) {
                            float pulse = (float) Math.sin((now + i * 210) / 280.0) * 0.5f + 0.5f;
                            int washAlpha = (int) (38 + 25 * pulse);
                            holoWash = (washAlpha << 24) | 0xC084FC;
                        } else {
                            holoWash = isSlotHovered ? 0x3DE0FFFF : 0x2280D8FF;
                        }
                        extractor.fill(-8, -8, 8, 8, holoWash);
                        extractor.itemDecorations(font, stack, -8, -8);

                        extractor.pose().popMatrix();
                    }
                }
            }

            if (session.isShulkerPreviewOpen()) {
                renderShulkerPreview(extractor, font, session, startX + totalW + (6f * scale), startY, scale, drawAlpha, crossX, crossY);
            }
        }

        if (!isScreenOpen) {
            manager.setAimingAtPanel(anyPanelAim);
            manager.setHoverState(bestHoveredSession, bestHoveredSlot);

            Font font = client.font;
            if (previewHoverStack != null && !previewHoverStack.isEmpty() && previewHoverPos != null) {
                renderHoloTooltip(extractor, client, font, previewHoverStack, null, previewHoverPos.x, previewHoverPos.y, previewHoverSize, sw, sh);
            } else if (bestHoveredSlot != -1 && !bestHoveredStack.isEmpty() && bestHoveredSlotPos != null) {
                renderHoloTooltip(extractor, client, font, bestHoveredStack, bestHoveredTheme, bestHoveredSlotPos.x, bestHoveredSlotPos.y, bestHoveredSlotSize, sw, sh);
            }
        } else {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
        }
    }

    private static void drawConnectedTail(GuiGraphicsExtractor extractor, float bx1, float bx2, float by, float tx, float ty, int fillColor, int borderColor, int beamColor) {
        int startY = (int) by;
        int endY = (int) ty;
        if (startY >= endY) return;

        for (int y = startY; y <= endY; y++) {
            float t = (float) (y - startY) / (float) (endY - startY);
            float curX1 = Mth.lerp(t, bx1, tx);
            float curX2 = Mth.lerp(t, bx2, tx);

            if (curX2 >= curX1) {
                extractor.fill((int) curX1, y, (int) curX2 + 1, y + 1, fillColor);
                extractor.fill((int) curX1, y, (int) curX1 + 1, y + 1, borderColor);
                extractor.fill((int) curX2, y, (int) curX2 + 1, y + 1, borderColor);
            }
        }

        extractor.fill((int) tx, startY, (int) tx + 1, endY, beamColor);
    }

    private static void drawBubbleBorders(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int bx1, int bx2, int color) {
        extractor.fill(x1, y1, x2, y1 + 1, color);
        extractor.fill(x1, y1, x1 + 1, y2, color);
        extractor.fill(x2 - 1, y1, x2, y2, color);

        int g1 = Math.max(x1, bx1);
        int g2 = Math.min(x2, bx2);
        if (g1 > x1) {
            extractor.fill(x1, y2 - 1, g1, y2, color);
        }
        if (g2 < x2) {
            extractor.fill(g2, y2 - 1, x2, y2, color);
        }
    }

    private static void renderShulkerPreview(GuiGraphicsExtractor extractor, Font font, HoloSession session, float px, float py, float scale, int alpha, float crossX, float crossY) {
        int cols = 9;
        int rows = 3;
        float slotSize = 16f * scale;
        float slotGap = 2f * scale;
        float pad = 5f * scale;

        float pW = (cols * slotSize) + ((cols - 1) * slotGap) + pad * 2;
        float pH = (rows * slotSize) + ((rows - 1) * slotGap) + pad * 2 + (10f * scale);

        session.setPreviewBounds(px, py, px + pW, py + pH);

        int bgColor = (Math.min(alpha, 190) << 24) | 0x051E33;
        int borderColor = (Math.min(alpha, 240) << 24) | 0x9933FF;
        extractor.fill((int) px, (int) py, (int) (px + pW), (int) (py + pH), bgColor);
        drawBorder(extractor, (int) px, (int) py, (int) (px + pW), (int) (py + pH), borderColor);

        String shulkerTitle = session.getPreviewTitle();
        extractor.pose().pushMatrix();
        extractor.pose().translate(px + 4f * scale, py + 2.5f * scale);
        float sFontScale = 0.75f * scale;
        extractor.pose().scale(sFontScale, sFontScale);
        extractor.text(font, "◆ " + shulkerTitle, 0, 0, (alpha << 24) | 0xD8B4FE, false);
        extractor.pose().popMatrix();

        var stacks = session.getPreviewStacks();
        if (stacks == null) return;

        for (int i = 0; i < 27; i++) {
            int col = i % cols;
            int row = i / cols;

            float sx = px + pad + col * (slotSize + slotGap);
            float sy = py + (10f * scale) + pad + row * (slotSize + slotGap);

            boolean isHovered = (crossX >= sx && crossX < sx + slotSize && crossY >= sy && crossY < sy + slotSize);

            ItemStack stack = (i < stacks.size()) ? stacks.get(i) : ItemStack.EMPTY;
            boolean isEnchanted = isItemEnchanted(stack);

            if (isHovered && !stack.isEmpty()) {
                previewHoverStack = stack;
                previewHoverSlot = i;
                previewHoverPos = new Vector2f(sx + slotSize / 2f, sy + slotSize / 2f);
                previewHoverSize = slotSize;
            }

            if (isEnchanted) {
                drawEnchantedSlotAura(extractor, sx, sy, slotSize, alpha, isHovered, System.currentTimeMillis(), i);
            } else {
                int sBg = isHovered ? ((Math.min(alpha, 210) << 24) | 0x7E22CE) : ((Math.min(alpha, 80) << 24) | 0x2E1065);
                int sBorder = isHovered ? ((alpha << 24) | 0xF3E8FF) : ((Math.min(alpha, 120) << 24) | 0xA855F7);
                extractor.fill((int) sx, (int) sy, (int) (sx + slotSize), (int) (sy + slotSize), sBg);
                drawBorder(extractor, (int) sx, (int) sy, (int) (sx + slotSize), (int) (sy + slotSize), sBorder);
            }

            if (!stack.isEmpty()) {
                extractor.pose().pushMatrix();
                float itemCenterX = sx + slotSize / 2f;
                float itemCenterY = sy + slotSize / 2f;
                extractor.pose().translate(itemCenterX, itemCenterY);
                float itemScale = (isHovered ? 1.20f : 1.0f) * (slotSize / 18f);
                extractor.pose().scale(itemScale, itemScale);

                extractor.item(stack, -8, -8);
                int holoWash;
                if (isEnchanted) {
                    float pulse = (float) Math.sin((System.currentTimeMillis() + i * 210) / 280.0) * 0.5f + 0.5f;
                    int washAlpha = (int) (38 + 25 * pulse);
                    holoWash = (washAlpha << 24) | 0xC084FC;
                } else {
                    holoWash = isHovered ? 0x3DE0FFFF : 0x2280D8FF;
                }
                extractor.fill(-8, -8, 8, 8, holoWash);
                extractor.itemDecorations(font, stack, -8, -8);
                extractor.pose().popMatrix();
            }
        }
    }

    public static boolean isItemEnchanted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.hasFoil() || stack.isEnchanted();
    }

    private static void drawEnchantedSlotAura(GuiGraphicsExtractor extractor, float sx, float sy, float size, int drawAlpha, boolean isHovered, long now, int slotIndex) {
        float pulse = (float) Math.sin((now + slotIndex * 210) / 280.0) * 0.5f + 0.5f;
        int auraAlpha = (int) (Math.min(drawAlpha, 150) * (0.50f + 0.50f * pulse));
        int auraColor = (auraAlpha << 24) | 0x6B21A8;
        extractor.fill((int) sx, (int) sy, (int) (sx + size), (int) (sy + size), auraColor);

        int shimmerBorder = isHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 215) << 24) | 0xC084FC);
        drawBorder(extractor, (int) sx, (int) sy, (int) (sx + size), (int) (sy + size), shimmerBorder);
        drawBorder(extractor, (int) sx + 1, (int) sy + 1, (int) (sx + size) - 1, (int) (sy + size) - 1, (Math.min(drawAlpha, 95) << 24) | 0xE9D5FF);

        for (int p = 0; p < 2; p++) {
            float pPhase = ((now + slotIndex * 230 + p * 410) % 1100) / 1100f;
            float sparkAlphaF = (float) Math.sin(pPhase * Math.PI);
            if (sparkAlphaF > 0.08f) {
                float px = sx + 2f + ((float) ((slotIndex * 7 + p * 11) % 11) / 11f) * (size - 4f);
                float py = sy + (size - 2f) - pPhase * (size - 3f);
                int sparkColor = ((int) (sparkAlphaF * Math.min(drawAlpha, 240)) << 24) | 0xFFFFFF;
                extractor.fill((int) px, (int) py, (int) px + 1, (int) py + 1, sparkColor);
                if (sparkAlphaF > 0.65f) {
                    int crossColor = ((int) (sparkAlphaF * 0.5f * Math.min(drawAlpha, 240)) << 24) | 0xE9D5FF;
                    extractor.fill((int) px - 1, (int) py, (int) px, (int) py + 1, crossColor);
                    extractor.fill((int) px + 1, (int) py, (int) px + 2, (int) py + 1, crossColor);
                    extractor.fill((int) px, (int) py - 1, (int) px + 1, (int) py, crossColor);
                    extractor.fill((int) px, (int) py + 1, (int) px + 1, (int) py + 2, crossColor);
                }
            }
        }
    }

    private static void renderFlyingItems(GuiGraphicsExtractor extractor, Minecraft client) {
        for (FlyingItem item : HoloChestManager.getInstance().getFlyingItems()) {
            if (item.isFinished()) continue;

            float curX = item.getScreenX();
            float curY = item.getScreenY();
            float p = item.getProgress();

            int trailColor = (180 << 24) | 0x00E5FF;
            extractor.fill((int) curX - 2, (int) curY - 2, (int) curX + 2, (int) curY + 2, trailColor);

            extractor.pose().pushMatrix();
            extractor.pose().translate(curX, curY);
            float flightScale = 1.0f + (float) Math.sin(p * Math.PI) * 0.35f;
            extractor.pose().scale(flightScale, flightScale);

            extractor.item(item.getStack(), -8, -8);
            extractor.pose().popMatrix();
        }
    }

    public static void drawBorder(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int color) {
        extractor.fill(x1, y1, x2, y1 + 1, color);
        extractor.fill(x1, y2 - 1, x2, y2, color);
        extractor.fill(x1, y1, x1 + 1, y2, color);
        extractor.fill(x2 - 1, y1, x2, y2, color);
    }

    private static void drawFlameIcon(GuiGraphicsExtractor extractor, float x, float y, float w, float h, int color, float fillProgress) {
        float fillH = h * Math.clamp(fillProgress, 0f, 1f);
        float startY = y + (h - fillH);
        int ix = (int) x;
        int iy = (int) startY;
        int iw = (int) w;
        int ih = (int) fillH;
        if (ih <= 0) return;

        extractor.fill(ix + 3, iy, ix + iw - 3, iy + ih, color);
        extractor.fill(ix + 1, iy + 2, ix + iw - 1, iy + ih, color);
        extractor.fill(ix, iy + 4, ix + iw, iy + ih, color);
    }

    private static void drawCookArrow(GuiGraphicsExtractor extractor, float x, float y, float w, float h, int color, float fillProgress) {
        float fillW = w * Math.clamp(fillProgress, 0f, 1f);
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) fillW;
        int ih = (int) h;
        if (iw <= 0) return;

        int midY = iy + ih / 2;
        extractor.fill(ix, midY - 2, ix + iw, midY + 2, color);
        if (fillProgress >= 0.7f) {
            float headP = (fillProgress - 0.7f) / 0.3f;
            int headSize = (int) (3 * headP);
            for (int d = 0; d <= headSize; d++) {
                extractor.fill(ix + iw - d, midY - d - 1, ix + iw - d + 1, midY + d + 2, color);
            }
        }
    }

    private static void renderHoloTooltip(GuiGraphicsExtractor extractor, Minecraft client, Font tr, ItemStack stack, HoloTheme theme, float anchorX, float anchorY, float slotSize, int sw, int sh) {
        if (stack == null || stack.isEmpty()) return;

        List<Component> lines;
        if (ItemStack.matches(stack, lastTooltipStack) && cachedTooltipLines != null) {
            lines = cachedTooltipLines;
        } else {
            try {
                lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(client, stack);
            } catch (Throwable t) {
                lines = List.of(stack.getHoverName());
            }

            if (lines == null || lines.isEmpty()) {
                lines = List.of(stack.getHoverName());
            }
            lastTooltipStack = stack.copy();
            cachedTooltipLines = lines;
        }

        int maxTextW = 0;
        for (Component line : lines) {
            int w = tr.width(line);
            if (w > maxTextW) {
                maxTextW = w;
            }
        }

        int padX = 7;
        int padY = 6;
        int lineSpacing = 11;
        int boxW = maxTextW + padX * 2;
        int extraSpacer = (lines.size() > 1) ? 2 : 0;
        int boxH = lines.size() * lineSpacing + extraSpacer + padY * 2 - 2;

        int tipX = (int) (anchorX + slotSize / 2f + 9);
        int tipY = (int) (anchorY - 12);

        if (tipX + boxW > sw - 6) {
            tipX = (int) (anchorX - slotSize / 2f - boxW - 9);
        }
        if (tipX < 6) {
            tipX = 6;
        }

        if (tipY + boxH > sh - 6) {
            tipY = sh - boxH - 6;
        }
        if (tipY < 6) {
            tipY = 6;
        }

        long now = System.currentTimeMillis();
        boolean isEnchanted = isItemEnchanted(stack);
        float pulse = (float) Math.sin(now / 240.0) * 0.5f + 0.5f;

        int primaryColor = (theme != null) ? theme.primaryColor : 0x00E5FF;

        int outerGlow = isEnchanted ? (0x20000000 | 0xC084FC) : (0x18000000 | primaryColor);
        extractor.fill(tipX - 1, tipY - 1, tipX + boxW + 1, tipY + boxH + 1, outerGlow);

        int bgTop = isEnchanted ? 0xF4160A2A : (0xF4000000 | (theme != null ? theme.bgTopColor : 0x051322));
        int bgBot = isEnchanted ? 0xF60A031B : (0xF6000000 | (theme != null ? theme.bgBotColor : 0x020813));
        extractor.fillGradient(tipX, tipY, tipX + boxW, tipY + boxH, bgTop, bgBot);

        int scanColor = isEnchanted ? 0x12C084FC : (0x12000000 | primaryColor);
        for (int y = tipY + 2; y < tipY + boxH - 1; y += 3) {
            extractor.fill(tipX + 1, y, tipX + boxW - 1, y + 1, scanColor);
        }

        float sweep = (now % 2200) / 2200f;
        int sweepY = (int) (tipY + sweep * boxH);
        if (sweepY < tipY + boxH - 1) {
            extractor.fill(tipX + 1, sweepY, tipX + boxW - 1, sweepY + 1, 0x1AFFFFFF);
        }

        int borderColor = isEnchanted ? (((int) (205 + 50 * pulse) << 24) | 0xC084FC) : (0xEE000000 | primaryColor);
        drawBorder(extractor, tipX, tipY, tipX + boxW, tipY + boxH, borderColor);
        drawBorder(extractor, tipX + 1, tipY + 1, tipX + boxW - 1, tipY + boxH - 1, 0x22FFFFFF);

        int cornerColor = 0xFFFFFFFF;
        extractor.fill(tipX, tipY, tipX + 3, tipY + 1, cornerColor);
        extractor.fill(tipX, tipY, tipX + 1, tipY + 3, cornerColor);
        extractor.fill(tipX + boxW - 3, tipY, tipX + boxW, tipY + 1, cornerColor);
        extractor.fill(tipX + boxW - 1, tipY, tipX + boxW, tipY + 3, cornerColor);
        extractor.fill(tipX, tipY + boxH - 1, tipX + 3, tipY + boxH, cornerColor);
        extractor.fill(tipX, tipY + boxH - 3, tipX + 1, tipY + boxH, cornerColor);
        extractor.fill(tipX + boxW - 3, tipY + boxH - 1, tipX + boxW, tipY + boxH, cornerColor);
        extractor.fill(tipX + boxW - 1, tipY + boxH - 3, tipX + boxW, tipY + boxH, cornerColor);

        if (lines.size() > 1) {
            int sepY = tipY + padY + 10;
            int sepColor = isEnchanted ? (0x50000000 | 0xC084FC) : (0x40000000 | primaryColor);
            extractor.fill(tipX + 4, sepY, tipX + boxW - 4, sepY + 1, sepColor);
        }

        for (int l = 0; l < lines.size(); l++) {
            Component line = lines.get(l);
            int yOffset = (l == 0) ? 0 : 2;
            int lineY = tipY + padY + l * lineSpacing + yOffset;
            extractor.text(tr, line, tipX + padX, lineY, 0xFFFFFFFF, true);
        }
    }
}
