package com.holochest;

import com.holochest.config.HoloChestConfig;
import com.holochest.hologram.HoloChestManager;
import com.holochest.render.HoloHudRenderer;
import com.holochest.sound.HoloSoundHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HoloChestMod implements ClientModInitializer {

    public static final String MOD_ID = "holochest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyMapping holoChestKey;
    private static KeyMapping holoSortKey;

    private static InputConstants.Type getKeyType() {
        try {
            return InputConstants.Type.valueOf("KEYBOARD");
        } catch (Throwable t) {
            try {
                return InputConstants.Type.valueOf("KEYSYM");
            } catch (Throwable t2) {
                return InputConstants.Type.values()[0];
            }
        }
    }

    @Override
    public void onInitializeClient() {
        HoloChestConfig.load();
        HoloSoundHelper.registerSounds();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("holochest", "category"));
        InputConstants.Type keyType = getKeyType();

        holoChestKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.holochest.open",
                keyType,
                InputConstants.KEY_V,
                category
        ));

        holoSortKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.holochest.sort",
                keyType,
                InputConstants.KEY_R,
                category
        ));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("holochest", "hud"), (extractor, deltaTracker) -> {
            if (HoloChestManager.getInstance().hasActiveSessions() || !HoloChestManager.getInstance().getFlyingItems().isEmpty()) {
                HoloHudRenderer.render(extractor, deltaTracker);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (holoChestKey.consumeClick()) {
                HoloChestManager.getInstance().toggle();
            }

            while (holoSortKey.consumeClick()) {
                if (HoloChestManager.getInstance().hasActiveSessions()) {
                    HoloChestManager.getInstance().sortFocusedSession();
                }
            }

            HoloChestManager.getInstance().tick();
        });

        LOGGER.info("HoloChest initialized");
    }

    public static KeyMapping getHoloChestKey() {
        return holoChestKey;
    }

    public static KeyMapping getHoloSortKey() {
        return holoSortKey;
    }
}
