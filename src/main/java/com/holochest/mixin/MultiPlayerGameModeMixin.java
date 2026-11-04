package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void holochest$onInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (hitResult != null) {
            HoloChestManager manager = HoloChestManager.getInstance();
            if (manager.hasActiveSessions() && (manager.isAimingAtAnyPanel() || manager.isContainerAjar(hitResult.getBlockPos()))) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}
