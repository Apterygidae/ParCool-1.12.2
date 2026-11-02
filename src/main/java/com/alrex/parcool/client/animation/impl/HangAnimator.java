package com.alrex.parcool.client.animation.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.Animator;
import com.alrex.parcool.client.animation.PlayerModelRotator;
import com.alrex.parcool.client.animation.PlayerModelTransformer;
import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 挂杆的动画（1.12.2 版）—— 数学逻辑 1:1 保留。
 *
 * <p>两种姿态由 {@code orthogonalToBar}（玩家是否<b>垂直于</b>杆）决定：</p>
 * <ul>
 *   <li><b>垂直</b>：双手向前抓（绕 Z 小幅摆动）</li>
 *   <li><b>平躺方向</b>：双手一正一反抓住（绕 X 大幅摆动）</li>
 * </ul>
 * <p>摆动量来自 {@code getArmSwingAmount()}（由横向移动速度累计），所以<b>人在杆上挪动时手会晃</b> ✓</p>
 *
 * <h3>1.12.2 的替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 *   <tr><td>{@code clientPlayer.isLocalPlayer()}</td>
 *       <td>{@link ParCool#PROXY}{@code .isLocalPlayer(...)}</td></tr>
 *   <tr><td>{@code mc.options.getCameraType().isFirstPerson()}</td>
 *       <td>{@code Minecraft.getMinecraft().gameSettings.thirdPersonView == 0}</td></tr>
 *   <tr><td><b>{@code clientPlayer.getViewXRot(partialTicks)}</b></td>
 *       <td><b>1.12.2 没有</b> → 手算
 *           {@code prevRotationPitch + (rotationPitch - prevRotationPitch) * partial}</td></tr>
 *   <tr><td>{@code rotator.startBasedTop()}</td>
 *       <td>同名 ✓（本工程已实现）—— 注意它是「以<b>头顶</b>为基准」，挂杆时才对</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class HangAnimator extends Animator {

    @Override
    public boolean shouldRemoved(EntityPlayer player, Parkourability parkourability) {
        return !parkourability.get(HangDown.class).isDoing();
    }

    @Override
    public void animatePost(EntityPlayer player, Parkourability parkourability, PlayerModelTransformer transformer) {
        HangDown hangDown = parkourability.get(HangDown.class);
        HangDown.BarAxis axis = hangDown.getHangingBarAxis();
        if (axis == null) return;
        boolean orthogonal = hangDown.isOrthogonalToBar();
        transformer
                .translateRightArm(0.3f, -2.0f, 0)
                .translateLeftArm(-0.3f, -2.0f, 0);
        if (orthogonal) {
            float zAngle = (float) Math.toRadians(10 + 20 * Math.sin(24 * hangDown.getArmSwingAmount()));
            transformer
                    .rotateRightArm(
                            (float) Math.PI,
                            0,
                            -zAngle
                    )
                    .rotateLeftArm(
                            (float) Math.PI,
                            0,
                            zAngle
                    );
        } else {
            float xAngle = (float) Math.toRadians(180 + 25 * Math.sin(24 * hangDown.getArmSwingAmount()));
            transformer
                    .rotateRightArm(
                            xAngle,
                            0,
                            (float) Math.toRadians(15)
                    )
                    .rotateLeftArm(
                            -xAngle,
                            0,
                            -(float) Math.toRadians(15)
                    );
        }
        transformer
                .rotateRightLeg(0, 0, 0)
                .rotateLeftLeg(0, 0, 0)
                .makeLegsLittleMoving()
                .end();
    }

    /** 身体前后摆动的角度 —— 幅度由 {@code bodySwingAngleFactor}（起手时的水平速度）决定，之后慢慢衰减 ✓ */
    private float getRotateAngle(HangDown hangDown, double partialTick) {
        return (float) (-hangDown.getBodySwingAngleFactor() * 40
                * Math.sin((hangDown.getDoingTick() + partialTick) / 10 * Math.PI));
    }

    @Override
    public void rotatePost(EntityPlayer player, Parkourability parkourability, PlayerModelRotator rotator) {
        HangDown hangDown = parkourability.get(HangDown.class);
        rotator.startBasedTop()   // 挂杆要绕「头顶」转，不是身体中心
                .rotatePitchFrontward(getRotateAngle(hangDown, rotator.getPartialTick()))
                .end();
    }

    /** 1.12.2 没有 {@code getViewXRot(partialTicks)} —— 手算插值后的俯仰角。 */
    private static float getViewXRot(EntityPlayer player, float partialTicks) {
        return player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
    }

    @Override
    public void onCameraSetUp(EntityViewRenderEvent.CameraSetup event, EntityPlayer clientPlayer,
                              Parkourability parkourability) {
        if (!ParCool.PROXY.isLocalPlayer(clientPlayer)
                || Minecraft.getMinecraft().gameSettings.thirdPersonView != 0
                || !ParCoolConfig.Client.Booleans.EnableCameraAnimationOfHangDown.get()
        ) return;
        HangDown hangDown = parkourability.get(HangDown.class);
        event.setPitch(getViewXRot(clientPlayer, (float) event.getRenderPartialTicks())
                + getRotateAngle(hangDown, event.getRenderPartialTicks()));
    }
}
