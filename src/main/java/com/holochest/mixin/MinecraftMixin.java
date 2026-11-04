package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Unique
    private static boolean holochest$rightMouseWasPressed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void holochest$onClientTick(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.options != null && !client.options.keyUse.isDown()) {
            holochest$rightMouseWasPressed = false;
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void holochest$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
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

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void holochest$onPickBlock(CallbackInfo ci) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            if (manager.getHoveredSession() != null || manager.isAimingAtAnyPanel()) {
                manager.sortFocusedSession();
                ci.cancel();
            }
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void holochest$onStartUseItem(CallbackInfo ci) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            boolean isPressed = Minecraft.getInstance().options.keyUse.isDown();
            if (manager.getHoveredSlot() != -1) {
                if (!holochest$rightMouseWasPressed) {
                    manager.handleRightClick();
                    holochest$rightMouseWasPressed = true;
                }
                ci.cancel();
            } else if (manager.isAimingAtAnyPanel()) {
                ci.cancel();
            } else {
                Minecraft client = Minecraft.getInstance();
                if (client.hitResult instanceof BlockHitResult bhr
                        && bhr.getType() == HitResult.Type.BLOCK) {
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

    @Inject(method = "setScreenAndShow", at = @At("HEAD"), cancellable = true)
    private void holochest$onSetScreen(Screen screen, CallbackInfo ci) {
        HoloChestManager manager = HoloChestManager.getInstance();
        if (manager.hasActiveSessions()) {
            if (screen instanceof PauseScreen) {
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
