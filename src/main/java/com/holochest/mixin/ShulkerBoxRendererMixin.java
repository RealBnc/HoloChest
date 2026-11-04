package com.holochest.mixin;

import com.holochest.hologram.HoloChestManager;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxRenderer.class)
public abstract class ShulkerBoxRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"))
    private void holochest$capLidProgress(ShulkerBoxBlockEntity entity, ShulkerBoxRenderState state, float tickDelta, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfo ci) {
        if (HoloChestManager.getInstance().isContainerAjar(entity.getBlockPos())) {
            float progress = HoloChestManager.getInstance().getContainerOpenProgress(entity.getBlockPos());
            state.progress = 0.07f * progress;
        }
    }
}
