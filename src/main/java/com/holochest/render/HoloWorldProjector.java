package com.holochest.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class HoloWorldProjector {

    public record ScreenPos(float x, float y, float depth, boolean visible) {}

    private static final Quaternionf TMP_ROT = new Quaternionf();
    private static final Vector3f TMP_VEC = new Vector3f();

    private static final java.lang.invoke.MethodHandle CAMERA_GETTER = resolveCameraGetter();

    private static java.lang.invoke.MethodHandle resolveCameraGetter() {
        try {
            java.lang.reflect.Method m = net.minecraft.client.renderer.GameRenderer.class.getMethod("mainCamera");
            return java.lang.invoke.MethodHandles.lookup().unreflect(m);
        } catch (Throwable t) {
            try {
                java.lang.reflect.Method m2 = net.minecraft.client.renderer.GameRenderer.class.getMethod("getMainCamera");
                return java.lang.invoke.MethodHandles.lookup().unreflect(m2);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    public static Camera getCamera(net.minecraft.client.renderer.GameRenderer renderer) {
        if (renderer == null) return null;
        try {
            if (CAMERA_GETTER != null) {
                return (Camera) CAMERA_GETTER.invoke(renderer);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static ScreenPos project(Vec3 target) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || client.getWindow() == null) {
            return new ScreenPos(0, 0, -1, false);
        }

        Camera camera = getCamera(client.gameRenderer);
        if (camera == null) {
            return new ScreenPos(0, 0, -1, false);
        }

        Vec3 camPos = camera.position();
        double rx = target.x - camPos.x;
        double ry = target.y - camPos.y;
        double rz = target.z - camPos.z;

        Quaternionf rot = camera.rotation().conjugate(TMP_ROT);
        Vector3f camSpace = TMP_VEC.set((float) rx, (float) ry, (float) rz);
        rot.transform(camSpace);

        if (camSpace.z >= -0.1f) {
            return new ScreenPos(0, 0, -1, false);
        }

        float depth = -camSpace.z;

        double fovDegrees = client.options.fov().get();
        float halfHeight = client.getWindow().getGuiScaledHeight() / 2f;
        float halfWidth = client.getWindow().getGuiScaledWidth() / 2f;
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
