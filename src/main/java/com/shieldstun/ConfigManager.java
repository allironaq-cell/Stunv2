package com.shieldstun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    public static class Config {
        public int stepDelayTicks = 0;            // idle ticks between steps (min 0)
        public boolean waitForCooldown = false;   // only hit when cooldown >= cooldownThreshold
        public double cooldownThreshold = 0.9;
        public boolean forceFastMode = true;      // ignore cooldown, hit at stepDelayTicks pace
        public boolean restoreSlot = true;
        public boolean sprintReset = true;
        public boolean criticalHit = false;       // only hit while falling (crit)
        public boolean onlyWhenTargetBlocking = false;
        public boolean repeatWhileHeld = false;
        public boolean showMessages = true;
        public boolean mergeSwitchAndHit = true;  // switch + hit in the SAME tick (3 ticks total, hotbar still visibly changes)
        public boolean axeOnlyMode = false;       // allow combo with just an axe
        public double targetFovDegrees = 40.0;    // half-angle for the "nearest player" fallback
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config config = new Config();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("shieldstun.json");
    }

    public static Config get() {
        return config;
    }

    public static void load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                Config loaded = GSON.fromJson(Files.readString(p), Config.class);
                if (loaded != null) config = loaded;
            }
        } catch (Exception e) {
            System.err.println("[ShieldStun] Failed to read config, using defaults: " + e);
        }
        config.stepDelayTicks = Math.max(0, config.stepDelayTicks);
        config.cooldownThreshold = Math.min(1.0, Math.max(0.0, config.cooldownThreshold));
        config.targetFovDegrees = Math.min(180.0, Math.max(1.0, config.targetFovDegrees));
        save(); // writes defaults / newly added fields
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(config));
        } catch (IOException e) {
            System.err.println("[ShieldStun] Failed to write config: " + e);
        }
    }

    private ConfigManager() {}
}
