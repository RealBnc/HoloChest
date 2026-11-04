package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void holochest$suppressSneakWhenAiming(boolean slowDown, float f, CallbackInfo ci) {
        if (HoloChestManager.getInstance().isAimingAtAnyPanel()) {
            this.sneaking = false;
        }
    }
}
