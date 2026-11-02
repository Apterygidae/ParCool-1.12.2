package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 闪避的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>四方向各有不同的动作：前=前空翻、后=后手翻、左/右=侧滚 ✓
 * 配第一人称镜头旋转（受 {@code enable_camera_rotation_dodge} 控制）。</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code player.getBbHeight()}</td><td>{@code player.height}</td></tr>
 *   <tr><td>{@code mc.options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td>{@code clientPlayer.isLocalPlayer()}</td>
 *       <td>{@link ParCool#PROXY}{@code .isLocalPlayer(...)}</td></tr>
 *   <tr><td>{@code player.setYBodyRot(x)} / {@code getYHeadRot()}</td>
 *       <td>{@code player.renderYawOffset = x} / {@code player.rotationYawHead}</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class DodgeAnimator extends Animator {

    final Dodge.DodgeDirection direction;

    public DodgeAnimator(Dodge.DodgeDirection dodgeDirection) {
        direction = dodgeDirection;
    }

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return getTick() >= Dodge.MAX_TICK;
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        float phase = (getTick() + transformer.getPartialTick()) / Dodge.MAX_TICK;
        if (phase > 1) {
            return;
        }
        float animFactor = new Easing(phase)
                .squareOut(0, 0.25f, 0, 1)
                .linear(0.25f, 0.75f, 1, 1)
                .sinInOut(0.70f, 1, 1, 0)
                .get();
        switch (direction) {
            case Right: {
                float leftArmXFactor = new Easing(phase)
                        .sinInOut(0, 0.40f, 0, 1)
                        .linear(0.40f, 0.7f, 1, 1)
                        .sinInOut(0.7f, 1, 1, 0)
                        .get();
                float rightArmXFactor = new Easing(phase)
                        .sinInOut(0, 0.3f, 0, -0.8f)
                        .sinInOut(0.3f, 0.6f, -0.8f, 1)
                        .sinInOut(0.6f, 1, 1, 0)
                        .get();
                float rightArmZFactor = new Easing(phase)
                        .linear(0, 0.3f, 0, 0)
                        .sinInOut(0.4f, 0.7f, 0, 1)
                        .sinInOut(0.7f, 1, 1, 0)
                        .get();
                float leftArmZFactor = new Easing(phase)
                        .sinInOut(0, 0.5f, 0, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                float rightLegXFactor = new Easing(phase)
                        .sinInOut(0, 0.35f, 0, 1)
                        .linear(0.35f, 0.65f, 1, 1)
                        .sinInOut(0.65f, 1, 1, 0)
                        .get();
                float rightLegZFactor = new Easing(phase)
                        .linear(0, 0.35f, 0, -0.9f)
                        .sinInOut(0.35f, 0.75f, -0.9f, 1f)
                        .sinInOut(0.75f, 1f, 1f, 0)
                        .get();
                float leftLegXFactor = new Easing(phase)
                        .linear(0, 0.2f, 0, 0)
                        .sinInOut(0.2f, 0.6f, 0, 1)
                        .sinInOut(0.6f, 1, 1, 0).get();
                float leftLegZFactor = new Easing(phase)
                        .linear(0, 0.3f, 0, -0.5f)
                        .sinInOut(0.3f, 0.6f, -0.5f, 0.8f)
                        .sinInOut(0.6f, 1f, 0.8f, 0)
                        .get();
                float headYawFactor = new Easing(phase)
                        .sinInOut(0, 0.5f, 0, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                float headPitchFactor = new Easing(phase)
                        .sinInOut(0, 0.5f, 0, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                transformer
                        .rotateRightLeg((float) Math.toRadians(-85 * rightLegXFactor), 0, (float) Math.toRadians(30 * rightLegZFactor), animFactor)
                        .rotateLeftLeg((float) Math.toRadians(-85 * leftLegXFactor + 15), 0, (float) Math.toRadians(30 * leftLegZFactor), animFactor)
                        .rotateRightArm((float) Math.toRadians(-5 + 45 * rightArmXFactor), 0, (float) Math.toRadians(-20 * rightArmZFactor), animFactor)
                        .rotateLeftArm((float) Math.toRadians(-60 * leftArmXFactor), 0, (float) Math.toRadians(40 * leftArmZFactor), animFactor)
                        .rotateAdditionallyHeadPitch(40 * headPitchFactor)
                        .rotateAdditionallyHeadYaw(-30 * headYawFactor)
                        .end();
                break;
            }
            case Left: {
                float rightArmXFactor = new Easing(phase)
                        .sinInOut(0, 0.40f, 0, 1)
                        .linear(0.40f, 0.7f, 1, 1)
                        .sinInOut(0.7f, 1, 1, 0)
                        .get();
                float leftArmXFactor = new Easing(phase)
                        .sinInOut(0, 0.3f, 0, -0.8f)
                        .sinInOut(0.3f, 0.6f, -0.8f, 1)
                        .sinInOut(0.6f, 1, 1, 0)
                        .get();
                float leftArmZFactor = new Easing(phase)
                        .linear(0, 0.3f, 0, 0)
                        .sinInOut(0.4f, 0.7f, 0, 1)
                        .sinInOut(0.7f, 1, 1, 0)
                        .get();
                float rightArmZFactor = new Easing(phase)
                        .sinInOut(0, 0.5f, 0, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                float leftLegXFactor = new Easing(phase)
                        .sinInOut(0, 0.35f, 0, 1)
                        .linear(0.35f, 0.65f, 1, 1)
                        .sinInOut(0.65f, 1, 1, 0)
                        .get();
                float leftLegZFactor = new Easing(phase)
                        .linear(0, 0.5f, 0, 0.2f)
                        .sinInOut(0.5f, 0.75f, 0.2f, 1)
                        .sinInOut(0.75f, 1, 1, 0)
                        .get();
                float rightLegXFactor = new Easing(phase)
                        .linear(0, 0.2f, 0, 0)
                        .sinInOut(0.2f, 0.6f, 0, 1)
                        .sinInOut(0.6f, 1, 1, 0).get();
                float rightLegZFactor = new Easing(phase)
                        .squareOut(0, 0.4f, 0, 1)
                        .sinInOut(0.4f, 0.75f, 1, 0.2f)
                        .linear(0.75f, 1, 0.2f, 0)
                        .get();
                float headYawFactor = new Easing(phase)
                        .sinInOut(0, 0.5f, 0, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                float headPitchFactor = new Easing(phase)
                        .sinInOut(0, 0.5f, 0, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                transformer
                        .rotateRightLeg((float) Math.toRadians(-85 * rightLegXFactor + 15), 0, (float) Math.toRadians(30 * rightLegZFactor), animFactor)
                        .rotateLeftLeg((float) Math.toRadians(-85 * leftLegXFactor), 0, (float) Math.toRadians(-35 * leftLegZFactor), animFactor)
                        .rotateRightArm((float) Math.toRadians(-60 * rightArmXFactor), 0, (float) Math.toRadians(40 * rightArmZFactor), animFactor)
                        .rotateLeftArm((float) Math.toRadians(-5 + 45 * leftArmXFactor), 0, (float) Math.toRadians(-20 * leftArmZFactor), animFactor)
                        .rotateAdditionallyHeadPitch(40 * headPitchFactor)
                        .rotateAdditionallyHeadYaw(30 * headYawFactor)
                        .end();
                break;
            }
            case Front: {
                float rightLegXFactor = new Easing(phase)
                        .sinInOut(0, 0.2f, 0, -0.3f)
                        .sinInOut(0.2f, 0.4f, -0.3f, 1)
                        .sinInOut(0.4f, 0.85f, 1, -0.3f)
                        .sinInOut(0.85f, 1, -0.3f, 0)
                        .get();
                float leftLegXFactor = new Easing(phase)
                        .sinInOut(0, 0.1f, 0, -0.3f)
                        .sinInOut(0.1f, 0.3f, -0.3f, 1)
                        .sinInOut(0.3f, 0.75f, 1, -0.3f)
                        .sinInOut(0.75f, 1, -0.3f, 0)
                        .get();
                float armXFactor = new Easing(phase)
                        .squareOut(0.0f, 0.20f, -0.1f, 1)
                        .sinInOut(0.0f, 1, 1f, 0)
                        .get();
                float headPitchFactor = new Easing(phase)
                        .sinInOut(0, 0.10f, 0, -0.2f)
                        .sinInOut(0.10f, 0.25f, -0.2f, 1)
                        .sinInOut(0.25f, 1, 1f, 0)
                        .get();
                float armZFactor = new Easing(phase)
                        .sinInOut(0, 0.4f, 0, 1)
                        .sinInOut(0.4f, 1, 1, 0)
                        .get();
                transformer
                        .translateLeftArm(-0.8f * armZFactor, 0, 0)
                        .translateRightArm(0.8f * armZFactor, 0, 0)
                        .rotateRightLeg((float) Math.toRadians(rightLegXFactor * -70), 0, 0, animFactor)
                        .rotateLeftLeg((float) Math.toRadians(leftLegXFactor * -70), 0, 0, animFactor)
                        .rotateRightArm((float) Math.toRadians(-170 * armXFactor), 0, (float) Math.toRadians(15 * armZFactor), animFactor)
                        .rotateLeftArm((float) Math.toRadians(-170 * armXFactor), 0, (float) Math.toRadians(-15 * armZFactor), animFactor)
                        .rotateAdditionallyHeadPitch(40 * headPitchFactor)
                        .end();
                break;
            }
            case Back: {
                float leftLegXFactor = new Easing(phase)
                        .sinInOut(0, 0.2f, 0, -0.3f)
                        .sinInOut(0.2f, 0.4f, -0.3f, 1)
                        .sinInOut(0.4f, 0.85f, 1, -0.3f)
                        .sinInOut(0.85f, 1, -0.3f, 0)
                        .get();
                float rightLegXFactor = new Easing(phase)
                        .sinInOut(0, 0.1f, 0, -0.3f)
                        .sinInOut(0.1f, 0.4f, -0.3f, 1)
                        .sinInOut(0.4f, 0.8f, 1, -0.3f)
                        .sinInOut(0.8f, 1, -0.3f, 0)
                        .get();
                float armXFactor = new Easing(phase)
                        .sinInOut(0, 0.10f, 0, -0.2f)
                        .sinInOut(0.10f, 0.4f, -0.2f, 1)
                        .sinInOut(0.4f, 1, 1f, 0)
                        .get();
                float headPitchFactor = new Easing(phase)
                        .sinInOut(0, 0.10f, 0, -0.2f)
                        .sinInOut(0.10f, 0.35f, -0.2f, 1)
                        .sinInOut(0.35f, 1, 1f, 0)
                        .get();
                float armZFactor = new Easing(phase)
                        .sinInOut(0, 0.4f, 0, 1)
                        .sinInOut(0.4f, 1, 1, 0)
                        .get();
                transformer
                        .rotateRightLeg((float) Math.toRadians(rightLegXFactor * 70), 0, 0, animFactor)
                        .rotateLeftLeg((float) Math.toRadians(leftLegXFactor * 70), 0, 0, animFactor)
                        .rotateRightArm((float) Math.toRadians(-180 * armXFactor), 0, (float) Math.toRadians(10 * armZFactor), animFactor)
                        .rotateLeftArm((float) Math.toRadians(-180 * armXFactor), 0, (float) Math.toRadians(-10 * armZFactor), animFactor)
                        .rotateAdditionallyHeadPitch(-40 * headPitchFactor)
                        .end();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        float phase = (getTick() + rotator.getPartialTick()) / Dodge.MAX_TICK;
        if (phase > 1) {
            return;
        }
        switch (direction) {
            case Front: {
                float bodyPitchFactor = new Easing(phase)
                        .squareOut(0, 1, 0, 1)
                        .get();
                float translateYFactor = new Easing(phase)
                        .squareOut(0, 0.4f, 0, 1)
                        .sinInOut(0.4f, 1, 1, 0)
                        .get();
                rotator.startBasedCenter()
                        .translateY(-translateYFactor * player.height / 4f)
                        .rotatePitchFrontward(360 * bodyPitchFactor)
                        .end();
                break;
            }
            case Left:
            case Right: {
                float bodyPitchFactor = new Easing(phase)
                        .squareOut(0, 0.25f, 0, 1)
                        .linear(0.25f, 0.4f, 1, 1)
                        .sinInOut(0.4f, 1, 1, 0)
                        .get();
                float bodyYawFactor = new Easing(phase)
                        .squareIn(0f, 0.15f, 0, 0.2f)
                        .squareOut(0.15f, 1, 0.2f, 1)
                        .get();
                float yawFactor = new Easing(phase)
                        .sinInOut(0, 0.15f, 0, -1)
                        .sinInOut(0.15f, 0.8f, -1, 1)
                        .sinInOut(0.8f, 1, 1, 0)
                        .get();
                float yTranslateFactor = new Easing(phase)
                        .squareOut(0, 0.3f, 0, 1)
                        .linear(0.3f, 0.5f, 1, 1)
                        .sinInOut(0.5f, 1, 1, 0)
                        .get();
                if (direction == Dodge.DodgeDirection.Left) {
                    rotator.startBasedCenter()
                            .translateY(-yTranslateFactor * player.height / 3f)
                            .rotateYawRightward(25 * yawFactor - 4)
                            .rotatePitchFrontward(105 * bodyPitchFactor)
                            .rotateYawRightward(360 * bodyYawFactor)
                            .end();
                } else {
                    rotator.startBasedCenter()
                            .translateY(-yTranslateFactor * player.height / 3f)
                            .rotateYawRightward(-25 * yawFactor + 4)
                            .rotatePitchFrontward(105 * bodyPitchFactor)
                            .rotateYawRightward(-360 * bodyYawFactor)
                            .end();
                }
                break;
            }
            case Back: {
                float bodyPitchFactor = new Easing(phase)
                        .linear(0, 0.4f, 0, 0.4f)
                        .sinInOut(0.4f, 1, 0.4f, 1)
                        .get();
                rotator.startBasedCenter()
                        .rotatePitchFrontward(-360 * bodyPitchFactor)
                        .end();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer,
                              Parkourability parkourability) {
        if (!(ParCool.PROXY.isLocalPlayer(clientPlayer)
                && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0
                && ParCoolConfig.Client.Booleans.EnableCameraAnimationOfDodge.get()
        )) return;
        float phase = (float) ((getTick() + event.getRenderPartialTicks()) / Dodge.MAX_TICK);
        switch (direction) {
            case Front: {
                float bodyPitchFactor = new Easing(phase)
                        .sinInOut(0, 1, 0, 1)
                        .get();
                event.setPitch(event.getPitch() + 360 * bodyPitchFactor);
                break;
            }
            case Left:
            case Right: {
                float rollFactor = new Easing(phase)
                        .squareOut(0, 1, 0, 1)
                        .get();
                if (direction == Dodge.DodgeDirection.Right) {
                    event.setRoll(event.getRoll() + 360 * rollFactor);
                } else {
                    event.setRoll(event.getRoll() - 360 * rollFactor);
                }
                break;
            }
            case Back: {
                float bodyPitchFactor = new Easing(phase)
                        .sinInOut(0, 1, 0, 1)
                        .get();
                event.setPitch(event.getPitch() - 360 * bodyPitchFactor);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
        switch (direction) {
            case Right:
                player.renderYawOffset = player.rotationYawHead - 5;
                break;
            case Left:
                player.renderYawOffset = player.rotationYawHead + 5;
                break;
            default:
                break;
        }
    }
}
