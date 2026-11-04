package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import com.holochest.render.HoloHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void holochest$renderHolo(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (HoloChestManager.getInstance().hasActiveSessions() || !HoloChestManager.getInstance().getFlyingItems().isEmpty()) {
            HoloHudRenderer.render(context, tickCounter);
        }
    }
}
