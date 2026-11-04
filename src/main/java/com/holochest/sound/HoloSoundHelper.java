package com.holochest.sound;

import com.holochest.config.HoloChestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

public class HoloSoundHelper {

    public static final Identifier HOLO_OPEN_ID = Identifier.of("holochest", "holo_open");
    public static final SoundEvent HOLO_OPEN = SoundEvent.of(HOLO_OPEN_ID);

    public static final Identifier HOLO_OPEN_50_ID = Identifier.of("holochest", "holo_open_50");
    public static final SoundEvent HOLO_OPEN_50 = SoundEvent.of(HOLO_OPEN_50_ID);

    public static final Identifier HOLO_CLOSE_ID = Identifier.of("holochest", "holo_close");
    public static final SoundEvent HOLO_CLOSE = SoundEvent.of(HOLO_CLOSE_ID);

    public static final Identifier HOLO_CLOSE_75_ID = Identifier.of("holochest", "holo_close_75");
    public static final SoundEvent HOLO_CLOSE_75 = SoundEvent.of(HOLO_CLOSE_75_ID);

    public static final Identifier HOLO_CLOSE_50_ID = Identifier.of("holochest", "holo_close_50");
    public static final SoundEvent HOLO_CLOSE_50 = SoundEvent.of(HOLO_CLOSE_50_ID);

    public static final Identifier HOLO_CLOSE_25_ID = Identifier.of("holochest", "holo_close_25");
    public static final SoundEvent HOLO_CLOSE_25 = SoundEvent.of(HOLO_CLOSE_25_ID);

    public static final Identifier HOLO_SORT_ID = Identifier.of("holochest", "holo_sort");
    public static final SoundEvent HOLO_SORT = SoundEvent.of(HOLO_SORT_ID);

    private static SoundInstance currentHoloSound = null;

    public static void registerSounds() {
        Registry.register(Registries.SOUND_EVENT, HOLO_OPEN_ID, HOLO_OPEN);
        Registry.register(Registries.SOUND_EVENT, HOLO_OPEN_50_ID, HOLO_OPEN_50);
        Registry.register(Registries.SOUND_EVENT, HOLO_CLOSE_ID, HOLO_CLOSE);
        Registry.register(Registries.SOUND_EVENT, HOLO_CLOSE_75_ID, HOLO_CLOSE_75);
        Registry.register(Registries.SOUND_EVENT, HOLO_CLOSE_50_ID, HOLO_CLOSE_50);
        Registry.register(Registries.SOUND_EVENT, HOLO_CLOSE_25_ID, HOLO_CLOSE_25);
        Registry.register(Registries.SOUND_EVENT, HOLO_SORT_ID, HOLO_SORT);
    }

    private static PositionedSoundInstance createUiSound(SoundEvent sound, float volume, float pitch) {
        return new PositionedSoundInstance(
                sound.id(),
                SoundCategory.PLAYERS,
                volume,
                pitch,
                SoundInstance.createRandom(),
                false,
                0,
                SoundInstance.AttenuationType.NONE,
                0.0,
                0.0,
                0.0,
                true
        );
    }

    public static void stopHoloSounds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        SoundManager sm = client.getSoundManager();
        if (sm == null) return;

        if (currentHoloSound != null) {
            sm.stop(currentHoloSound);
            currentHoloSound = null;
        }

        sm.stopSounds(HOLO_OPEN_ID, SoundCategory.PLAYERS);
        sm.stopSounds(HOLO_OPEN_50_ID, SoundCategory.PLAYERS);
        sm.stopSounds(HOLO_CLOSE_ID, SoundCategory.PLAYERS);
        sm.stopSounds(HOLO_CLOSE_75_ID, SoundCategory.PLAYERS);
        sm.stopSounds(HOLO_CLOSE_50_ID, SoundCategory.PLAYERS);
        sm.stopSounds(HOLO_CLOSE_25_ID, SoundCategory.PLAYERS);
        sm.stopSounds(HOLO_OPEN_ID, null);
        sm.stopSounds(HOLO_OPEN_50_ID, null);
        sm.stopSounds(HOLO_CLOSE_ID, null);
        sm.stopSounds(HOLO_CLOSE_75_ID, null);
        sm.stopSounds(HOLO_CLOSE_50_ID, null);
        sm.stopSounds(HOLO_CLOSE_25_ID, null);
    }

    public static void playHoloOpen(float currentProgress) {
        if (!HoloChestConfig.get().playHoloSounds) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        stopHoloSounds();

        SoundEvent soundToPlay = (currentProgress >= 0.40f) ? HOLO_OPEN_50 : HOLO_OPEN;
        PositionedSoundInstance instance = createUiSound(soundToPlay, 0.55f, 1.0f);
        currentHoloSound = instance;
        client.getSoundManager().play(instance);
    }

    public static void playHoloOpen() {
        playHoloOpen(0f);
    }

    public static void playHoloClose(float currentOpenProgress) {
        if (!HoloChestConfig.get().playHoloSounds) return;
        MinecraftClient client = MinecraftClient.getInstance();
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

        PositionedSoundInstance instance = createUiSound(soundToPlay, 0.55f, 1.0f);
        currentHoloSound = instance;
        client.getSoundManager().play(instance);
    }

    public static void playHoloClose() {
        playHoloClose(1.0f);
    }

    public static void playHoloSort() {
        if (!HoloChestConfig.get().playHoloSounds) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        client.getSoundManager().play(createUiSound(HOLO_SORT, 0.55f, 1.0f));
        client.getSoundManager().play(createUiSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.9f));
        client.getSoundManager().play(createUiSound(SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, 0.30f, 1.3f));
    }

    public static void playSlotHover() {
        if (!HoloChestConfig.get().playHoloSounds) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        client.getSoundManager().play(createUiSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.12f, 2.0f));
    }

    public static void playItemFlight() {
        if (!HoloChestConfig.get().playHoloSounds) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        client.getSoundManager().play(createUiSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.30f, 1.4f));
    }
}
