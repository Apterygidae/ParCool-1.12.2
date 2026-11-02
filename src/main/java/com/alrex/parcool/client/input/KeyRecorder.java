package com.alrex.parcool.client.input;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 按键状态记录器（1.12.2 版，1:1 移植）。
 *
 * <p>把 {@code KeyBinding} 的按下/松开翻译成动作需要的四种边沿信号：
 * {@code pressed}（本 tick 刚按下）、{@code released}、{@code doubleTapped}（双击）、
 * 以及「连续按下/连续松开多少 tick」的计数。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <ul>
 *   <li>{@code @OnlyIn(Dist.CLIENT)} → {@code @SideOnly(Side.CLIENT)}</li>
 *   <li>{@code Vector3d} → {@link Vec3d}</li>
 *   <li>{@code InputUpdateEvent} 在 1.12.2 <b>同名存在</b>（Forge 从
 *       {@code EntityPlayerSP.onLivingUpdate} 里抛出），所以钩子不用改</li>
 *   <li>启用/设置两个组合键改走 {@code KeyBindings.isEnableKeyPressed()} /
 *       {@code isOpenSettingsKeyPressed()}（1.12.2 的 KeyBinding 没有修饰键，见该类注释）</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class KeyRecorder {

    public static final KeyState keyForward = new KeyState();
    public static final KeyState keyBack = new KeyState();
    public static final KeyState keyRight = new KeyState();
    public static final KeyState keyLeft = new KeyState();
    public static final KeyState keySneak = new KeyState();
    public static final KeyState keyJumpState = new KeyState();
    public static final KeyState keySprintState = new KeyState();
    public static final KeyState keyCrawlState = new KeyState();
    public static final KeyState keyOpenSettingsState = new KeyState();
    public static final KeyState keyEnableState = new KeyState();
    public static final KeyState keyFastRunning = new KeyState();
    public static final KeyState keyDodge = new KeyState();
    public static final KeyState keyRideZipline = new KeyState();
    public static final KeyState keyBreakfall = new KeyState();
    public static final KeyState keyWallJump = new KeyState();
    public static final KeyState keyQuickTurn = new KeyState();
    public static final KeyState keyFlipping = new KeyState();
    public static final KeyState keyGrabWall = new KeyState();

    @Nullable
    public static Vec3d lastDirection = null;

    private KeyRecorder() {
    }

    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        record(KeyBindings.isKeyForwardDown(), keyForward);
        record(KeyBindings.isKeyBackDown(), keyBack);
        record(KeyBindings.isKeyRightDown(), keyRight);
        record(KeyBindings.isKeyLeftDown(), keyLeft);
        record(KeyBindings.getKeySneak(), keySneak);
        record(KeyBindings.isKeyJumpDown(), keyJumpState);
        record(KeyBindings.getKeySprint(), keySprintState);
        record(KeyBindings.getKeyCrawl(), keyCrawlState);
        // 两个组合键走带修饰键判定的入口
        record(KeyBindings.isOpenSettingsKeyPressed(), keyOpenSettingsState);
        record(KeyBindings.isEnableKeyPressed(), keyEnableState);
        record(KeyBindings.getKeyFastRunning(), keyFastRunning);
        record(KeyBindings.getKeyDodge(), keyDodge);
        record(KeyBindings.getKeyRideZipline(), keyRideZipline);
        record(KeyBindings.getKeyBreakfall(), keyBreakfall);
        record(KeyBindings.getKeyWallJump(), keyWallJump);
        record(KeyBindings.getKeyQuickTurn(), keyQuickTurn);
        record(KeyBindings.getKeyFlipping(), keyFlipping);
        record(KeyBindings.getKeyGrabWall(), keyGrabWall);
        recordMovingVector(KeyBindings.isAnyMovingKeyDown());
    }

    @Nullable
    public static Vec3d getLastMoveVector() {
        return lastDirection;
    }

    /** 上游是 {@code private static void record(Boolean isDown, ...)}。 */
    private static void record(boolean isDown, KeyState state) {
        state.pressed = (isDown && state.tickKeyDown == 0);
        state.released = (!isDown && state.tickNotKeyDown == 0);
        state.doubleTapped = (isDown && 0 < state.tickNotKeyDown && state.tickNotKeyDown <= 2);
        if (state.pressed && state.tickNotKeyDown > 0) {
            state.previousTickNotKeyDown = state.tickNotKeyDown;
        }
        if (isDown) {
            state.tickKeyDown++;
            state.tickNotKeyDown = 0;
        } else {
            state.tickKeyDown = 0;
            state.tickNotKeyDown++;
        }
    }

    private static void record(KeyBinding keyBinding, KeyState state) {
        record(keyBinding.isKeyDown(), state);
    }

    private static void recordMovingVector(boolean isMoving) {
        if (isMoving) lastDirection = KeyBindings.getCurrentMoveVector();
    }

    /** 世界卸载时清掉方向缓存，避免带着上个世界的方向进新世界。 */
    public static void reset() {
        lastDirection = null;
    }

    public static class KeyState {
        private boolean pressed = false;
        private boolean released = false;
        private boolean doubleTapped = false;
        private int tickKeyDown = 0;
        private int tickNotKeyDown = 0;
        private int previousTickNotKeyDown = Integer.MAX_VALUE;

        public boolean isPressed() {
            return pressed;
        }

        public boolean isReleased() {
            return released;
        }

        public boolean isDoubleTapped() {
            return doubleTapped;
        }

        public int getTickKeyDown() {
            return tickKeyDown;
        }

        public int getTickNotKeyDown() {
            return tickNotKeyDown;
        }

        public int getPreviousTickNotKeyDown() {
            return previousTickNotKeyDown;
        }
    }
}
