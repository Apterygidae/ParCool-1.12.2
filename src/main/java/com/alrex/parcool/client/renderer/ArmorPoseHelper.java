package com.alrex.parcool.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 把「玩家主模型已经摆好的姿态」抄给护甲模型 —— 修「穿护甲做跑酷动作时护甲和身体错位」。
 *
 * <h3>🔴 为什么 1.12.2 必须额外做这一步（1.16 不需要）</h3>
 * <table border="1">
 *   <tr><th></th><th>1.16.5（上游）</th><th>1.12.2</th></tr>
 *   <tr><td>姿态计算</td>
 *       <td>{@code setupAnim} 与「绘制」<b>分离</b> ✓</td>
 *       <td>{@code ModelBiped#render(Entity, …)} <b>开头就调 {@code setRotationAngles}</b> ✗
 *           （{@code ModelBiped.render} 第 1 行，已核实）</td></tr>
 *   <tr><td>护甲层怎么同步</td>
 *       <td>{@code BipedArmorLayer} 显式
 *           {@code getParentModel().copyPropertiesTo(armorModel)} ✓（连各部位旋转一起拷 ✓）</td>
 *       <td>{@code LayerArmorBase#renderArmorLayer} 只调
 *           {@code t.setModelAttributes(mainModel)} ✗ —— 它<b>只拷 {@code isSneak}/{@code leftArmPose}/{@code rightArmPose}</b>
 *           （{@code ModelBiped#setModelAttributes}，已核实）✗，
 *           然后 {@code t.render(…)} 里<b>重新算一遍原版姿态</b> ✗</td></tr>
 * </table>
 * <p>→ 结果：<b>身体是 ParCool 的姿态、护甲是原版姿态</b> ✗ → 穿护甲做动作时明显错位 ✓
 * （实测症状：蓄力高跳时胸甲悬在头周围、靴子与腿错开 ✓）。</p>
 *
 * <h3>★ 修法：在护甲模型「算完姿态」的位置把它改成「抄主模型的」</h3>
 * <p>见 {@code mixin/client/ModelBipedMixin}：在 {@code ModelBiped#setRotationAngles} 的
 * <b>HEAD 取消</b>，改为调用本类的 {@link #copyPose} ✓ —— 而不是「算完再覆盖」✗：
 * 那样会被 Aqua 在方法中段的 {@code cancellable} 注入提前返回，导致游泳时又不同步 ✗。</p>
 *
 * <p>⚠️ <b>为什么用 {@code setRotationAngles} 而不用 {@code @Redirect} 掉调用点</b> ✗：
 * Aqua Acrobatics 已经用
 * {@code @Redirect(method="render", at=INVOKE setRotationAngles)} 占住了那个调用点
 * （{@code ModelBipedMixin:47}，已核实）→ 两个 mod 同时 Redirect 同一点会<b>加载期冲突</b> ✗。
 * 本类改注 {@code setRotationAngles} 方法本体（不同方法、不同注入点）→ <b>不冲突</b> ✓。</p>
 *
 * <h3>★ 为什么可以安全地「无条件抄」</h3>
 * <p>不区分「是否正在做跑酷动作」✓ —— 因为主模型在<b>原版状态下</b>算出来的姿态与护甲自己算的
 * <b>本就一致</b> ✓（同一套 {@code setRotationAngles} + 同一 {@code isSneak}），
 * 抄过去等于没变 ✓；只有被 ParCool（或 Aqua）改过时才会产生差异 ✓ —— 而那正是我们想要的 ✓。</p>
 *
 * <p>渲染顺序保证「抄的时候主模型已经算完」✓：
 * {@code RenderLivingBase#doRender} 先 {@code renderModel}（:164）→ 再 {@code renderLayers}（:169）✓。</p>
 */
@SideOnly(Side.CLIENT)
public final class ArmorPoseHelper {

    /**
     * 1 条目缓存 ✓ —— 护甲层每帧会问 4 次（头/胸/腿/脚），
     * 而 {@code getEntityRenderObject} 要走一次类映射查找 ✓。
     */
    @Nullable
    private static EntityPlayer cachedPlayer = null;
    @Nullable
    private static ModelBiped cachedMainModel = null;

    private ArmorPoseHelper() {
    }

    /**
     * 取该玩家<b>当前正在用的主模型</b>（即被 {@code ModelPlayerMixin} 改过的那一个）。
     *
     * @return 非玩家、或渲染器不是 {@code RenderPlayer}（例如被别的 mod 换掉）时返回 {@code null} ✓
     */
    @Nullable
    public static ModelBiped resolvePlayerMainModel(@Nullable Entity entity) {
        if (!(entity instanceof EntityPlayer)) return null;
        EntityPlayer player = (EntityPlayer) entity;

        if (player == cachedPlayer) return cachedMainModel;

        ModelBiped main = null;
        // ⚠️ 必须用通配符（或裸类型）✓ —— {@code RenderPlayer} 继承的是
        //     {@code Render<AbstractClientPlayer>}，泛型不变 → 它**不可能**是
        //     {@code Render<EntityPlayer>} ✗ → 那样写 {@code instanceof} 会直接编译失败 ✗（实测踩过）
        Render<?> renderer = Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(player);
        if (renderer instanceof RenderPlayer) {
            main = ((RenderPlayer) renderer).getMainModel();
        }
        cachedPlayer = player;
        cachedMainModel = main;
        return main;
    }

    /**
     * 把 {@code source} 的七个共用部位（<b>旋转角 + 旋转基点</b>）抄给 {@code dest} ✓。
     *
     * <p>⚠️ <b>只抄这两个东西</b>，<b>不碰</b> {@code isHidden}/{@code isChild}/{@code isSneak} ✗ ——
     * 那些决定「哪几片护甲该显示、要不要按小孩缩放」，是 {@code setModelSlotVisible} 与
     * {@code setModelAttributes} 的职责 ✓，抄过来会把头盔/护腿的显示逻辑搞坏 ✗。</p>
     *
     * <p>用 {@code ModelBase.copyModelAngles} ✓ —— 它连 {@code rotationPointX/Y/Z} 一起拷 ✓，
     * 而 ParCool 的动画器<b>确实会改 {@code rotationPoint}</b>（手臂/腿/头的偏移）✓，
     * 只拷 {@code rotateAngle} 是不够的 ✗。</p>
     */
    public static void copyPose(ModelBiped source, ModelBiped dest) {
        ModelBase.copyModelAngles(source.bipedHead, dest.bipedHead);
        ModelBase.copyModelAngles(source.bipedHeadwear, dest.bipedHeadwear);
        ModelBase.copyModelAngles(source.bipedBody, dest.bipedBody);
        ModelBase.copyModelAngles(source.bipedRightArm, dest.bipedRightArm);
        ModelBase.copyModelAngles(source.bipedLeftArm, dest.bipedLeftArm);
        ModelBase.copyModelAngles(source.bipedRightLeg, dest.bipedRightLeg);
        ModelBase.copyModelAngles(source.bipedLeftLeg, dest.bipedLeftLeg);
    }
}
