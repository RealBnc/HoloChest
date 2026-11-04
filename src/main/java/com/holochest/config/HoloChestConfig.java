package com.holochest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class HoloChestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "holochest.json");
    private static HoloChestConfig INSTANCE = new HoloChestConfig();

    public boolean enabled = true;
    public int holoGridColor = 0x6600E5FF;
    public int holoGlowColor = 0xAA00B4D8;
    public int slotBorderColor = 0x8890E0EF;
    public int hoverGlowColor = 0xFFFFFFFF;
    public float maxLidAjarAngle = 0.28f; 
    public boolean playHoloSounds = true;

    public static HoloChestConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static HoloChestConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                HoloChestConfig config = GSON.fromJson(reader, HoloChestConfig.class);
                if (config != null) {
                    INSTANCE = config;
                    return config;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        INSTANCE = new HoloChestConfig();
        INSTANCE.save();
        return INSTANCE;
    }

    public void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
