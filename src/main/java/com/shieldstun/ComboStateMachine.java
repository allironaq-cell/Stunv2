package com.shieldstun;

import com.shieldstun.ConfigManager.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * IDLE -> SWORD_SWITCH -> SWORD_HIT -> AXE_SWITCH -> AXE_HIT -> RESTORE -> IDLE
 *
 * COOLDOWN NOTES (vanilla 1.9+ combat):
 *  - Every attack resets the attack-cooldown meter, and changing the held item also resets it,
 *    so after "sword hit -> switch to axe" the meter is near 0 again.
 *  - With waitForCooldown=true we only attack once getAttackCooldownProgress(0.5f) >= cooldownThreshold.
 *    Hits are full-strength (shield disable + full damage) but the combo takes noticeably longer.
 *  - With forceFastMode=true the cooldown is ignored and hits go out at stepDelayTicks pace.
 *    Fast, but those hits are weak (reduced damage/knockback). Axe shield-disable still applies
 *    only if the server accepts the hit as an attack on a blocking target.
 *
 * SPRINT / CRIT NOTES:
 *  - Sprint knockback needs the server to see you sprinting at hit time. sprintReset re-sends
 *    STOP/START sprint so the server state matches before the sword hit.
 *  - Vanilla crits are NOT possible while sprinting, so when criticalHit=true the sprint reset
 *    is skipped (the two options are mutually exclusive in practice).
 *  - The mod cannot make the enemy jump; it only makes the sword hit as strong as possible.
 */
public final class ComboStateMachine {
    public enum State { IDLE, SWORD_SWITCH, SWORD_HIT, AXE_SWITCH, AXE_HIT, RESTORE }

    private static final double REACH = 3.0;
    private static final int MAX_WAIT_TICKS = 40; // give up waiting for cooldown / crit

    private State state = State.IDLE;
    private LivingEntity target;
    private int originalSlot, swordSlot = -1, axeSlot = -1;
    private int delay, waited;
    private boolean wasSprinting, slotChanged;

    public boolean isRunning() {
        return state != State.IDLE;
    }

    // ---------------------------------------------------------------- start

    public void start(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null || mc.currentScreen != null || !p.isAlive()) return;

        Config cfg = ConfigManager.get();
        HotbarScanner.Result r = HotbarScanner.scan(p.getInventory());
        boolean ok = r.axeSlot() >= 0 && (r.swordSlot() >= 0 || cfg.axeOnlyMode);
        if (!ok) {
            ShieldStunClient.msg(mc, "No sword/axe in hotbar");
            return;
        }

        LivingEntity t = findTarget(mc, p, cfg);
        if (t == null) {
            ShieldStunClient.msg(mc, "ShieldStun: no target");
            return;
        }

