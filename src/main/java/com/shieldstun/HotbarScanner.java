package com.shieldstun;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

public final class HotbarScanner {
    public record Result(int swordSlot, int axeSlot) {}

    /** Scans hotbar slots 0-8; returns -1 for a missing tool. */
    public static Result scan(PlayerInventory inv) {
        int sword = -1, axe = -1;
        long swordScore = Long.MIN_VALUE, axeScore = Long.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.isIn(ItemTags.SWORDS)) {
                long sc = score(s);
                if (sc > swordScore) { swordScore = sc; sword = i; }
            } else if (s.isIn(ItemTags.AXES)) {
                long sc = score(s);
                if (sc > axeScore) { axeScore = sc; axe = i; }
            }
        }
        return new Result(sword, axe);
    }

    /**
     * Ranks by material tier (a good proxy for attack damage), then durability as a tie-breaker.
     * Unknown/modded tools rank 0 but are still selectable.
     */
    private static long score(ItemStack s) {
        String path = Registries.ITEM.getId(s.getItem()).getPath();
        int tier = 0;
        if (path.contains("netherite")) tier = 7;
        else if (path.contains("diamond")) tier = 6;
        else if (path.contains("iron")) tier = 5;
        else if (path.contains("copper")) tier = 4;
        else if (path.contains("stone")) tier = 3;
        else if (path.contains("golden")) tier = 2;
        else if (path.contains("wooden")) tier = 1;
        return tier * 100_000L + s.getMaxDamage();
    }

    private HotbarScanner() {}
}
