package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.renderer.ArmorPoseHelper;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让**护甲模型跟着主模型的姿态走** —— 修「穿护甲做跑酷动作时护甲与身体错位」。
 *
 * <h3>问题本质</h3>
 * <p>1.12.2 的 {@code ModelBiped#render(Entity, …)} <b>第一行就调 {@code setRotationAngles}</b> ✗，
 * 而 {@code LayerArmorBase#renderArmorLayer} 对护甲模型只
 * {@code setModelAttributes(mainModel)}（只拷 {@code isSneak}/{@code armPose}）✗ 然后 {@code t.render(…)} ✓
 * → 护甲<b>把自己按原版姿态重算一遍</b> ✗ → 身体是 ParCool 的姿态、护甲是原版姿态 ✓ → 错位 ✓。
 * （1.16 没这问题：{@code setupAnim} 与绘制分离，护甲层显式 {@code copyPropertiesTo} ✓）</p>
 *
 * <h3>注入点的选择（这里有个冲突要绕开）</h3>
 * <ul>
 *   <li>❌ <b>不能</b> {@code @Redirect} 掉 {@code render} 里对 {@code setRotationAngles} 的调用 ——
 *       <b>Aqua Acrobatics 已经占了那个点</b>（{@code ModelBipedMixin:47} 的
 *       {@code @Redirect(method="render", at=INVOKE setRotationAngles)}）→ 两个 mod 同时 Redirect
 *       同一点会<b>加载期冲突</b> ✗</li>
 *   <li>⚠️ 也不能只在 <b>TAIL</b> 覆盖 —— Aqua 在 {@code setRotationAngles} <b>中段</b>有一个
 *       {@code cancellable} 注入（{@code :103}，遇到游泳会提前 return）✗ → 我们的 TAIL 不会执行 ✗
 *       → 游泳时护甲又错位 ✗</li>
 *   <li>✅ <b>HEAD + {@code ci.cancel()}</b>：先抄主模型的姿态、再整个跳过重算 ✓
 *       （Aqua 的泳姿已经在<b>主模型</b>上算过了 ✓，抄过来即得 ✓）</li>
 * </ul>
 * <p>注入的是 {@code ModelBiped#setRotationAngles}（方法本体），与 Aqua 的 {@code render} 调用点
 * <b>不同方法、不同注入点</b> → 不冲突 ✓。</p>
 *
 * <h3>★ 两道守卫，确保只碰「护甲模型」</h3>
 * <ol>
 *   <li>{@code this instanceof ModelPlayer} → 直接放过 ✓ ——
 *       玩家主模型、以及第一人称手臂用的模型<b>都是 {@code ModelPlayer}</b> ✓，
 *       护甲模型是 {@code LayerBipedArmor#initArmor} 里 {@code new ModelBiped(0.5F/1.0F)} 的
 *       <b>裸 {@code ModelBiped}</b> ✓（已核实）→ 这条守卫精确区分两者 ✓</li>
 *   <li>{@link ArmorPoseHelper#resolvePlayerMainModel} 只认 {@code EntityPlayer} ✓ ——
 *       僵尸/骷髅等穿戴的护甲仍走原版逻辑 ✓（它们的模型与姿态都是原版的，本来就不会错位 ✓）</li>
 * </ol>
 *
 * <p><b>只由 {@code mixins.parcool.json} 的 {@code client} 数组加载</b> ✓ ——
 * 专用服务端不会应用它（否则会碰到被 {@code @SideOnly(CLIENT)} 剥掉的 {@code ModelPlayer} ✗）。</p>
 */
@Mixin(ModelBiped.class)
public abstract class ModelBipedMixin {

    @Inject(method = "setRotationAngles", at = @At("HEAD"), cancellable = true)
    private void parCool$copyPlayerPoseToArmorModel(float limbSwing, float limbSwingAmount, float ageInTicks,
                                                    float netHeadYaw, float headPitch, float scaleFactor,
                                                    Entity entityIn, CallbackInfo ci) {
        // 守卫 ①：主模型 / 第一人称手臂都是 ModelPlayer —— 交给 ModelPlayerMixin 处理，这里不插手
        ModelBiped self = (ModelBiped) (Object) this;
        if (self instanceof ModelPlayer) return;

        // 守卫 ②：只认玩家（护甲层的 entityIn 就是穿戴者 ✓）
        ModelBiped main = ArmorPoseHelper.resolvePlayerMainModel(entityIn);
        if (main == null || main == self) return;

        // 把主模型（已被 ParCool / Aqua 摆好）的姿态抄过来，然后跳过原版重算 ✓
        ArmorPoseHelper.copyPose(main, self);
        ci.cancel();
    }
}
