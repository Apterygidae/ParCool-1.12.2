package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.action.impl.SkyDive;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.utilities.Easing;
import com.alrex.parcool.utilities.EasingFunctions;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 俯冲的「宿主」动画器（1.12.2 版）—— 本工程里<b>结构最复杂</b>的一个动画器。
 *
 * <h3>★ 它为什么是「宿主」</h3>
 * <p>它自己几乎不做姿态计算，而是<b>持有并调度两个子动画器</b>：</p>
 * <ul>
 *   <li>{@link DiveAnimator} —— 俯冲姿态（身体随下落速度翻转）</li>
 *   <li>{@link SkyDiveAnimator} —— 「空中俯冲」姿态（可控制俯仰/横滚）</li>
 * </ul>
 * <p>并在两者之间做<b>交叉淡入淡出</b>（{@code transitioning} + {@code transitionTick}）✓
 * 这样「普通俯冲 ↔ 空中俯冲」切换时不会突跳 ✓</p>
 *
 * <h3>★★ 一个巧妙的设计：子动画器都是 {@link Animator} 但<b>不由 Animation 持有</b></h3>
 * <p>{@code shouldRemoved} 恒返回 {@code false} —— 它们的生命周期完全由宿主管理
 * （宿主 {@code tick()} 手动转发 tick 给它们）✓
 * 所以<b>不能</b>把它们当成普通动画器注册进 {@code Animation}，会出问题 ✗</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <p>本类<b>不含任何 MC 方块/世界 API</b>，只用到 {@code KeyBindings} 与实体状态 ——
 * 所以替换很少：{@code PlayerEntity} → {@link EntityPlayer}、
 * {@code Animation.tick} 的参数类型、以及 {@code @SideOnly(Side.CLIENT)} ✓</p>
 *
 * <p><b>⚠️ 上游这里有两个「恒 false 的 shouldRemoved」都照搬了</b> ——
 * 它们不是疏漏，而是「由宿主管理生命周期」的正确写法 ✓</p>
 */
@SideOnly(Side.CLIENT)
public class DiveAnimationHostAnimator extends Animator {

    final static int MaxTransitionStartedInAirTick = 10;
    final static int MaxTransitionTick = 6;

    final DiveAnimator diveAnimator;
    @Nullable
    SkyDiveAnimator skyDiveAnimator = null;
    final boolean fromInAir;

    private boolean transitioning = false;
    private int transitionTick = 0;
    boolean oldSkyDiveDoing = false;

    public DiveAnimationHostAnimator(double ySpeed, boolean fromInAir) {
        diveAnimator = new DiveAnimator(ySpeed);
        this.fromInAir = fromInAir;
    }

