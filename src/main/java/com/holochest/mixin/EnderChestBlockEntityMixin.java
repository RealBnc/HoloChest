package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestBlockEntityMixin {

    @Inject(method = "getAnimationProgress", at = @At("RETURN"), cancellable = true)
    private void holochest$capLidProgress(float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (HoloChestManager.getInstance().hasActiveSessions()) {
            EnderChestBlockEntity self = (EnderChestBlockEntity) (Object) this;
            float progress = HoloChestManager.getInstance().getContainerOpenProgress(self.getPos());
            if (progress > 0f) {
                cir.setReturnValue(0.07f * progress);
            }
        }
    }
}
