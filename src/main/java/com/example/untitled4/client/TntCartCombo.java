package com.example.untitled4.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

public class TntCartCombo implements ClientModInitializer {

    private static KeyBinding activateKey;
    private static int tickTimer = -1;
    private static int step = 0;

    private static int railSlot = -1;
    private static int bowSlot = -1;
    private static int cartSlot = -1;
    private static int requiredBowHoldTicks = 3;
    private static BlockHitResult initialTargetHit;
    private static BlockPos placedRailPos;
    private static boolean shouldPlaceRail = true;
    private static boolean onlyShootMinecart = false;
    private static boolean entityTargetMode = false;

    @Override
    public void onInitializeClient() {
        activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tntcartcombo.activate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.tntcartcombo"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            PlayerEntity player = client.player;

            if (activateKey.wasPressed() && tickTimer < 0) {
                findSlots(player);
                if (bowSlot == -1) {
                    System.out.println("[TNTCartCombo] You need a bow in your hotbar!");
                    return;
                }

                HitResult target = client.crosshairTarget;

                if (target instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof AbstractMinecartEntity cart) {
                    if (cart.getContainedBlock().getBlock() == Blocks.TNT) {
                        tickTimer = 0;
                        step = 100;
                        onlyShootMinecart = true;

                        Vec3d eye = player.getPos().add(0, player.getStandingEyeHeight(), 0);
                        Vec3d cartCenter = cart.getPos().add(0, cart.getBoundingBox().getLengthY() / 2, 0);
                        double dist = eye.distanceTo(cartCenter);
                        requiredBowHoldTicks = (dist <= 3) ? 4 : 5;

                        Box box = cart.getBoundingBox();
                        float playerYaw = player.getYaw();
                        Vec3d bestPoint = null;
                        float closestYawDiff = Float.MAX_VALUE;

                        for (double x = box.minX; x <= box.maxX; x += 0.1) {
                            for (double z = box.minZ; z <= box.maxZ; z += 0.1) {
                                Vec3d point = new Vec3d(x, cart.getY(), z);
                                double dx = point.x - eye.x;
                                double dz = point.z - eye.z;
                                float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                                float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - playerYaw));
                                if (yawDiff < closestYawDiff) {
                                    closestYawDiff = yawDiff;
                                    bestPoint = point;
                                }
                            }
                        }

                        if (bestPoint != null) {
                            double dx = bestPoint.x - eye.x;
                            double dz = bestPoint.z - eye.z;
                            float bestYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                            player.setYaw(bestYaw);
                        }

                        return;
                    }
                }

                if (target instanceof EntityHitResult entityHit2 && entityHit2.getEntity() instanceof LivingEntity living) {
                    entityTargetMode = true;
                    BlockPos basePos = living.getBlockPos();
                    BlockPos closestSide = basePos;
                    double minDist = Double.MAX_VALUE;

                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        BlockPos candidate = basePos.offset(dir);
                        if (canPlaceRail(client, candidate)) {
                            double dist = player.getPos().squaredDistanceTo(Vec3d.ofCenter(candidate));
                            if (dist < minDist) {
                                closestSide = candidate;
                                minDist = dist;
                            }
                        }
                    }

