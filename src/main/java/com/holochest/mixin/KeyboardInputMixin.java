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
    private void holochest$suppressSneakWhenAiming(CallbackInfo ci) {
        if (HoloChestManager.getInstance().isAimingAtAnyPanel() && this.playerInput != null) {
            this.playerInput = new net.minecraft.util.PlayerInput(playerInput.forward(), playerInput.backward(), playerInput.left(), playerInput.right(), playerInput.jump(), false, playerInput.sprint());
        }
    }
}
