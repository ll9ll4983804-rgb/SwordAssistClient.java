package com.swordassist;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public class SwordAssistClient implements ClientModInitializer {

    private static KeyMapping toggleKey;
    private static boolean enabled = false;

    private long nextActionTime = 0L;
    private long lastMaceOrAirTime = 0L;
    private boolean awaitingLandingSwitch = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.swordassist.toggle",
                GLFW.GLFW_KEY_UNKNOWN,
                "category.swordassist.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        while (toggleKey.consumeClick()) {
            enabled = !enabled;
        }

        if (!enabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionTime) {
            return;
        }

        boolean isAirborneOrGliding = player.isFallFlying() || (!player.onGround() && player.getDeltaMovement().y < -0.25D);
        if (isHoldingMace(player) && isAirborneOrGliding) {
            awaitingLandingSwitch = true;
            lastMaceOrAirTime = now;
        }

        LivingEntity target = getNearestTarget(client, 4.5D);
        boolean landedAfterMace = player.onGround() && (awaitingLandingSwitch || (isHoldingMace(player) && now - lastMaceOrAirTime < 1000L));
        boolean missedMaceOnGround = awaitingLandingSwitch && !player.isFallFlying() && target != null && player.distanceToSqr(target) <= 16.0D;

        if (landedAfterMace || missedMaceOnGround) {
            int swordSlot = findSwordHotbarSlot(player);
            if (swordSlot != -1 && player.getInventory().selected != swordSlot) {
                player.getInventory().selected = swordSlot;
                nextActionTime = now + ThreadLocalRandom.current().nextLong(35, 80);
            }
            awaitingLandingSwitch = false;
        }

        if (isHoldingSword(player) && target != null && client.gameMode != null) {
            float cooldown = player.getAttackStrengthScale(0.5F);
            if (cooldown >= 0.92F) {
                client.gameMode.attack(player, target);
                player.swing(InteractionHand.MAIN_HAND);
                nextActionTime = now + ThreadLocalRandom.current().nextLong(35, 80);
            }
        }
    }

    private int findSwordHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof SwordItem) {
                return i;
            }
        }
        return -1;
    }

    private boolean isHoldingSword(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getItem() instanceof SwordItem;
    }

    private boolean isHoldingMace(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() && main.getDescriptionId().endsWith(".mace");
    }

    private LivingEntity getNearestTarget(Minecraft client, double range) {
        LivingEntity nearest = null;
        double nearestDistSq = range * range;
        for (LivingEntity entity : client.level.players()) {
            if (entity != client.player && entity.isAlive()) {
                double distSq = client.player.distanceToSqr(entity);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = entity;
                }
            }
        }
        return nearest;
    }
}
