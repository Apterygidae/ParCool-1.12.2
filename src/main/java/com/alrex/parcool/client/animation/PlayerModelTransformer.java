package com.alrex.parcool.client.animation;

import com.alrex.parcool.api.unstable.animation.AnimationOption;
import com.alrex.parcool.api.unstable.animation.AnimationPart;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 模型变换适配层（1.12.2 版）—— 本工程动画部分<b>唯一被重写</b>的类。
 *
 * <p>上游 20 个 Animator 的数学逻辑不改，它们只调用本类暴露的方法。
 * 所以这里保持<b>方法签名与语义 1:1 一致</b>，只替换底层模型 API。</p>
 *
 * <h3>⚠️ 角度单位是<b>弧度</b>（上游类注释原文：Using Radians）</h3>
 *
 * <h3>1.12.2 ↔ 1.16.5 的字段/方法对照（本类全部改动都在这里）</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code PlayerModel}</td><td>{@link ModelPlayer}</td></tr>
 *   <tr><td>{@code ModelRenderer}</td><td>{@link ModelRenderer} —— <b>同名</b></td></tr>
 *   <tr><td>{@code renderer.xRot / yRot / zRot}</td>
 *       <td>{@code rotateAngleX / rotateAngleY / rotateAngleZ}</td></tr>
 *   <tr><td>{@code renderer.x / y / z}</td>
 *       <td>{@code rotationPointX / rotationPointY / rotationPointZ}</td></tr>
 *   <tr><td>{@code renderer.visible}</td><td>{@code showModel}</td></tr>
 *   <tr><td>{@code model.rightArm / leftArm / rightLeg / leftLeg / head / body}</td>
 *       <td>{@code bipedRightArm / bipedLeftArm / bipedRightLeg / bipedLeftLeg / bipedHead / bipedBody}</td></tr>
 *   <tr><td>{@code model.hat / jacket / rightSleeve / leftSleeve / rightPants / leftPants}</td>
 *       <td>{@code bipedHeadwear / bipedBodyWear / bipedRightArmwear / bipedLeftArmwear / bipedRightLegwear / bipedLeftLegwear}</td></tr>
 *   <tr><td>{@code ModelRenderer#copyFrom(other)}</td>
 *       <td><b>1.12.2 没有</b> → 本类自带 {@link #copyFrom(ModelRenderer, ModelRenderer)} 手动复制 6 个字段</td></tr>
 *   <tr><td>{@code ModelHelper.bobArms(a, b, ageInTicks)}</td>
 *       <td><b>1.12.2 没有 {@code ModelHelper}</b> → 本类自带 {@link #bobArms}，
 *           用的是 1.12.2 {@code ModelBiped.setRotationAngles} 里同一套经典手臂摆动公式</td></tr>
 *   <tr><td>{@code Minecraft.getInstance().getFrameTime()}</td>
 *       <td>{@code Minecraft.getMinecraft().getRenderPartialTicks()}</td></tr>
 *   <tr><td>{@code PlayerEntity}</td><td>{@link EntityPlayer}</td></tr>
 * </table>
 *
 * <p>{@code slim} 参数上游就没用上（兼容保留），这里同样保留签名。</p>
 */
@SideOnly(Side.CLIENT)
public class PlayerModelTransformer {

    private final EntityPlayer player;
    private final ModelPlayer model;
    /**
     * 模型是不是 slim（Alex）。
     *
     * <p>上游把这个参数收了却<b>从未使用</b>（所以它那种写法对 slim 模型是错的，见 {@link #reset()}）。
     * 1.12.2 版把它用起来 —— 这是本类相对上游的一处<b>有意的修正</b>。</p>
     */
    private final boolean slim;
    private final float partial;
    private final float ageInTicks;
    private final float limbSwing;
    private final float limbSwingAmount;
    private final float netHeadYaw;
    private final float headPitch;
    private AnimationOption option = new AnimationOption();

    public PlayerModelTransformer(
            EntityPlayer player,
            ModelPlayer model,
            boolean slim,
            float ageInTicks,
            float limbSwing,
            float limbSwingAmount,
            float netHeadYaw,
            float headPitch
    ) {
        this.player = player;
        this.model = model;
        this.slim = slim;
        this.partial = Minecraft.getMinecraft().getRenderPartialTicks();
        this.ageInTicks = ageInTicks;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
    }

    // ── 读取 ────────────────────────────────────────────────────────────────

    public float getPartialTick() {
        return partial;
    }

    public float getHeadPitch() {
        return headPitch;
    }

    public float getNetHeadYaw() {
        return netHeadYaw;
    }

    public float getLimbSwing() {
        return limbSwing;
    }

    public float getLimbSwingAmount() {
        return limbSwingAmount;
    }

    public ModelPlayer getRawModel() {
        return model;
    }

    /** 上游没用，保留以免子类/未来代码找不到入口。 */
    @SuppressWarnings("unused")
    public EntityPlayer getPlayer() {
        return player;
    }

    public void setOption(AnimationOption option) {
        this.option = option;
    }

    // ── 手臂 ────────────────────────────────────────────────────────────────

    /**
     * @param angleX 前后摆臂
     * @param angleY 手臂绕自身轴旋转
     * @param angleZ 上下抬臂
     */
    public PlayerModelTransformer rotateRightArm(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        ModelRenderer rightArm = model.bipedRightArm;
        if (rightArm.showModel) {
            setRotations(rightArm, angleX, angleY, angleZ);
        }
        return this;
    }

    public PlayerModelTransformer rotateRightArm(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        ModelRenderer rightArm = model.bipedRightArm;
        if (rightArm.showModel) {
            setRotations(rightArm,
                    MathUtil.lerp(rightArm.rotateAngleX, angleX, factor),
                    MathUtil.lerp(rightArm.rotateAngleY, angleY, factor),
                    MathUtil.lerp(rightArm.rotateAngleZ, angleZ, factor)
            );
        }
        return this;
    }

    public PlayerModelTransformer rotateLeftArm(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        ModelRenderer leftArm = model.bipedLeftArm;
        if (leftArm.showModel) {
            setRotations(leftArm, angleX, angleY, angleZ);
        }
        return this;
    }

    public PlayerModelTransformer rotateLeftArm(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        ModelRenderer leftArm = model.bipedLeftArm;
        if (leftArm.showModel) {
            setRotations(leftArm,
                    MathUtil.lerp(leftArm.rotateAngleX, angleX, factor),
                    MathUtil.lerp(leftArm.rotateAngleY, angleY, factor),
                    MathUtil.lerp(leftArm.rotateAngleZ, angleZ, factor)
            );
        }
        return this;
    }

    // ── 腿 ──────────────────────────────────────────────────────────────────

    public PlayerModelTransformer rotateRightLeg(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        ModelRenderer rightLeg = model.bipedRightLeg;
        if (rightLeg.showModel) {
            setRotations(rightLeg, angleX, angleY, angleZ);
        }
        return this;
    }

    public PlayerModelTransformer rotateRightLeg(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        ModelRenderer rightLeg = model.bipedRightLeg;
        if (rightLeg.showModel) {
            setRotations(rightLeg,
                    MathUtil.lerp(rightLeg.rotateAngleX, angleX, factor),
                    MathUtil.lerp(rightLeg.rotateAngleY, angleY, factor),
                    MathUtil.lerp(rightLeg.rotateAngleZ, angleZ, factor)
            );
        }
        return this;
    }

    public PlayerModelTransformer rotateLeftLeg(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        ModelRenderer leftLeg = model.bipedLeftLeg;
        if (leftLeg.showModel) {
            setRotations(leftLeg, angleX, angleY, angleZ);
        }
        return this;
    }

    public PlayerModelTransformer rotateLeftLeg(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        ModelRenderer leftLeg = model.bipedLeftLeg;
        if (leftLeg.showModel) {
            setRotations(leftLeg,
                    MathUtil.lerp(leftLeg.rotateAngleX, angleX, factor),
                    MathUtil.lerp(leftLeg.rotateAngleY, angleY, factor),
                    MathUtil.lerp(leftLeg.rotateAngleZ, angleZ, factor)
            );
        }
        return this;
    }

    // ── 叠加旋转 ────────────────────────────────────────────────────────────

    public PlayerModelTransformer addRotateRightArm(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        ModelRenderer arm = model.bipedRightArm;
        if (arm.showModel) {
            setRotations(arm, arm.rotateAngleX + angleX, arm.rotateAngleY + angleY, arm.rotateAngleZ + angleZ);
        }
        return this;
    }

    public PlayerModelTransformer addRotateLeftArm(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        ModelRenderer arm = model.bipedLeftArm;
        if (arm.showModel) {
            setRotations(arm, arm.rotateAngleX + angleX, arm.rotateAngleY + angleY, arm.rotateAngleZ + angleZ);
        }
        return this;
    }

    public PlayerModelTransformer addRotateRightLeg(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        ModelRenderer leg = model.bipedRightLeg;
        if (leg.showModel) {
            setRotations(leg, leg.rotateAngleX + angleX, leg.rotateAngleY + angleY, leg.rotateAngleZ + angleZ);
        }
        return this;
    }

    public PlayerModelTransformer addRotateLeftLeg(float angleX, float angleY, float angleZ) {
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        ModelRenderer leg = model.bipedLeftLeg;
        if (leg.showModel) {
            setRotations(leg, leg.rotateAngleX + angleX, leg.rotateAngleY + angleY, leg.rotateAngleZ + angleZ);
        }
        return this;
    }

    public PlayerModelTransformer addRotateRightArm(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        return addRotateRightArm(angleX * factor, angleY * factor, angleZ * factor);
    }

    public PlayerModelTransformer addRotateLeftArm(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        return addRotateLeftArm(angleX * factor, angleY * factor, angleZ * factor);
    }

    public PlayerModelTransformer addRotateRightLeg(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        return addRotateRightLeg(angleX * factor, angleY * factor, angleZ * factor);
    }

    public PlayerModelTransformer addRotateLeftLeg(float angleX, float angleY, float angleZ, float factor) {
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        return addRotateLeftLeg(angleX * factor, angleY * factor, angleZ * factor);
    }

    // ── 各部位「动起来」的快捷方法 ──────────────────────────────────────────

    /** 对应上游 {@code ModelHelper.bobArms(rightArm, leftArm, ageInTicks)}。 */
    public PlayerModelTransformer makeArmsNatural() {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        bobArms(model.bipedRightArm, model.bipedLeftArm, ageInTicks);
        return this;
    }

    public PlayerModelTransformer makeLegsMoveDynamically(float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        model.bipedRightLeg.rotateAngleZ += MathHelper.cos(ageInTicks * 0.56F) * 0.8F * factor + 0.05F;
        model.bipedLeftLeg.rotateAngleZ -= MathHelper.cos(ageInTicks * 0.56F) * 0.8F * factor + 0.05F;
        model.bipedRightLeg.rotateAngleX += MathHelper.sin(ageInTicks * 0.56F) * 0.8F * factor;
        model.bipedLeftLeg.rotateAngleX -= MathHelper.sin(ageInTicks * 0.56F) * 0.8F * factor;
        return this;
    }

    public PlayerModelTransformer makeArmsMoveDynamically(float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        model.bipedRightArm.rotateAngleZ += MathHelper.cos(ageInTicks * 0.56F) * 0.8F * factor + 0.05F;
        model.bipedLeftArm.rotateAngleZ -= MathHelper.cos(ageInTicks * 0.56F) * 0.8F * factor + 0.05F;
        model.bipedRightArm.rotateAngleX += MathHelper.sin(ageInTicks * 0.56F) * 0.8F * factor;
        model.bipedLeftArm.rotateAngleX -= MathHelper.sin(ageInTicks * 0.56F) * 0.8F * factor;
        return this;
    }

    /** 对应上游 {@code ModelHelper.bobArms(rightLeg, leftLeg, ageInTicks)}。 */
    public PlayerModelTransformer makeLegsLittleMoving() {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        bobArms(model.bipedRightLeg, model.bipedLeftLeg, ageInTicks);
        return this;
    }

    public PlayerModelTransformer makeLegsShakingDynamically(float factor) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        model.bipedRightLeg.rotateAngleZ += MathHelper.cos(ageInTicks * 0.56F) * 0.8F * factor + 0.05F;
        model.bipedLeftLeg.rotateAngleZ += MathHelper.cos(ageInTicks * 0.56F) * 0.8F * factor + 0.05F;
        model.bipedRightLeg.rotateAngleX += MathHelper.sin(ageInTicks * 0.56F) * 0.2F * factor;
        model.bipedLeftLeg.rotateAngleX -= MathHelper.sin(ageInTicks * 0.56F) * 0.2F * factor;
        return this;
    }

    // ── 头 ──────────────────────────────────────────────────────────────────

    public PlayerModelTransformer rotateAdditionallyHeadPitch(float pitchDegree) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        model.bipedHead.rotateAngleX = (float) Math.toRadians(pitchDegree + headPitch);
        return this;
    }

    public PlayerModelTransformer rotateHeadPitch(float pitchDegree) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        model.bipedHead.rotateAngleX = (float) Math.toRadians(pitchDegree);
        return this;
    }

    public PlayerModelTransformer rotateHeadYaw(float yawDegree) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        model.bipedHead.rotateAngleY = (float) Math.toRadians(yawDegree);
        return this;
    }

    public PlayerModelTransformer rotateHeadYawRadian(float yawRadian) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        model.bipedHead.rotateAngleY = yawRadian;
        return this;
    }

    public PlayerModelTransformer rotateAdditionallyHeadYaw(float yawDegree) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        model.bipedHead.rotateAngleY = (float) Math.toRadians(yawDegree + netHeadYaw);
        return this;
    }

    public PlayerModelTransformer rotateAdditionallyHeadRoll(float rollDegree) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        model.bipedHead.rotateAngleZ = (float) Math.toRadians(rollDegree + netHeadYaw);
        return this;
    }

    // ── 位移 ────────────────────────────────────────────────────────────────

    public PlayerModelTransformer translateRightArm(float xOffset, float yOffset, float zOffset) {
        if (option.isCanceled(AnimationPart.RIGHT_ARM)) return this;
        ModelRenderer arm = model.bipedRightArm;
        arm.rotationPointX += xOffset;
        arm.rotationPointY += yOffset;
        arm.rotationPointZ += zOffset;
        return this;
    }

    public PlayerModelTransformer translateLeftArm(float xOffset, float yOffset, float zOffset) {
        if (option.isCanceled(AnimationPart.LEFT_ARM)) return this;
        ModelRenderer arm = model.bipedLeftArm;
        arm.rotationPointX += xOffset;
        arm.rotationPointY += yOffset;
        arm.rotationPointZ += zOffset;
        return this;
    }

    public PlayerModelTransformer translateRightLeg(float xOffset, float yOffset, float zOffset) {
        if (option.isCanceled(AnimationPart.RIGHT_LEG)) return this;
        ModelRenderer leg = model.bipedRightLeg;
        leg.rotationPointX += xOffset;
        leg.rotationPointY += yOffset;
        leg.rotationPointZ += zOffset;
        return this;
    }

    public PlayerModelTransformer translateLeftLeg(float xOffset, float yOffset, float zOffset) {
        if (option.isCanceled(AnimationPart.LEFT_LEG)) return this;
        ModelRenderer leg = model.bipedLeftLeg;
        leg.rotationPointX += xOffset;
        leg.rotationPointY += yOffset;
        leg.rotationPointZ += zOffset;
        return this;
    }

    public PlayerModelTransformer translateHead(float xOffset, float yOffset, float zOffset) {
        if (option.isCanceled(AnimationPart.HEAD)) return this;
        ModelRenderer head = model.bipedHead;
        head.rotationPointX += xOffset;
        head.rotationPointY += yOffset;
        head.rotationPointZ += zOffset;
        return this;
    }

    public void end() {
        // 上游就是空的 —— 变换是直接改模型字段，没有需要收尾的矩阵栈
    }

    /** 把主部位的变换同步到「衣物层」（袖子/裤腿/外套/帽子）。 */
    public void copyFromBodyToWear() {
        copyFrom(model.bipedRightArm, model.bipedRightArmwear);
        copyFrom(model.bipedLeftArm, model.bipedLeftArmwear);
        copyFrom(model.bipedRightLeg, model.bipedRightLegwear);
        copyFrom(model.bipedLeftLeg, model.bipedLeftLegwear);
        copyFrom(model.bipedBody, model.bipedBodyWear);
        copyFrom(model.bipedHead, model.bipedHeadwear);
    }

    // ── 复位 ────────────────────────────────────────────────────────────────

    /**
     * 把模型恢复到「未加动画」的初始姿态。
     *
     * <p>手里的数字是 1.12.2 {@code ModelBiped} 构造器里的原始 rotationPoint ——
     * 与 1.16 的同名常量一致（模型坐标系两版没变）。</p>
     *
     * <h3>⚠️ 手臂的 Y 取决于模型是不是 slim —— 上游这里有个坑</h3>
     * <p>{@code ModelPlayer} 构造器对 slim（Alex）模型会把手臂旋转基点设成 <b>2.5F</b>：</p>
     * <pre>
     * if (smallArmsIn) {
     *     this.bipedLeftArm.setRotationPoint(5.0F, 2.5F, 0.0F);
     *     this.bipedRightArm.setRotationPoint(-5.0F, 2.5F, 0.0F);
     * }
     * </pre>
     * <p>而非 slim 是 {@code ModelBiped} 里的 <b>2.0F</b>。
     * 上游的 {@code reset()} 两处都硬编码 2.0F，且它构造 {@code PlayerModelTransformer} 时传进来的
     * {@code slim} 参数<b>从未被使用</b> —— 也就是说：<b>上游对 slim 模型每帧都会把手臂多按下去 0.5</b>。
     * 1.12.2 版把这个参数用起来，按 {@code slim} 取正确的值。</p>
     */
    public void reset() {
        resetModel(model.bipedHead);
        resetModel(model.bipedHeadwear);
        resetModel(model.bipedBodyWear);
        resetModel(model.bipedBody);
        float armRotationPointY = slim ? 2.5F : 2.0F;
        {
            resetModel(model.bipedRightArm);
            model.bipedRightArm.rotationPointX = -5.0F;
            model.bipedRightArm.rotationPointY = armRotationPointY;
            model.bipedRightArm.rotationPointZ = 0.0F;
            copyFrom(model.bipedRightArm, model.bipedRightArmwear);
        }
        {
            resetModel(model.bipedLeftArm);
            model.bipedLeftArm.rotationPointX = 5.0F;
            model.bipedLeftArm.rotationPointY = armRotationPointY;
            model.bipedLeftArm.rotationPointZ = 0.0F;
            copyFrom(model.bipedLeftArm, model.bipedLeftArmwear);
        }
        {
            resetModel(model.bipedLeftLeg);
            model.bipedLeftLeg.rotationPointX = 1.9F;
            model.bipedLeftLeg.rotationPointY = 12.0F;
            model.bipedLeftLeg.rotationPointZ = 0.0F;
            copyFrom(model.bipedLeftLeg, model.bipedLeftLegwear);
        }
        {
            resetModel(model.bipedRightLeg);
            model.bipedRightLeg.rotationPointX = -1.9F;
            model.bipedRightLeg.rotationPointY = 12.0F;
            model.bipedRightLeg.rotationPointZ = 0.0F;
            copyFrom(model.bipedRightLeg, model.bipedRightLegwear);
        }
        model.bipedHeadwear.rotationPointX = 0.0F;
        model.bipedHeadwear.rotationPointY = 0.0F;
        model.bipedHeadwear.rotationPointZ = 0.0F;
    }

    public void resetModel(ModelRenderer model) {
        model.rotateAngleX = 0;
        model.rotateAngleY = 0;
        model.rotateAngleZ = 0;
        model.rotationPointX = 0;
        model.rotationPointY = 0;
        model.rotationPointZ = 0;
    }

    // ── 内部工具（顶替 1.16 有、1.12.2 没有的 API） ─────────────────────────

    private void setRotations(ModelRenderer renderer, float angleX, float angleY, float angleZ) {
        renderer.rotateAngleX = angleX;
        renderer.rotateAngleY = angleY;
        renderer.rotateAngleZ = angleZ;
    }

    /**
     * 顶替 1.16 的 {@code ModelRenderer#copyFrom(other)}（1.12.2 的 {@code ModelRenderer} 没有这个方法）。
     * 复制的是「旋转 + 旋转基点」共 6 个字段 —— 正好是 copyFrom 在 1.16 里同步的那一组。
     */
    private static void copyFrom(ModelRenderer from, ModelRenderer to) {
        to.rotateAngleX = from.rotateAngleX;
        to.rotateAngleY = from.rotateAngleY;
        to.rotateAngleZ = from.rotateAngleZ;
        to.rotationPointX = from.rotationPointX;
        to.rotationPointY = from.rotationPointY;
        to.rotationPointZ = from.rotationPointZ;
    }

    /**
     * 顶替 1.16 的 {@code ModelHelper.bobArms(a, b, ageInTicks)}（1.12.2 没有 {@code ModelHelper}）。
     *
     * <p>用的就是 1.12.2 {@code ModelBiped.setRotationAngles} 里那段经典手臂摆动 ——
     * 同一套系数（0.09 / 0.05 / 0.067 / 0.05），只是把相位参数换成传入的 {@code ageInTicks}。</p>
     */
    private static void bobArms(ModelRenderer a, ModelRenderer b, float ageInTicks) {
        a.rotateAngleZ += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        b.rotateAngleZ -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
        a.rotateAngleX += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        b.rotateAngleX -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
    }
}
