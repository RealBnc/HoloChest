package com.holochest.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class HoloWorldProjector {

    public record ScreenPos(float x, float y, float depth, boolean visible) {}

    private static final Quaternionf TMP_ROT = new Quaternionf();
    private static final Vector3f TMP_VEC = new Vector3f();

    public static ScreenPos project(Vec3d target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.getWindow() == null) {
            return new ScreenPos(0, 0, -1, false);
        }

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) {
            return new ScreenPos(0, 0, -1, false);
        }

        Vec3d camPos = camera.getCameraPos();
        double rx = target.x - camPos.x;
        double ry = target.y - camPos.y;
        double rz = target.z - camPos.z;

        Quaternionf rot = camera.getRotation().conjugate(TMP_ROT);
        Vector3f camSpace = TMP_VEC.set((float) rx, (float) ry, (float) rz);
        rot.transform(camSpace);

        if (camSpace.z >= -0.1f) {
            return new ScreenPos(0, 0, -1, false);
        }

        float depth = -camSpace.z;

        double fovDegrees = client.options.getFov().getValue();
        float halfHeight = client.getWindow().getScaledHeight() / 2f;
        float halfWidth = client.getWindow().getScaledWidth() / 2f;
        float fovRad = (float) Math.toRadians(fovDegrees);
        float tanHalfFov = (float) Math.tan(fovRad / 2.0);

        float fovFactor = halfHeight / tanHalfFov;

        float screenX = halfWidth + (camSpace.x / depth) * fovFactor;
        float screenY = halfHeight - (camSpace.y / depth) * fovFactor;

        float screenW = halfWidth * 2f;
        float screenH = halfHeight * 2f;
        float margin = 80f;
        if (screenX < -margin || screenX > screenW + margin || screenY < -margin || screenY > screenH + margin) {
            return new ScreenPos(screenX, screenY, depth, false);
        }

        return new ScreenPos(screenX, screenY, depth, true);
    }
}
