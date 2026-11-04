package com.holochest.render;

import com.holochest.hologram.FlyingItem;
import com.holochest.hologram.HoloChestManager;
import com.holochest.hologram.HoloSession;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.*;

public class HoloHudRenderer {

    private static class SessionRenderData {
        HoloSession session;
        HoloTheme theme;
        Vec3d emitterPos;
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

        private static BlockPos persistentFocusedPos = null;
    private static ItemStack previewHoverStack = null;
    private static Vector2f previewHoverPos = null;
    private static float previewHoverSize = 18f;

    public static BlockPos getPersistentFocusedPos() {
        return persistentFocusedPos;
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null || client.getWindow() == null) return;

        previewHoverStack = null;
        previewHoverPos = null;

        HoloChestManager manager = HoloChestManager.getInstance();

        boolean isScreenOpen = (client.currentScreen != null);
        if (isScreenOpen) {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
        }

        renderFlyingItems(context, client);

        if (!manager.hasActiveSessions()) {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
            
            return;
        }

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
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

            BlockState state = client.world.getBlockState(pos);
            Block b = state.getBlock();

            double emitterY = (b instanceof AbstractChestBlock) ? pos.getY() + 0.875 : pos.getY() + 1.0;
            Vec3d emitterPos = new Vec3d(centerX, emitterY, centerZ);

            double panelY = emitterY + 0.50 + (smooth * 0.35);
            Vec3d panelPos = new Vec3d(centerX, panelY, centerZ);

            HoloWorldProjector.ScreenPos projEmitter = HoloWorldProjector.project(emitterPos);
            HoloWorldProjector.ScreenPos projPanel = HoloWorldProjector.project(panelPos);
            if (!projPanel.visible()) continue;

            SessionRenderData data = new SessionRenderData();
            data.session = session;
            data.theme = HoloTheme.getTheme(state); 
            data.emitterPos = emitterPos;
            data.projEmitter = projEmitter;
            data.projPanel = projPanel;

            data.distance3D = (float) camera.getPos().distanceTo(panelPos);
            float clampedDist = Math.clamp(data.distance3D, 2.0f, 40.0f);
            float baseScale = Math.clamp(4.8f / clampedDist, 0.75f, 1.15f);
            boolean isFocused = true;
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

            float tx = d.projEmitter.x();
            float ty = d.projEmitter.y() - 1f;

            float targetBottomY = (d.projPanel.y() - 16f * d.scale) + 0f;
            float panelCenterX = d.projPanel.x() + 0f;

            float riseProgress = closing ? (progress * progress) : (1.0f - (float) Math.pow(1.0f - progress, 3));
            float currentBottomY = MathHelper.lerp(riseProgress, ty, targetBottomY);

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
                context.fill((int) (tx - pulseR), (int) ty - 1, (int) (tx + pulseR), (int) ty + 1, (Math.min(drawAlpha, 240) << 24) | 0xFFFFFF);
            } else {
                context.fill((int) (tx - emitterR), (int) ty - 1, (int) (tx + emitterR), (int) ty + 1, beamColor);
                context.fill((int) (tx - emitterR * 0.5f), (int) ty - 2, (int) (tx + emitterR * 0.5f), (int) ty + 2, borderColor);
            }

            drawConnectedTail(context, bx1, bx2, bottomY, tx, ty, bgBotColor, borderColor, beamColor);

            int backingAlpha = isFocused ? Math.min(drawAlpha, 252) : Math.min(drawAlpha, 85);
            context.fill((int) startX, (int) startY, (int) (startX + totalW), (int) bottomY, (backingAlpha << 24) | 0x060913);

            context.fillGradient((int) startX, (int) startY, (int) (startX + totalW), (int) bottomY, bgTopColor, bgBotColor);

            int scanlineColor = (Math.min(drawAlpha, 25) << 24) | theme.primaryColor;
            for (int y = (int) startY + 2; y < (int) bottomY; y += 3) {
                context.fill((int) startX + 1, y, (int) (startX + totalW) - 1, y + 1, scanlineColor);
            }

            float sweep = (now % 2400) / 2400f;
            int sweepY = (int) (startY + sweep * currentH);
            if (sweepY < bottomY) {
                int sweepColor = (Math.min(drawAlpha, 50) << 24) | 0xFFFFFF;
                context.fill((int) startX + 2, sweepY, (int) (startX + totalW) - 2, sweepY + 2, sweepColor);
            }