                    placedRailPos = canPlaceRail(client, closestSide) ? closestSide : basePos;
                    step = 200;
                    tickTimer = 0;
                    Vec3d eye = player.getPos().add(0, player.getStandingEyeHeight(), 0);
                    double dist = eye.distanceTo(Vec3d.ofCenter(placedRailPos));
                    requiredBowHoldTicks = (dist <= 3) ? 4 : 5;
                    return;
                }

                if (railSlot != -1 && cartSlot != -1 && target instanceof BlockHitResult hit && target.getType() == HitResult.Type.BLOCK) {
                    initialTargetHit = hit;
                    BlockPos targetPos = hit.getBlockPos();
                    Block block = client.world.getBlockState(targetPos).getBlock();
                    shouldPlaceRail = !(block instanceof RailBlock);
                    placedRailPos = shouldPlaceRail ? targetPos.offset(hit.getSide()) : targetPos;
                    tickTimer = 0;
                    step = 0;
                    Vec3d eye = player.getPos().add(0, player.getStandingEyeHeight(), 0);
                    double dist = eye.distanceTo(Vec3d.ofCenter(targetPos));
                    requiredBowHoldTicks = (dist <= 3) ? 4 : 5;
                }
            }

            if (tickTimer >= 0) {
                tickTimer++;

                if (onlyShootMinecart) {
                    switch (step) {
                        case 100 -> {
                            if (tickTimer >= 1) {
                                player.getInventory().selectedSlot = bowSlot;
                                Vec3d eye = player.getPos().add(0, player.getStandingEyeHeight(), 0);
                                Vec3d aimAt = client.crosshairTarget.getPos();
                                double dist = eye.distanceTo(aimAt);
                                float pitchOffset = (dist <= 3) ? 13f : 15f;
                                player.setPitch(Math.max(-90f, player.getPitch() - pitchOffset));
                                client.options.useKey.setPressed(true);
                                tickTimer = 0;
                                step++;
                            }
                        }
                        case 101 -> {
                            if (tickTimer >= requiredBowHoldTicks) {
                                client.options.useKey.setPressed(false);
                                player.setPitch(Math.min(90f, player.getPitch() + 15f));
                                tickTimer = -1;
                                onlyShootMinecart = false;
                            }
                        }
                    }
                    return;
                }

                if (entityTargetMode) {
                    switch (step) {
                        case 200 -> {
                            if (tickTimer >= 1) {
                                player.getInventory().selectedSlot = railSlot;
                                placeRailAtPos(client, placedRailPos);
                                tickTimer = 0;
                                step++;
                            }
                        }
                        case 201 -> {
                            if (tickTimer >= 2) {
                                player.getInventory().selectedSlot = cartSlot;
                                placeCartOnRail(client, Hand.MAIN_HAND, placedRailPos);
                                tickTimer = 0;
                                step++;
                            }
                        }
                        case 202 -> {
                            if (tickTimer >= 2) {
                                player.getInventory().selectedSlot = bowSlot;
                                Vec3d eye = player.getPos().add(0, player.getStandingEyeHeight(), 0);
                                Vec3d aimAt = Vec3d.ofCenter(placedRailPos);
                                double dist = eye.distanceTo(aimAt);
                                float pitchOffset = (dist <= 3) ? 13f : 15f;
                                player.setPitch(Math.max(-90f, player.getPitch() - pitchOffset));
                                client.options.useKey.setPressed(true);
                                tickTimer = 0;
                                step++;
                            }
                        }
                        case 203 -> {
                            if (tickTimer >= requiredBowHoldTicks) {
                                client.options.useKey.setPressed(false);
                                player.setPitch(Math.min(90f, player.getPitch() + 15f));
                                tickTimer = -1;
                                entityTargetMode = false;
                            }
                        }
                    }
                    return;
                }

                switch (step) {
                    case 0 -> {
                        if (shouldPlaceRail && tickTimer >= 2) {
                            player.getInventory().selectedSlot = railSlot;
                            placeItemAt(initialTargetHit, client, Hand.MAIN_HAND);
                            tickTimer = 0;
                            step++;
                        } else if (!shouldPlaceRail) {
                            step++;
                            tickTimer = 0;
                        }
                    }
                    case 1 -> {
                        if (tickTimer >= 2 && placedRailPos != null) {
                            player.getInventory().selectedSlot = bowSlot;
                            Vec3d eye = player.getPos().add(0, player.getStandingEyeHeight(), 0);
                            float playerYaw = player.getYaw();
                            Vec3d bestTarget = null;
                            float closestYawDiff = Float.MAX_VALUE;

                            for (double x = 0.25; x <= 0.75; x += 0.25) {
                                for (double z = 0.25; z <= 0.75; z += 0.25) {
                                    Vec3d target = new Vec3d(
                                            placedRailPos.getX() + x,
                                            placedRailPos.getY() + 0.4,
                                            placedRailPos.getZ() + z
                                    );
                                    double dx = target.x - eye.x;
                                    double dz = target.z - eye.z;
                                    float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                                    float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - playerYaw));
                                    if (yawDiff < closestYawDiff) {
                                        closestYawDiff = yawDiff;
                                        bestTarget = target;
                                    }
                                }
                            }

                            if (bestTarget != null) {
                                double dx = bestTarget.x - eye.x;
                                double dz = bestTarget.z - eye.z;
                                float bestYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                                player.setYaw(bestYaw);
                                double dist = eye.distanceTo(bestTarget);
                                float pitchOffset = (dist <= 3) ? 13f : 15f;
                                player.setPitch(Math.max(-90f, player.getPitch() - pitchOffset));
                            }

                            client.options.useKey.setPressed(true);
                            tickTimer = 0;
                            step++;
                        }
                    }
                    case 2 -> {
                        if (tickTimer >= requiredBowHoldTicks) {
                            client.options.useKey.setPressed(false);
                            player.setPitch(Math.min(90f, player.getPitch() + 15f));
                            tickTimer = 0;
                            step++;
                        }
                    }
                    case 3 -> {
                        if (tickTimer >= 2) {
                            tickTimer = 0;
                            step++;
                        }
                    }
                    case 4 -> {
                        if (tickTimer >= 1 && placedRailPos != null) {
                            player.getInventory().selectedSlot = cartSlot;
                            placeCartOnRail(client, Hand.MAIN_HAND, placedRailPos);
                            tickTimer = -1;
                        }
                    }
                }
            }
        });
    }

    private void placeRailAtPos(MinecraftClient client, BlockPos pos) {
        BlockHitResult railHit = new BlockHitResult(
                Vec3d.ofCenter(pos),
                Direction.UP,
                pos,
                false
        );
        client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, railHit);
    }

    private void placeItemAt(BlockHitResult hitResult, MinecraftClient client, Hand hand) {
        if (hitResult != null) {
            client.interactionManager.interactBlock(client.player, hand, hitResult);
        }
    }

    private void placeCartOnRail(MinecraftClient client, Hand hand, BlockPos railPos) {
        BlockHitResult railHit = new BlockHitResult(
                Vec3d.ofCenter(railPos),
                Direction.UP,
                railPos,
                false
        );
        client.interactionManager.interactBlock(client.player, hand, railHit);
    }

    private boolean canPlaceRail(MinecraftClient client, BlockPos pos) {
        BlockPos below = pos.down();
        return client.world.getBlockState(below).isSolidBlock(client.world, below) &&
                client.world.getBlockState(pos).isReplaceable();
    }

    private void findSlots(PlayerEntity player) {
        railSlot = -1;
        bowSlot = -1;
        cartSlot = -1;

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            Item item = stack.getItem();

            if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof RailBlock && railSlot == -1) {
                railSlot = i;
            } else if (item instanceof BowItem && bowSlot == -1) {
                bowSlot = i;
            } else if (item == Items.TNT_MINECART && cartSlot == -1) {
                cartSlot = i;
            }
        }
    }
}