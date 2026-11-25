package com.typeomega.qa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Local client-only freecam controller for {@link WandEditorScreen} using a dummy {@link Marker} camera entity
 * and {@link MovementInputUpdateEvent} freezing so the real player stays stationary.
 */
@OnlyIn(Dist.CLIENT)
public final class WandFreecamController {

    private static final double MOVE_SPEED = 0.45;
    private static boolean active;
    private static @Nullable Marker cameraEntity;
    private static float yaw;
    private static float pitch;
    private static Vec3 pos = Vec3.ZERO;

    private WandFreecamController() {}

    public static boolean isActive() {
        return active;
    }

    public static void toggle() {
        if (active) disable();
        else enable();
    }

    public static void enable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Player player = mc.player;
        pos = player.getEyePosition();
        yaw = player.getYRot();
        pitch = player.getXRot();

        cameraEntity = new Marker(EntityType.MARKER, mc.level);
        syncEntityPose();
        mc.setCameraEntity(cameraEntity);
        active = true;
    }

    public static void disable() {
        Minecraft mc = Minecraft.getInstance();
        active = false;
        cameraEntity = null;
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
    }

    public static void rotate(double deltaYaw, double deltaPitch) {
        if (!active) return;
        yaw += (float) deltaYaw;
        pitch = Math.max(-89.5f, Math.min(89.5f, pitch + (float) deltaPitch));
        syncEntityPose();
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!active) return;
        Input input = event.getInput();
        input.forwardImpulse = 0f;
        input.leftImpulse = 0f;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.screen instanceof WandEditorScreen editor)) {
            if (!(mc.screen instanceof WandEditorScreen)) disable();
            return;
        }
        if (editor.isTypingInTextBox()) return;

        long window = mc.getWindow().getWindow();
        double forward = (isKeyDown(window, GLFW.GLFW_KEY_W) ? 1 : 0) -
                (isKeyDown(window, GLFW.GLFW_KEY_S) ? 1 : 0);
        double strafe = (isKeyDown(window, GLFW.GLFW_KEY_D) ? 1 : 0) -
                (isKeyDown(window, GLFW.GLFW_KEY_A) ? 1 : 0);
        double vertical = (isKeyDown(window, GLFW.GLFW_KEY_SPACE) ? 1 : 0) -
                (isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ? 1 : 0);

        if (forward == 0 && strafe == 0 && vertical == 0) return;
        double yawRad = Math.toRadians(yaw);
        double lookX = -Math.sin(yawRad);
        double lookZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        Vec3 delta = new Vec3(
                (lookX * forward + rightX * strafe) * MOVE_SPEED,
                vertical * MOVE_SPEED,
                (lookZ * forward + rightZ * strafe) * MOVE_SPEED);
        pos = pos.add(delta);
        syncEntityPose();
    }

    private static boolean isKeyDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private static void syncEntityPose() {
        Entity cam = cameraEntity;
        if (cam == null) return;
        cam.setPos(pos.x, pos.y, pos.z);
        cam.xo = pos.x;
        cam.yo = pos.y;
        cam.zo = pos.z;
        cam.setYRot(yaw);
        cam.setXRot(pitch);
        cam.yRotO = yaw;
        cam.xRotO = pitch;
    }
}