            if (!closing && progress < 0.88f) {
                int flash1 = (int) (startY + ((now * 4) % (int) Math.max(2, currentH)));
                int flash2 = (int) (startY + (((now * 9) + 33) % (int) Math.max(2, currentH)));
                context.fill((int) (startX - 4), flash1, (int) (startX + totalW + 4), flash1 + 2, (Math.min(drawAlpha, 235) << 24) | 0xFFFFFF);
                context.fill((int) (startX - 2), flash2, (int) (startX + totalW + 2), flash2 + 1, (Math.min(drawAlpha, 180) << 24) | theme.primaryColor);
            }

            drawBubbleBorders(context, (int) startX, (int) startY, (int) (startX + totalW), (int) bottomY, (int) bx1, (int) bx2, borderColor);

            TextRenderer tr = client.textRenderer;
            String titleText = session.getTitle();
            int titleColor = (drawAlpha << 24) | theme.primaryColor;

            if (currentH >= d.padTop * 0.75f) {
                float fontScale = 1.18f * scale;
                float availW = (totalW - (session.isFurnace() ? 12f : 28f) * scale) / fontScale;
                String displayTitle = titleText;
                if (tr.getWidth(displayTitle) > availW) {
                    displayTitle = tr.trimToWidth(displayTitle, (int) Math.max(10, availW - 6)) + "...";
                }

                context.getMatrices().push();
                context.getMatrices().translate((double)(startX + 6f * scale), (double)(startY + 4.5f * scale), 0.0);
                context.getMatrices().scale((float)(fontScale), (float)(fontScale), 1.0f);
                context.drawTextWithShadow(tr, displayTitle, 0, 0, titleColor);
                context.getMatrices().pop();

                if (!session.isFurnace()) {
                    float btnW = 16f * scale;
                    float btnH = 11f * scale;
                    float btnX = (startX + totalW) - btnW - 6f * scale;
                    float btnY = startY + 4f * scale;

                    boolean isBtnHovered = isSessionHovered && (crossX >= btnX && crossX <= btnX + btnW && crossY >= btnY && crossY <= btnY + btnH);
                    session.setSortButtonHovered(isBtnHovered);

                    int btnBg = isBtnHovered ? ((Math.min(drawAlpha, 210) << 24) | theme.primaryColor) : ((Math.min(drawAlpha, 110) << 24) | 0x060913);
                    int btnBorder = isBtnHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 150) << 24) | theme.primaryColor);

                    context.fill((int) btnX, (int) btnY, (int) (btnX + btnW), (int) (btnY + btnH), btnBg);
                    drawBorder(context, (int) btnX, (int) btnY, (int) (btnX + btnW), (int) (btnY + btnH), btnBorder);

                    context.getMatrices().push();
                    context.getMatrices().translate((double)(btnX + 3.8f * scale), (double)(btnY + 1.2f * scale), 0.0);
                    float iconScale = 0.95f * scale;
                    context.getMatrices().scale((float)(iconScale), (float)(iconScale), 1.0f);
                    int iconColor = isBtnHovered ? 0xFFFFFFFF : ((drawAlpha << 24) | 0xE0F7FA);
                    context.drawTextWithShadow(tr, "⇅", 0, 0, iconColor);
                    context.getMatrices().pop();

                    if (isBtnHovered) {
                        String sortTip = Text.translatable("holochest.sort_tip").getString();
                        int tipW = tr.getWidth(sortTip);
                        int tipX = (int) (btnX + btnW / 2f - tipW / 2f);
                        int tipY = (int) (startY - 15f * scale);
                        if (tipY < 4) {
                            tipY = (int) (btnY + btnH + 4f * scale);
                        }
                        int badgeBg = (Math.min(drawAlpha, 230) << 24) | 0x060913;
                        int badgeBorder = (drawAlpha << 24) | 0x80D8FF;
                        context.fill(tipX - 4, tipY - 2, tipX + tipW + 4, tipY + 10, badgeBg);
                        drawBorder(context, tipX - 4, tipY - 2, tipX + tipW + 4, tipY + 10, badgeBorder);
                        context.drawTextWithShadow(tr, sortTip, tipX, tipY, 0xFFFFFFFF);
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

                drawFlameIcon(context, flameX, flameY, flameW, flameH, (Math.min(drawAlpha, 60) << 24) | 0x442200, 1.0f);
                if (litProg > 0.01f) {
                    drawFlameIcon(context, flameX, flameY, flameW, flameH, (Math.min(drawAlpha, 235) << 24) | 0xFFA500, litProg);
                }

                float arrowX = startX + 38f * scale;
                float arrowY = startY + 36f * scale;
                float arrowW = 16f * scale;
                float arrowH = 8f * scale;
                float cookProg = session.getFurnaceCookProgress();

                drawCookArrow(context, arrowX, arrowY, arrowW, arrowH, (Math.min(drawAlpha, 70) << 24) | 0x444455, 1.0f);
                if (cookProg > 0.01f) {
                    drawCookArrow(context, arrowX, arrowY, arrowW, arrowH, (Math.min(drawAlpha, 240) << 24) | 0x00E5FF, cookProg);
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

                    Vector2f center = new Vector2f(sx + sSize / 2f, sy + sSize / 2f);
                    

                    boolean isSlotHovered = isSessionHovered && (crossX >= sx && crossX < sx + sSize && crossY >= sy && crossY < sy + sSize);
                    if (isSlotHovered) {
                        bestHoveredSession = session;
                        bestHoveredSlot = i;
                        bestHoveredStack = session.getStack(i);
                        bestHoveredSlotPos = center;
                        bestHoveredSlotSize = sSize;
                        bestHoveredTheme = theme;
                    }

                    ItemStack stack = session.getStack(i);
                    boolean isEnchanted = isItemEnchanted(stack);

                    if (isEnchanted) {
                        drawEnchantedSlotAura(context, sx, sy, sSize, drawAlpha, isSlotHovered, now, i);
                    } else {
                        int slotBg = isSlotHovered ? ((Math.min(drawAlpha, 210) << 24) | theme.primaryColor) : ((Math.min(drawAlpha, 85) << 24) | theme.slotBg);
                        int slotBorder = isSlotHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 125) << 24) | theme.slotBorder);

                        context.fill((int) sx, (int) sy, (int) (sx + sSize), (int) (sy + sSize), slotBg);
                        drawBorder(context, (int) sx, (int) sy, (int) (sx + sSize), (int) (sy + sSize), slotBorder);
                    }

                    if (i == 2) {
                        drawBorder(context, (int) sx + 1, (int) sy + 1, (int) (sx + sSize) - 1, (int) (sy + sSize) - 1, (Math.min(drawAlpha, 80) << 24) | theme.primaryColor);
                    }

                    if (i == 1 && session.getStack(1).isEmpty()) {
                        drawFlameIcon(context, sx + 4f * scale, sy + 4f * scale, 10f * scale, 10f * scale, (Math.min(drawAlpha, 45) << 24) | 0xFFA500, 1.0f);
                    }

                    if (!stack.isEmpty()) {
                        context.getMatrices().push();
                        context.getMatrices().translate((double)(center.x), (double)(center.y), 0.0);

                        float itemScale = (isSlotHovered ? 1.22f : 1.0f) * (sSize / 18f);
                        context.getMatrices().scale((float)(itemScale), (float)(itemScale), 1.0f);

                        context.drawItem(stack, -8, -8);
                        int holoWash;
                        if (isEnchanted) {
                            float pulse = (float) Math.sin((now + i * 210) / 280.0) * 0.5f + 0.5f;
                            int washAlpha = (int) (38 + 25 * pulse);
                            holoWash = (washAlpha << 24) | 0xC084FC;
                        } else {
                            holoWash = isSlotHovered ? 0x3DE0FFFF : 0x2280D8FF;
                        }
                        context.fill(-8, -8, 8, 8, holoWash);
                        context.drawItemInSlot(tr, stack, -8, -8);

                        context.getMatrices().pop();
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

                    Vector2f center = new Vector2f(sx + d.slotSize / 2f, sy + d.slotSize / 2f);
                    

                    boolean isSlotHovered = isSessionHovered && (crossX >= sx && crossX < sx + d.slotSize && crossY >= sy && crossY < sy + d.slotSize);
                    if (isSlotHovered) {
                        bestHoveredSession = session;
                        bestHoveredSlot = i;
                        bestHoveredStack = session.getStack(i);
                        bestHoveredSlotPos = center;
                        bestHoveredSlotSize = d.slotSize;
                        bestHoveredTheme = theme;
                    }

                    ItemStack stack = session.getStack(i);
                    boolean isEnchanted = isItemEnchanted(stack);

                    if (isEnchanted) {
                        drawEnchantedSlotAura(context, sx, sy, d.slotSize, drawAlpha, isSlotHovered, now, i);
                    } else {
                        int slotBg = isSlotHovered ? ((Math.min(drawAlpha, 210) << 24) | theme.primaryColor) : ((Math.min(drawAlpha, 85) << 24) | theme.slotBg);
                        int slotBorder = isSlotHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 125) << 24) | theme.slotBorder);

                        context.fill((int) sx, (int) sy, (int) (sx + d.slotSize), (int) (sy + d.slotSize), slotBg);
                        drawBorder(context, (int) sx, (int) sy, (int) (sx + d.slotSize), (int) (sy + d.slotSize), slotBorder);
                    }

                    long sortElapsed = now - session.getLastSortTime();
                    if (sortElapsed < 450) {
                        float sortProg = sortElapsed / 450.0f;
                        float slotDiag = (col + row) / (float) Math.max(1, (cols + session.getRows()));
                        float diff = Math.abs(sortProg - slotDiag);
                        if (diff < 0.20f) {
                            float waveStrength = 1.0f - (diff / 0.20f);
                            int waveAlpha = (int) (waveStrength * Math.min(drawAlpha, 180));
                            context.fill((int) sx, (int) sy, (int) (sx + d.slotSize), (int) (sy + d.slotSize), (waveAlpha << 24) | 0x80D8FF);
                        }
                    }

                    if (!stack.isEmpty()) {
                        context.getMatrices().push();
                        context.getMatrices().translate((double)(center.x), (double)(center.y), 0.0);

                        float itemScale = (isSlotHovered ? 1.22f : 1.0f) * (d.slotSize / 18f);
                        context.getMatrices().scale((float)(itemScale), (float)(itemScale), 1.0f);

                        context.drawItem(stack, -8, -8);
                        int holoWash;
                        if (isEnchanted) {
                            float pulse = (float) Math.sin((now + i * 210) / 280.0) * 0.5f + 0.5f;
                            int washAlpha = (int) (38 + 25 * pulse);
                            holoWash = (washAlpha << 24) | 0xC084FC;
                        } else {
                            holoWash = isSlotHovered ? 0x3DE0FFFF : 0x2280D8FF;
                        }
                        context.fill(-8, -8, 8, 8, holoWash);
                        context.drawItemInSlot(tr, stack, -8, -8);

                        context.getMatrices().pop();
                    }
                }
            }

            if (session.isShulkerPreviewOpen()) {
                renderShulkerPreview(context, tr, session, startX + totalW + (6f * scale), startY, scale, drawAlpha, crossX, crossY);
            }
        }

        if (!isScreenOpen) {
            manager.setAimingAtPanel(anyPanelAim);
            manager.setHoverState(bestHoveredSession, bestHoveredSlot);

            TextRenderer textRenderer = client.textRenderer;
            if (previewHoverStack != null && !previewHoverStack.isEmpty() && previewHoverPos != null) {
                renderHoloTooltip(context, client, textRenderer, previewHoverStack, null, previewHoverPos.x, previewHoverPos.y, previewHoverSize, sw, sh);
            } else if (bestHoveredSlot != -1 && !bestHoveredStack.isEmpty() && bestHoveredSlotPos != null) {
                renderHoloTooltip(context, client, textRenderer, bestHoveredStack, bestHoveredTheme, bestHoveredSlotPos.x, bestHoveredSlotPos.y, bestHoveredSlotSize, sw, sh);
            }
        } else {
            manager.setAimingAtPanel(false);
            manager.setHoverState(null, -1);
        }
    }

    private static boolean anyHovered(List<SessionRenderData> list) {
        for (SessionRenderData d : list) {
            if (d.isHovered) return true;
        }
        return false;
    }

    

    private static void drawConnectedTail(DrawContext context, float bx1, float bx2, float by, float tx, float ty, int fillColor, int borderColor, int beamColor) {
        int startY = (int) by;
        int endY = (int) ty;
        if (startY >= endY) return;

        for (int y = startY; y <= endY; y++) {
            float t = (float) (y - startY) / (float) (endY - startY);
            float curX1 = MathHelper.lerp(t, bx1, tx);
            float curX2 = MathHelper.lerp(t, bx2, tx);

            if (curX2 >= curX1) {
                context.fill((int) curX1, y, (int) curX2 + 1, y + 1, fillColor);
                context.fill((int) curX1, y, (int) curX1 + 1, y + 1, borderColor);
                context.fill((int) curX2, y, (int) curX2 + 1, y + 1, borderColor);
            }
        }

        context.fill((int) tx, startY, (int) tx + 1, endY, beamColor);
    }

    private static void drawBubbleBorders(DrawContext context, int x1, int y1, int x2, int y2, int bx1, int bx2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);

        int g1 = Math.max(x1, bx1);
        int g2 = Math.min(x2, bx2);
        if (g1 > x1) {
            context.fill(x1, y2 - 1, g1, y2, color);
        }
        if (g2 < x2) {
            context.fill(g2, y2 - 1, x2, y2, color);
        }
    }

    private static void renderShulkerPreview(DrawContext context, TextRenderer tr, HoloSession session, float px, float py, float scale, int alpha, float crossX, float crossY) {
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
        context.fill((int) px, (int) py, (int) (px + pW), (int) (py + pH), bgColor);
        drawBorder(context, (int) px, (int) py, (int) (px + pW), (int) (py + pH), borderColor);

        String shulkerTitle = session.getPreviewTitle();
        context.getMatrices().push();
        context.getMatrices().translate((double)(px + 4f * scale), (double)(py + 2.5f * scale), 0.0);
        float sFontScale = 0.75f * scale;
        context.getMatrices().scale((float)(sFontScale), (float)(sFontScale), 1.0f);
        context.drawText(tr, "◆ " + shulkerTitle, 0, 0, (alpha << 24) | 0xD8B4FE, false);
        context.getMatrices().pop();

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
                previewHoverPos = new Vector2f(sx + slotSize / 2f, sy + slotSize / 2f);
                previewHoverSize = slotSize;
            }

            if (isEnchanted) {
                drawEnchantedSlotAura(context, sx, sy, slotSize, alpha, isHovered, System.currentTimeMillis(), i);
            } else {
                int sBg = isHovered ? ((Math.min(alpha, 210) << 24) | 0x7E22CE) : ((Math.min(alpha, 80) << 24) | 0x2E1065);
                int sBorder = isHovered ? ((alpha << 24) | 0xF3E8FF) : ((Math.min(alpha, 120) << 24) | 0xA855F7);
                context.fill((int) sx, (int) sy, (int) (sx + slotSize), (int) (sy + slotSize), sBg);
                drawBorder(context, (int) sx, (int) sy, (int) (sx + slotSize), (int) (sy + slotSize), sBorder);
            }

            if (!stack.isEmpty()) {
                context.getMatrices().push();
                float itemCenterX = sx + slotSize / 2f;
                float itemCenterY = sy + slotSize / 2f;
                context.getMatrices().translate((double)(itemCenterX), (double)(itemCenterY), 0.0);
                float itemScale = (isHovered ? 1.20f : 1.0f) * (slotSize / 18f);
                context.getMatrices().scale((float)(itemScale), (float)(itemScale), 1.0f);

                context.drawItem(stack, -8, -8);
                int holoWash;
                if (isEnchanted) {
                    float pulse = (float) Math.sin((System.currentTimeMillis() + i * 210) / 280.0) * 0.5f + 0.5f;
                    int washAlpha = (int) (38 + 25 * pulse);
                    holoWash = (washAlpha << 24) | 0xC084FC;
                } else {
                    holoWash = isHovered ? 0x3DE0FFFF : 0x2280D8FF;
                }
                context.fill(-8, -8, 8, 8, holoWash);
                context.drawItemInSlot(tr, stack, -8, -8);
                context.getMatrices().pop();
            }
        }
    }

    public static boolean isItemEnchanted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.hasGlint() || stack.hasEnchantments();
    }

    private static void drawEnchantedSlotAura(DrawContext context, float sx, float sy, float size, int drawAlpha, boolean isHovered, long now, int slotIndex) {
        float pulse = (float) Math.sin((now + slotIndex * 210) / 280.0) * 0.5f + 0.5f;
        int auraAlpha = (int) (Math.min(drawAlpha, 150) * (0.50f + 0.50f * pulse));
        int auraColor = (auraAlpha << 24) | 0x6B21A8; 
        context.fill((int) sx, (int) sy, (int) (sx + size), (int) (sy + size), auraColor);

        int shimmerBorder = isHovered ? ((drawAlpha << 24) | 0xFFFFFF) : ((Math.min(drawAlpha, 215) << 24) | 0xC084FC);
        drawBorder(context, (int) sx, (int) sy, (int) (sx + size), (int) (sy + size), shimmerBorder);
        drawBorder(context, (int) sx + 1, (int) sy + 1, (int) (sx + size) - 1, (int) (sy + size) - 1, (Math.min(drawAlpha, 95) << 24) | 0xE9D5FF);

        for (int p = 0; p < 2; p++) {
            float pPhase = ((now + slotIndex * 230 + p * 410) % 1100) / 1100f;
            float sparkAlphaF = (float) Math.sin(pPhase * Math.PI);
            if (sparkAlphaF > 0.08f) {
                float px = sx + 2f + ((float) ((slotIndex * 7 + p * 11) % 11) / 11f) * (size - 4f);
                float py = sy + (size - 2f) - pPhase * (size - 3f);
                int sparkColor = ((int) (sparkAlphaF * Math.min(drawAlpha, 240)) << 24) | 0xFFFFFF;
                context.fill((int) px, (int) py, (int) px + 1, (int) py + 1, sparkColor);
                if (sparkAlphaF > 0.65f) {
                    int crossColor = ((int) (sparkAlphaF * 0.5f * Math.min(drawAlpha, 240)) << 24) | 0xE9D5FF;
                    context.fill((int) px - 1, (int) py, (int) px, (int) py + 1, crossColor);
                    context.fill((int) px + 1, (int) py, (int) px + 2, (int) py + 1, crossColor);
                    context.fill((int) px, (int) py - 1, (int) px + 1, (int) py, crossColor);
                    context.fill((int) px, (int) py + 1, (int) px + 1, (int) py + 2, crossColor);
                }
            }
        }
    }

    private static void renderFlyingItems(DrawContext context, MinecraftClient client) {
        for (FlyingItem item : HoloChestManager.getInstance().getFlyingItems()) {
            if (item.isFinished()) continue;

            Vector2f cur = item.getCurrentScreenPos();
            float p = item.getProgress();

            int trailColor = (180 << 24) | 0x00E5FF;
            context.fill((int) cur.x - 2, (int) cur.y - 2, (int) cur.x + 2, (int) cur.y + 2, trailColor);

            context.getMatrices().push();
            context.getMatrices().translate((double)(cur.x), (double)(cur.y), 0.0);
            float flightScale = 1.0f + (float) Math.sin(p * Math.PI) * 0.35f;
            context.getMatrices().scale((float)(flightScale), (float)(flightScale), 1.0f);

            context.drawItem(item.getStack(), -8, -8);
            context.getMatrices().pop();
        }
    }

    public static void drawBorder(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }

    private static void drawFlameIcon(DrawContext context, float x, float y, float w, float h, int color, float fillProgress) {
        float fillH = h * Math.clamp(fillProgress, 0f, 1f);
        float startY = y + (h - fillH);
        int ix = (int) x;
        int iy = (int) startY;
        int iw = (int) w;
        int ih = (int) fillH;
        if (ih <= 0) return;

        context.fill(ix + 3, iy, ix + iw - 3, iy + ih, color);
        context.fill(ix + 1, iy + 2, ix + iw - 1, iy + ih, color);
        context.fill(ix, iy + 4, ix + iw, iy + ih, color);
    }

    private static void drawCookArrow(DrawContext context, float x, float y, float w, float h, int color, float fillProgress) {
        float fillW = w * Math.clamp(fillProgress, 0f, 1f);
        int ix = (int) x;
        int iy = (int) y;
        int iw = (int) fillW;
        int ih = (int) h;
        if (iw <= 0) return;

        int midY = iy + ih / 2;
        context.fill(ix, midY - 2, ix + iw, midY + 2, color);
        if (fillProgress >= 0.7f) {
            float headP = (fillProgress - 0.7f) / 0.3f;
            int headSize = (int) (3 * headP);
            for (int d = 0; d <= headSize; d++) {
                context.fill(ix + iw - d, midY - d - 1, ix + iw - d + 1, midY + d + 2, color);
            }
        }
    }

    private static void renderHoloTooltip(DrawContext context, MinecraftClient client, TextRenderer tr, ItemStack stack, HoloTheme theme, float anchorX, float anchorY, float slotSize, int sw, int sh) {
        if (stack == null || stack.isEmpty()) return;

        List<net.minecraft.text.Text> lines;
        try {
            lines = net.minecraft.client.gui.screen.Screen.getTooltipFromItem(client, stack);
        } catch (Throwable t) {
            lines = List.of(stack.getName());
        }

        if (lines == null || lines.isEmpty()) {
            lines = List.of(stack.getName());
        }

        int maxTextW = 0;
        for (net.minecraft.text.Text line : lines) {
            int w = tr.getWidth(line);
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

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 400.0f);
        int primaryColor = (theme != null) ? theme.primaryColor : 0x00E5FF;

        int outerGlow = isEnchanted ? (0x20000000 | 0xC084FC) : (0x18000000 | primaryColor);
        context.fill(tipX - 1, tipY - 1, tipX + boxW + 1, tipY + boxH + 1, outerGlow);

        int bgTop = isEnchanted ? 0xF4160A2A : (0xF4000000 | (theme != null ? theme.bgTopColor : 0x051322));
        int bgBot = isEnchanted ? 0xF60A031B : (0xF6000000 | (theme != null ? theme.bgBotColor : 0x020813));
        context.fillGradient(tipX, tipY, tipX + boxW, tipY + boxH, bgTop, bgBot);

        int scanColor = isEnchanted ? 0x12C084FC : (0x12000000 | primaryColor);
        for (int y = tipY + 2; y < tipY + boxH - 1; y += 3) {
            context.fill(tipX + 1, y, tipX + boxW - 1, y + 1, scanColor);
        }

        float sweep = (now % 2200) / 2200f;
        int sweepY = (int) (tipY + sweep * boxH);
        if (sweepY < tipY + boxH - 1) {
            context.fill(tipX + 1, sweepY, tipX + boxW - 1, sweepY + 1, 0x1AFFFFFF);
        }

        int borderColor = isEnchanted ? (((int) (205 + 50 * pulse) << 24) | 0xC084FC) : (0xEE000000 | primaryColor);
        drawBorder(context, tipX, tipY, tipX + boxW, tipY + boxH, borderColor);
        drawBorder(context, tipX + 1, tipY + 1, tipX + boxW - 1, tipY + boxH - 1, 0x22FFFFFF);

        int cornerColor = 0xFFFFFFFF;
        context.fill(tipX, tipY, tipX + 3, tipY + 1, cornerColor);
        context.fill(tipX, tipY, tipX + 1, tipY + 3, cornerColor);
        context.fill(tipX + boxW - 3, tipY, tipX + boxW, tipY + 1, cornerColor);
        context.fill(tipX + boxW - 1, tipY, tipX + boxW, tipY + 3, cornerColor);
        context.fill(tipX, tipY + boxH - 1, tipX + 3, tipY + boxH, cornerColor);
        context.fill(tipX, tipY + boxH - 3, tipX + 1, tipY + boxH, cornerColor);
        context.fill(tipX + boxW - 3, tipY + boxH - 1, tipX + boxW, tipY + boxH, cornerColor);
        context.fill(tipX + boxW - 1, tipY + boxH - 3, tipX + boxW, tipY + boxH, cornerColor);

        if (lines.size() > 1) {
            int sepY = tipY + padY + 10;
            int sepColor = isEnchanted ? (0x50000000 | 0xC084FC) : (0x40000000 | primaryColor);
            context.fill(tipX + 4, sepY, tipX + boxW - 4, sepY + 1, sepColor);
        }

        for (int l = 0; l < lines.size(); l++) {
            net.minecraft.text.Text line = lines.get(l);
            int yOffset = (l == 0) ? 0 : 2;
            int lineY = tipY + padY + l * lineSpacing + yOffset;
            context.drawTextWithShadow(tr, line, tipX + padX, lineY, 0xFFFFFFFF);
        }
        context.getMatrices().pop();
        context.getMatrices().pop();
    }

}
