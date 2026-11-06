package com.shieldstun;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class KeybindHandler {
    public static KeyBinding toggleKey;
    public static KeyBinding comboKey;
    private static boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void register() {
        // 1.21.9+: key bindings take a KeyBinding.Category. The translation key becomes
        // "key.category.shieldstun.main" (see lang file).
        KeyBinding.Category category =
                KeyBinding.Category.create(Identifier.of(ShieldStunClient.MOD_ID, "main"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldstun.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, category));
        comboKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shieldstun.combo", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, category));

        ClientTickEvents.END_CLIENT_TICK.register(KeybindHandler::onTick);
    }

    private static void onTick(MinecraftClient mc) {
        // Always drain wasPressed() so presses never pile up.
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            if (!enabled && ShieldStunClient.COMBO.isRunning()) {
                ShieldStunClient.COMBO.abort(mc, null);
            }
            ShieldStunClient.msg(mc, "ShieldStun: " + (enabled ? "ON" : "OFF"));
        }

        boolean trigger = false;
        while (comboKey.wasPressed()) trigger = true;

        if (!enabled) return;

        if (ConfigManager.get().repeatWhileHeld && comboKey.isPressed()) trigger = true;

        ComboStateMachine combo = ShieldStunClient.COMBO;
        if (trigger && !combo.isRunning()) combo.start(mc);
        combo.tick(mc);
    }

    private KeybindHandler() {}
}