        target = t;
        swordSlot = r.swordSlot();
        axeSlot = r.axeSlot();
        originalSlot = p.getInventory().getSelectedSlot();
        wasSprinting = p.isSprinting();
        slotChanged = false;
        delay = 0;
        waited = 0;
        state = (swordSlot >= 0) ? State.SWORD_SWITCH : State.AXE_SWITCH;
    }

    // ----------------------------------------------------------------- tick

    public void tick(MinecraftClient mc) {
        if (state == State.IDLE) return;

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) { reset(); return; }
        if (mc.currentScreen != null || !p.isAlive() || target == null || !target.isAlive()
                || target.isRemoved() || reachDistance(p, target) > REACH) {
            abort(mc, "ShieldStun: combo aborted");
            return;
        }

        if (delay > 0) { delay--; return; }
        Config cfg = ConfigManager.get();

        // One call = one step. With mergeSwitchAndHit (and stepDelayTicks=0) a slot switch is followed by
        // its hit in the same tick, so the whole combo takes 3 ticks:
        //   tick 1: sword slot + hit | tick 2: axe slot + hit | tick 3: restore slot.
        // Each tick still renders at least one frame, so the hotbar/hand visibly change every time.
        boolean again;
        do {
            again = step(mc, p, cfg);
        } while (again && state != State.IDLE);
    }

    /** @return true if the next step may run in the same tick. */
    private boolean step(MinecraftClient mc, ClientPlayerEntity p, Config cfg) {
        boolean merge = cfg.mergeSwitchAndHit && cfg.stepDelayTicks == 0;
        switch (state) {
            case SWORD_SWITCH -> {
                selectSlot(mc, p, swordSlot);
                next(State.SWORD_HIT, cfg);
                return merge;
            }
            case SWORD_HIT -> {
                if (!ready(mc, p, cfg)) return false;
                if (cfg.sprintReset && !cfg.criticalHit) sprintReset(mc, p);
                attack(mc, p);
                next(State.AXE_SWITCH, cfg);
                return false; // let the sword slot be shown for a tick
            }
            case AXE_SWITCH -> {
                selectSlot(mc, p, axeSlot);
                next(State.AXE_HIT, cfg);
                return merge;
            }
            case AXE_HIT -> {
                if (!ready(mc, p, cfg)) return false;
                attack(mc, p);
                next(State.RESTORE, cfg);
                return false; // let the axe slot be shown for a tick
            }
            case RESTORE -> {
                if (cfg.restoreSlot) selectSlot(mc, p, originalSlot);
                reset();
                return false;
            }
            default -> {
                reset();
                return false;
            }
        }
    }

    public void abort(MinecraftClient mc, String reason) {
        ClientPlayerEntity p = mc.player;
        if (p != null && mc.getNetworkHandler() != null && slotChanged && ConfigManager.get().restoreSlot) {
            selectSlot(mc, p, originalSlot);
        }
        if (reason != null) ShieldStunClient.msg(mc, reason);
        reset();
    }

    // -------------------------------------------------------------- helpers

    private void next(State s, Config cfg) {
        state = s;
        delay = cfg.stepDelayTicks;
        waited = 0;
    }

    private void reset() {
        state = State.IDLE;
        target = null;
        slotChanged = false;
    }

    /** True when a hit may be sent this tick; otherwise counts toward a timeout. */
    private boolean ready(MinecraftClient mc, ClientPlayerEntity p, Config cfg) {
        boolean cooldownOk = cfg.forceFastMode || !cfg.waitForCooldown
                || p.getAttackCooldownProgress(0.5f) >= cfg.cooldownThreshold;
        boolean critOk = !cfg.criticalHit || canCrit(p);
        if (cooldownOk && critOk) { waited = 0; return true; }
        if (++waited > MAX_WAIT_TICKS) abort(mc, "ShieldStun: timed out waiting for hit conditions");
        return false;
    }

    private static boolean canCrit(ClientPlayerEntity p) {
        return !p.isOnGround() && p.getVelocity().y < 0 && !p.isClimbing()
                && !p.isTouchingWater() && !p.hasVehicle() && !p.isSprinting();
    }

    /** Real, visible slot change: client inventory + C2S packet in the same tick. */
    private void selectSlot(MinecraftClient mc, ClientPlayerEntity p, int slot) {
        if (p.getInventory().getSelectedSlot() == slot) return;
        p.getInventory().setSelectedSlot(slot);
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh != null) nh.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        slotChanged = true;
    }

    private void attack(MinecraftClient mc, ClientPlayerEntity p) {
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND); // visible first-person swing
    }

    private void sprintReset(MinecraftClient mc, ClientPlayerEntity p) {
        boolean wantSprint = wasSprinting
                || (mc.options.forwardKey.isPressed() && p.getHungerManager().getFoodLevel() > 6);
        if (!wantSprint) return;
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh == null) return;
        nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        nh.sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_SPRINTING));
        p.setSprinting(true);
    }

    // ------------------------------------------------------------ targeting

    /** Eye position -> closest point of the target's hitbox. */
    private static double reachDistance(PlayerEntity p, Entity e) {
        Vec3d eye = p.getEyePos();
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - eye.x, 0), eye.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - eye.y, 0), eye.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - eye.z, 0), eye.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static LivingEntity findTarget(MinecraftClient mc, ClientPlayerEntity p, Config cfg) {
        LivingEntity crosshair = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != p && le.isAlive() && reachDistance(p, le) <= REACH) {
            crosshair = le;
        }

        // Fallback pool: closest players inside a cone around the look direction.
        Vec3d look = p.getRotationVec(1.0f).normalize();
        double minDot = Math.cos(Math.toRadians(cfg.targetFovDegrees));
        List<? extends PlayerEntity> players = mc.world.getPlayers();
        PlayerEntity bestBlocking = null, bestAny = null;
        double dBlock = Double.MAX_VALUE, dAny = Double.MAX_VALUE;
        for (PlayerEntity o : players) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            double d = reachDistance(p, o);
            if (d > REACH) continue;
            Vec3d to = o.getBoundingBox().getCenter().subtract(p.getEyePos()).normalize();
            if (look.dotProduct(to) < minDot) continue;
            if (o.isBlocking() && d < dBlock) { dBlock = d; bestBlocking = o; }
            if (d < dAny) { dAny = d; bestAny = o; }
        }

        LivingEntity chosen;
        if (crosshair != null && (crosshair.isBlocking() || bestBlocking == null)) chosen = crosshair;
        else if (bestBlocking != null) chosen = bestBlocking;
        else chosen = (crosshair != null) ? crosshair : bestAny;

        if (chosen != null && cfg.onlyWhenTargetBlocking && !chosen.isBlocking()) return null;
        return chosen;
    }
}
