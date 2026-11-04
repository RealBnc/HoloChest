package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void holochest$onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (hitResult != null) {
            HoloChestManager manager = HoloChestManager.getInstance();
            if (manager.hasActiveSessions() && (manager.isAimingAtAnyPanel() || manager.isContainerAjar(hitResult.getBlockPos()))) {
                cir.setReturnValue(ActionResult.SUCCESS);
                return;
            }
            manager.recordBlockInteraction(hitResult.getBlockPos());
        }
    }
}
