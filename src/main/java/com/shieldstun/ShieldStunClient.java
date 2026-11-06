package com.shieldstun;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ShieldStunClient implements ClientModInitializer {
    public static final String MOD_ID = "shieldstun";
    public static final ComboStateMachine COMBO = new ComboStateMachine();

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        KeybindHandler.register();
    }

    /** Action-bar message, respects the "showMessages" config option. */
    public static void msg(MinecraftClient mc, String text) {
        if (mc.player != null && ConfigManager.get().showMessages) {
            mc.player.sendMessage(Text.literal(text), true);
        }
    }
}
