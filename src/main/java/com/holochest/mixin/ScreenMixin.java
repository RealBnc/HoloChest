package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import com.holochest.render.HoloHudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void holochest$renderBehindScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (HoloChestManager.getInstance().hasActiveSessions()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getRenderTickCounter() != null) {
                HoloHudRenderer.render(context, client.getRenderTickCounter());
            }
        }
    }
}
