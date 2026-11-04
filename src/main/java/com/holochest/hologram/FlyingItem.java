package com.holochest.hologram;

import com.holochest.sound.HoloSoundHelper;
import net.minecraft.item.ItemStack;
import org.joml.Vector2f;

public class FlyingItem {

    private final ItemStack stack;
    private final float startX;
    private final float startY;
    private final float endX;
    private final float endY;
    private final long startTime;
    private final long durationMs;
    private final boolean toPlayer;
    private boolean finished = false;

    public FlyingItem(ItemStack stack, Vector2f startPos, Vector2f endPos, long durationMs, boolean toPlayer) {
        this.stack = stack.copy();
        this.startX = startPos != null ? startPos.x : 0f;
        this.startY = startPos != null ? startPos.y : 0f;
        this.endX = endPos != null ? endPos.x : 0f;
        this.endY = endPos != null ? endPos.y : 0f;
        this.startTime = System.currentTimeMillis();
        this.durationMs = durationMs;
        this.toPlayer = toPlayer;
    }

    public ItemStack getStack() {
        return stack;
    }

    public boolean isToPlayer() {
        return toPlayer;
    }

    public boolean isFinished() {
        return finished;
    }

    public float getProgress() {
        long elapsed = System.currentTimeMillis() - startTime;
        float p = (float) elapsed / (float) durationMs;
        if (p >= 1.0f && !finished) {
            finished = true;
            HoloSoundHelper.playItemFlight();
        }
        return Math.clamp(p, 0f, 1f);
    }

    public float getScreenX() {
        float p = getProgress();
        float easeP = p * p * (3.0f - 2.0f * p);
        return startX + (endX - startX) * easeP;
    }

    public float getScreenY() {
        float p = getProgress();
        float arc = (float) Math.sin(p * Math.PI) * (toPlayer ? -40f : 40f);
        float easeP = p * p * (3.0f - 2.0f * p);
        return startY + (endY - startY) * easeP + arc;
    }

    public Vector2f getCurrentScreenPos() {
        return new Vector2f(getScreenX(), getScreenY());
    }
}
