package com.holochest;

import com.holochest.config.HoloChestConfig;
import com.holochest.hologram.HoloChestManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HoloChestMod implements ClientModInitializer {

    public static final String MOD_ID = "holochest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding holoChestKey;
    private static KeyBinding holoSortKey;

    @Override
    public void onInitializeClient() {
        HoloChestConfig.load();
        com.holochest.sound.HoloSoundHelper.registerSounds();

        holoChestKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.holochest.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                KeyBinding.Category.MISC
        ));

        holoSortKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.holochest.sort",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (holoChestKey.wasPressed()) {
                HoloChestManager.getInstance().toggle();
            }

            while (holoSortKey.wasPressed()) {
                if (HoloChestManager.getInstance().hasActiveSessions()) {
                    HoloChestManager.getInstance().sortFocusedSession();
                }
            }

            HoloChestManager.getInstance().tick();
        });

        LOGGER.info("HoloChest initialized");
    }

    public static KeyBinding getHoloChestKey() {
        return holoChestKey;
    }

    public static KeyBinding getHoloSortKey() {
        return holoSortKey;
    }
}
