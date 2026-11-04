package com.holochest.sound;

import com.holochest.config.HoloChestConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class HoloSoundHelper {

    public static final Identifier HOLO_OPEN_ID = Identifier.fromNamespaceAndPath("holochest", "holo_open");
    public static final SoundEvent HOLO_OPEN = SoundEvent.createVariableRangeEvent(HOLO_OPEN_ID);

    public static final Identifier HOLO_OPEN_50_ID = Identifier.fromNamespaceAndPath("holochest", "holo_open_50");
    public static final SoundEvent HOLO_OPEN_50 = SoundEvent.createVariableRangeEvent(HOLO_OPEN_50_ID);

    public static final Identifier HOLO_CLOSE_ID = Identifier.fromNamespaceAndPath("holochest", "holo_close");
    public static final SoundEvent HOLO_CLOSE = SoundEvent.createVariableRangeEvent(HOLO_CLOSE_ID);

    public static final Identifier HOLO_CLOSE_75_ID = Identifier.fromNamespaceAndPath("holochest", "holo_close_75");
    public static final SoundEvent HOLO_CLOSE_75 = SoundEvent.createVariableRangeEvent(HOLO_CLOSE_75_ID);

    public static final Identifier HOLO_CLOSE_50_ID = Identifier.fromNamespaceAndPath("holochest", "holo_close_50");
    public static final SoundEvent HOLO_CLOSE_50 = SoundEvent.createVariableRangeEvent(HOLO_CLOSE_50_ID);

    public static final Identifier HOLO_CLOSE_25_ID = Identifier.fromNamespaceAndPath("holochest", "holo_close_25");
    public static final SoundEvent HOLO_CLOSE_25 = SoundEvent.createVariableRangeEvent(HOLO_CLOSE_25_ID);

    public static final Identifier HOLO_SORT_ID = Identifier.fromNamespaceAndPath("holochest", "holo_sort");
    public static final SoundEvent HOLO_SORT = SoundEvent.createVariableRangeEvent(HOLO_SORT_ID);

    private static SoundInstance currentHoloSound = null;

    public static void registerSounds() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_OPEN_ID, HOLO_OPEN);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_OPEN_50_ID, HOLO_OPEN_50);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_CLOSE_ID, HOLO_CLOSE);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_CLOSE_75_ID, HOLO_CLOSE_75);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_CLOSE_50_ID, HOLO_CLOSE_50);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_CLOSE_25_ID, HOLO_CLOSE_25);
        Registry.register(BuiltInRegistries.SOUND_EVENT, HOLO_SORT_ID, HOLO_SORT);
    }

    public static void stopHoloSounds() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        SoundManager sm = client.getSoundManager();
        if (sm == null) return;

        if (currentHoloSound != null) {
            sm.stop(currentHoloSound);
            currentHoloSound = null;
        }

        sm.stop(HOLO_OPEN_ID, SoundSource.UI);
        sm.stop(HOLO_OPEN_50_ID, SoundSource.UI);
        sm.stop(HOLO_CLOSE_ID, SoundSource.UI);
        sm.stop(HOLO_CLOSE_75_ID, SoundSource.UI);
        sm.stop(HOLO_CLOSE_50_ID, SoundSource.UI);
        sm.stop(HOLO_CLOSE_25_ID, SoundSource.UI);
        sm.stop(HOLO_OPEN_ID, null);
        sm.stop(HOLO_OPEN_50_ID, null);
        sm.stop(HOLO_CLOSE_ID, null);
        sm.stop(HOLO_CLOSE_75_ID, null);
        sm.stop(HOLO_CLOSE_50_ID, null);
        sm.stop(HOLO_CLOSE_25_ID, null);
    }

    public static void playHoloOpen(float currentProgress) {
        if (!HoloChestConfig.get().playHoloSounds) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        stopHoloSounds();

        SoundEvent soundToPlay = (currentProgress >= 0.40f) ? HOLO_OPEN_50 : HOLO_OPEN;
        SimpleSoundInstance instance = SimpleSoundInstance.forUI(soundToPlay, 1.0f, 0.55f);
        currentHoloSound = instance;
        client.getSoundManager().play(instance);
    }

    public static void playHoloOpen() {
        playHoloOpen(0f);
    }

    public static void playHoloClose(float currentOpenProgress) {
        if (!HoloChestConfig.get().playHoloSounds) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        stopHoloSounds();

        SoundEvent soundToPlay;
        if (currentOpenProgress >= 0.75f) {
            soundToPlay = HOLO_CLOSE;
        } else if (currentOpenProgress >= 0.50f) {
            soundToPlay = HOLO_CLOSE_75;
        } else if (currentOpenProgress >= 0.25f) {
            soundToPlay = HOLO_CLOSE_50;
        } else {
            soundToPlay = HOLO_CLOSE_25;
        }

        SimpleSoundInstance instance = SimpleSoundInstance.forUI(soundToPlay, 1.0f, 0.55f);
        currentHoloSound = instance;
        client.getSoundManager().play(instance);
    }

    public static void playHoloClose() {
        playHoloClose(1.0f);
    }

    public static void playHoloSort() {
        if (!HoloChestConfig.get().playHoloSounds) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        client.getSoundManager().play(SimpleSoundInstance.forUI(HOLO_SORT, 1.0f, 0.55f));
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.9f, 0.35f));
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_DROP_CONTENTS, 1.3f, 0.30f));
    }

    public static void playSlotHover() {
        if (!HoloChestConfig.get().playHoloSounds) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0f, 0.12f));
    }

    public static void playItemFlight() {
        if (!HoloChestConfig.get().playHoloSounds) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.4f, 0.30f));
    }
}
