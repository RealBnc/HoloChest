package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Unique
    private static boolean holochest$rightMouseWasPressed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void holochest$onClientTick(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null && !client.options.useKey.isPressed()) {
            holochest$rightMouseWasPressed = false;
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void holochest$onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            if (manager.getHoveredSession() != null && manager.getHoveredSession().isSortButtonHovered()) {
                manager.sortSession(manager.getHoveredSession());
                cir.setReturnValue(false);
            } else if (manager.getHoveredSlot() != -1) {
                manager.handleLeftClick();
                cir.setReturnValue(false);
            } else if (manager.isAimingAtAnyPanel()) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "doItemPick", at = @At("HEAD"), cancellable = true)
    private void holochest$onDoItemPick(CallbackInfo ci) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            if (manager.getHoveredSession() != null || manager.isAimingAtAnyPanel()) {
                manager.sortFocusedSession();
                ci.cancel();
            }
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void holochest$onDoItemUse(CallbackInfo ci) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            boolean isPressed = MinecraftClient.getInstance().options.useKey.isPressed();
            if (manager.getHoveredSlot() != -1) {
                if (!holochest$rightMouseWasPressed) {
                    manager.handleRightClick();
                    holochest$rightMouseWasPressed = true;
                }
                ci.cancel();
            } else if (manager.isAimingAtAnyPanel()) {
                ci.cancel();
            } else {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr
                        && bhr.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    if (manager.isContainerAjar(bhr.getBlockPos())) {
                        ci.cancel();
                    }
                }
            }
            if (isPressed) {
                holochest$rightMouseWasPressed = true;
            }
        } else {
            holochest$rightMouseWasPressed = false;
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void holochest$onSetScreen(Screen screen, CallbackInfo ci) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            if (screen instanceof GameMenuScreen) {
                if (manager.hasAnyNonClosingSession()) {
                    manager.startClosingAll();
                    ci.cancel();
                } else {
                    manager.forceCloseAll();
                }
            } else if (screen != null) {
                manager.startClosingAll();
            }
        }
    }
}