    @Override
    public void tick(EntityPlayer player) {
        super.tick(player);
        diveAnimator.tick(player);
        if (skyDiveAnimator != null) skyDiveAnimator.tick(player);
        if (transitioning) {
            transitionTick++;
            if (transitionTick >= MaxTransitionTick) {
                transitionTick = 0;
                transitioning = false;
            }
        }
    }

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(Dive.class).isDoing();
    }

    void checkTransition(Parkourability parkourability) {
        boolean doing = parkourability.get(SkyDive.class).isDoing();
        if (doing != oldSkyDiveDoing) {
            startTransition();
        }
        oldSkyDiveDoing = doing;
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        if (fromInAir && getTick() < MaxTransitionStartedInAirTick) {
            // 空中起手时的「入水式过渡」
            float phase = (getTick() + transformer.getPartialTick()) / MaxTransitionStartedInAirTick;
            diveAnimator.animatePost(
                    player, parkourability, transformer,
                    new Easing(phase)
                            .squareOut(0, 1, 0, 1)
                            .get()
            );
            float legAngle = -45 * (float) Math.toRadians
                    (new Easing(phase)
                            .squareOut(0, 0.4f, 0, 1)
                            .sinInOut(0.4f, 1, 1, 0)
                            .get()
                    );
            transformer
                    .addRotateLeftLeg(legAngle, 0, 0)
                    .addRotateRightLeg(legAngle, 0, 0)
                    .end();
        } else {
            // 常规：在「俯冲」与「空中俯冲」之间调度
            checkTransition(parkourability);
            if (parkourability.get(SkyDive.class).isDoing()) {
                if (skyDiveAnimator == null) {
                    skyDiveAnimator = new SkyDiveAnimator(diveAnimator.getPitchAngle());
                }
                float factor = getTransitionFactor(transformer.getPartialTick());
                if (transitioning) {
                    diveAnimator.animatePost(player, parkourability, transformer);
                }
                skyDiveAnimator.animatePost(player, parkourability, transformer, factor);
            } else {
                if (transitioning && skyDiveAnimator != null) {
                    float factor = getTransitionFactor(transformer.getPartialTick());
                    skyDiveAnimator.animatePost(player, parkourability, transformer);
                    diveAnimator.animatePost(player, parkourability, transformer, factor);
                } else {
                    skyDiveAnimator = null;
                    diveAnimator.animatePost(player, parkourability, transformer);
                }
            }
        }
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        if (fromInAir && getTick() < MaxTransitionStartedInAirTick) {
            float factor = new Easing((getTick() + rotator.getPartialTick()) / MaxTransitionStartedInAirTick)
                    .squareOut(0, 1, 0, 1)
                    .get();
            diveAnimator.rotate(player, parkourability, rotator, factor, 0);
        } else {
            checkTransition(parkourability);
            if (parkourability.get(SkyDive.class).isDoing()) {
                if (skyDiveAnimator == null) {
                    skyDiveAnimator = new SkyDiveAnimator(diveAnimator.getPitchAngle());
                }
                skyDiveAnimator.rotatePost(player, parkourability, rotator);
            } else {
                if (transitioning && skyDiveAnimator != null) {
                    float factor = getTransitionFactor(rotator.getPartialTick());
                    diveAnimator.rotate(player, parkourability, rotator, factor, skyDiveAnimator.getPitchAngle());
                } else {
                    skyDiveAnimator = null;
                    diveAnimator.rotatePost(player, parkourability, rotator);
                }
            }
        }
    }

    private void startTransition() {
        transitioning = true;
        transitionTick = 0;
    }

    private float getTransitionFactor(float partialTick) {
        float factor;
        if (transitioning) {
            factor = (transitionTick + partialTick) / MaxTransitionTick;
        } else {
            factor = 1;
        }
        return factor;
    }

    /**
     * 「空中俯冲」子动画器 —— <b>生命周期由宿主管理，{@code shouldRemoved} 恒 false</b> ✓
     *
     * <p>玩家用前后左右键控制<b>俯仰/横滚计数</b>（各 ±8），按键按住计数增加、
     * 松开自动回中 —— 于是空中可以微调姿态 ✓</p>
     */
    public static class SkyDiveAnimator extends Animator {
        private int forwardAngleCount = 0;
        private int forwardAngleCountOld = 0;
        private int rightAngleCount = 0;
        private int rightAngleCountOld = 0;
        private final int maxCount = 8;
        private final float startPitchAngle;
        private float pitchAngle;

        public SkyDiveAnimator(float startPitchAngleDegree) {
            this.startPitchAngle = startPitchAngleDegree;
        }

        @Override
        public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
            return false;
        }

        @Override
        public void tick(EntityPlayer player) {
            super.tick(player);
            forwardAngleCountOld = forwardAngleCount;
            rightAngleCountOld = rightAngleCount;
            if (KeyBindings.isKeyForwardDown()) {
                if (KeyBindings.isKeyBackDown()) {
                    // 前后同按 → 回中
                    if (forwardAngleCount > 0) forwardAngleCount--;
                    if (forwardAngleCount < 0) forwardAngleCount++;
                } else {
                    if (forwardAngleCount < maxCount) forwardAngleCount++;
                }
            } else if (KeyBindings.isKeyBackDown()) {
                if (forwardAngleCount > -maxCount) forwardAngleCount--;
            } else {
                if (forwardAngleCount > 0) forwardAngleCount--;
                if (forwardAngleCount < 0) forwardAngleCount++;
            }
            if (KeyBindings.isKeyRightDown()) {
                if (KeyBindings.isKeyLeftDown()) {
                    if (rightAngleCount > 0) rightAngleCount--;
                    if (rightAngleCount < 0) rightAngleCount++;
                } else {
                    if (rightAngleCount < maxCount) rightAngleCount++;
                }
            } else if (KeyBindings.isKeyLeftDown()) {
                if (rightAngleCount > -maxCount) rightAngleCount--;
            } else {
                if (rightAngleCount > 0) rightAngleCount--;
                if (rightAngleCount < 0) rightAngleCount++;
            }
        }

        @Override
        public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
            animatePost(player, parkourability, transformer, 1);
        }

        public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer, float factor) {
            float forwardAngleFactor = getForwardAngleFactor(transformer.getPartialTick());
            float rightAngleFactor = getRightAngleFactor(transformer.getPartialTick());
            transformer
                    .rotateHeadPitch(-20)
                    .rotateRightArm((float) Math.toRadians(20 - 10 * rightAngleFactor), 0, (float) Math.toRadians(70 - 30 * forwardAngleFactor), factor)
                    .rotateLeftArm((float) Math.toRadians(20 + 10 * rightAngleFactor), 0, (float) Math.toRadians(-(70 - 30 * forwardAngleFactor)), factor)
                    .rotateRightLeg((float) Math.toRadians(20 - 10 * rightAngleFactor), 0, (float) Math.toRadians(25 - 10 * forwardAngleFactor), factor)
                    .rotateLeftLeg((float) Math.toRadians(20 + 10 * rightAngleFactor), 0, (float) Math.toRadians(-(25 - 10 * forwardAngleFactor)), factor)
                    .makeArmsMoveDynamically(0.06f)
                    .makeLegsMoveDynamically(0.06f)
                    .end();
        }

        private float getForwardAngleFactor(float partial) {
            float phase;
            if (forwardAngleCount > 0) {
                phase = MathUtil.lerp(forwardAngleCountOld, forwardAngleCount, partial) / maxCount;
            } else if (forwardAngleCount < 0) {
                phase = MathUtil.lerp(forwardAngleCountOld, forwardAngleCount, partial) / maxCount;
            } else {
                phase = 0;
            }
            if (phase > 1) phase = 1;
            if (phase < -1) phase = -1;
            if (phase > 0) {
                return EasingFunctions.CubicInOut(phase);
            } else if (phase < 0) {
                return -EasingFunctions.CubicInOut(-phase);
            }
            return 0;
        }

        private float getRightAngleFactor(float partial) {
            float phase;
            if (rightAngleCount > 0) {
                phase = MathUtil.lerp(rightAngleCountOld, rightAngleCount, partial) / maxCount;
            } else if (rightAngleCount < 0) {
                phase = MathUtil.lerp(rightAngleCountOld, rightAngleCount, partial) / maxCount;
            } else {
                phase = 0;
            }
            if (phase > 1) phase = 1;
            if (phase < -1) phase = -1;

            if (phase > 0) {
                return 1 - (1 - phase) * (1 - phase);
            } else if (phase < 0) {
                phase = -phase;
                return -1 + (1 - phase) * (1 - phase);
            }
            return 0;
        }

        @Override
        public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
            float forwardAngleFactor = getForwardAngleFactor(rotator.getPartialTick());
            float rightAngleFactor = getRightAngleFactor(rotator.getPartialTick());
            float basePitchAngle;
            if (getTick() >= MaxTransitionTick) {
                basePitchAngle = 90;
            } else {
                basePitchAngle = MathUtil.lerp(startPitchAngle, 90, (getTick() + rotator.getPartialTick()) / MaxTransitionTick);
            }
            pitchAngle = basePitchAngle + 24 * forwardAngleFactor;
            rotator.startBasedCenter()
                    .rotatePitchFrontward(pitchAngle)
                    .rotateYawRightward(-24 * rightAngleFactor)
                    .end();
        }

        float getPitchAngle() {
            return pitchAngle;
        }
    }

    /**
     * 「俯冲」子动画器 —— <b>生命周期由宿主管理，{@code shouldRemoved} 恒 false</b> ✓
     *
     * <p>身体翻转角度由<b>下落速度</b>经一条 sigmoid 曲线映射得到
     * （{@code getFactor}）—— 下落越快翻得越平，最终接近水平俯冲 ✓</p>
     */
    public static class DiveAnimator extends Animator {
        private final double initialYSpeed;
        private float pitchAngle = 0;
        private float oldFactor = 0;

        public DiveAnimator(double startYSpeed) {
            this.initialYSpeed = startYSpeed;
        }

        /** sigmoid 映射：把「当前下落速度 / 起手速度」变成 0~0.5 的翻转系数 ✓ */
        private float getFactor(double yMovement) {
            return (float) Math.max(
                    0,
                    2 / (1 + Math.exp(yMovement / (initialYSpeed) - 1)) - 0.9621
                    // -0.9621 是 -2/(1+exp(-1)) + 0.5 —— 保证「起手瞬间系数为 0」✓
            );
        }

        @Override
        public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
            return false;
        }

        @Override
        public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
            animatePost(player, parkourability, transformer, 1);
        }

        void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer, float factor) {
            double ySpeed = parkourability.get(Dive.class).getPlayerYSpeed(transformer.getPartialTick());
            float bodyFactor = getFactor(ySpeed);
            transformer
                    .rotateHeadPitch(-50 * bodyFactor)
                    .rotateRightArm(0, 0, (float) Math.toRadians(195 * bodyFactor), factor)
                    .rotateLeftArm(0, 0, (float) Math.toRadians(-195 * bodyFactor), factor)
                    // ⚠️ 这里用「本次与上次系数的差值」—— 因为 rotate 是叠加的，
                    //    用差值才能得到「累计角速度」而不是重复叠加同一个角度 ✓
                    .rotateRightLeg((float) Math.toRadians(-180 * (bodyFactor - oldFactor)), 0, 0, factor)
                    .rotateLeftLeg((float) Math.toRadians(-180 * (bodyFactor - oldFactor)), 0, 0, factor)
                    .end();
            oldFactor = bodyFactor;
        }

        @Override
        public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
            rotate(player, parkourability, rotator, 1, 0);
        }

        public void rotate(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator,
                           float factor, float transitionBaseAngle) {
            double ySpeed = parkourability.get(Dive.class).getPlayerYSpeed(rotator.getPartialTick());
            float angleFactor = getFactor(ySpeed);
            pitchAngle = 180 * angleFactor;
            rotator.startBasedCenter()
                    .rotatePitchFrontward(MathUtil.lerp(transitionBaseAngle, pitchAngle, factor))
                    .end();
        }

        private float getPitchAngle() {
            return pitchAngle;
        }
    }
}
