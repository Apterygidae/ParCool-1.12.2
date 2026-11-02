package com.alrex.parcool.mixin.common;

import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.core.P0Verifier;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P0 的 Mixin 注入验证。
 *
 * <p>为什么选 {@code EntityPlayer#onLivingUpdate} 作为注入点：</p>
 * <ul>
 *   <li>它<b>声明在 EntityPlayer 自己身上</b>（覆写了 EntityLivingBase 的同名方法）。
 *       这点很关键 —— Mixin 的 {@code @Inject} 只在目标类<b>自身声明</b>的方法里找注入点，
 *       若写成父类才有的方法（例如 1.16 的 {@code jumpFromGround}／1.12.2 的 {@code jump}，
 *       都声明在 EntityLivingBase 上），会直接报 "Cannot find method"。</li>
 *   <li>方法名 {@code onLivingUpdate} 在 obf 下会被映射成 srg 名，因此这一处注入
 *       <b>同时验证了 refmap 是否生效</b> —— 这是 1.12.2 最容易静默失败的环节。</li>
 *   <li>它每 tick 都跑，进世界后立刻能看到结果。</li>
 * </ul>
 *
 * <p>{@code priority = 500}：Aqua 默认 1000，数值越小越先应用、其注入也越先执行。
 * 这个优先级是 P3 处理 {@code EntityPlayer#getEyeHeight()} 硬冲突的预留手段
 * （见移植方案 §4.3），此处提前设定以保证后续行为一致。</p>
 *
 * <p>注意：本 mixin 与 Aqua 的 {@code EntityPlayerMixin} 注入同一个方法，但注入点不同
 * （Aqua 打在 {@code wasFallFlying} 字段处，不是 HEAD），因此可以共存 —— 这正好是
 * 一次「硬依赖共存」的早期验证。</p>
 */
@Mixin(value = EntityPlayer.class, priority = 500)
public abstract class EntityPlayerMixin extends EntityLivingBase {

    protected EntityPlayerMixin(World worldIn) {
        super(worldIn);
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void parcool$verifyInjection(CallbackInfo ci) {
        P0Verifier.markMixinApplied("EntityPlayer#onLivingUpdate");
    }

    /**
     * 「屏蔽鞘翅」——{@code BehaviorEnforcer.cancelFallFlying()} 的应用层 ✓
     *
     * <h3>★ 为什么放在这里而不是 {@code ActionProcessor}</h3>
     * <p>{@code Entity#setFlag(int, boolean)} 是 <b>{@code protected}</b> ✗ ——
     * 外部类调不到 ✓ 而本 mixin 继承 {@code EntityLivingBase}，
     * <b>在 {@code Entity} 子类内部就能调</b> ✓</p>
     *
     * <h3>★ 为什么用 TAIL 而不是 HEAD</h3>
     * <p>{@code updateElytra()}（负责按「是否还穿着鞘翅 / 是否落地」重设 flag 7）
     * 是在 {@code super.onLivingUpdate()} 链路里跑的 ✗ ——
     * 若在 HEAD 清 flag，结尾会被 {@code updateElytra} 再设回来 ✗
     * → <b>放在 TAIL，保证这一 tick 结束时 flag 7 一定是 false</b> ✓</p>
     *
     * <h3>⚠️ 1.12.2 为什么不能用上游的注入点</h3>
     * <p>上游注入 {@code PlayerEntity#tryToStartFallFlying} ✗ ——
     * <b>1.12.2 没有这个方法</b> ✗。它的起飞链路是：</p>
     * <pre>
     * 客户端 EntityPlayerSP:1065 发 CPacketEntityAction(START_FALL_FLYING)
     *   → 服务端 NetHandlerPlayServer 处理 → EntityPlayerMP:1605 setFlag(7, true)
     * </pre>
     * <p><b>没有单一可注入的方法</b> ✗ → 改为「每 tick 压回 false」✓
     * 效果：既阻止起飞、也终止进行中的滑翔 ✓
     * （flag 7 = 鞘翅，见 1.12.2 的 FLAGS 位定义 ✓）</p>
     */
    @Inject(method = "onLivingUpdate", at = @At("TAIL"))
    private void parcool$cancelFallFlying(CallbackInfo ci) {
        EntityPlayer self = (EntityPlayer) (Object) this;
        Parkourability parkourability = Parkourability.get(self);
        if (parkourability == null) return;
        if (parkourability.getBehaviorEnforcer().cancelFallFlying()) {
            // ⚠️ 必须是隐式 this —— Java 的 protected 只允许通过【本类实例】访问 ✗
            //    写成 `self.setFlag(...)` 会报「protected 访问控制」✗
            //    （self 是 EntityPlayer 类型的引用，编译器只看到本 mixin 继承 EntityLivingBase ✗）
            setFlag(7, false);
        }
    }

    /**
     * 「屏蔽跳跃」——{@code BehaviorEnforcer.cancelJump()} 的应用层 ✓
     *
     * <p><b>★ 为什么能注入 {@code jump}</b>：1.12.2 的 {@code EntityPlayer} <b>自己覆写</b>了
     * {@code EntityLivingBase#jump}（{@code EntityPlayer.java:1952}）→ 是「目标类自身声明的方法」✓
     * 若它没覆写，注入就会报 "Cannot find method" 并静默失败 ✗（本工程踩过一次 ✗）</p>
     *
     * <p>被谁用到：{@code ClingToCliff} / {@code WallSlide} / {@code Dodge} 等登记了跳跃屏蔽的动作 ✓</p>
     */
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void parcool$onJump(CallbackInfo ci) {
        Parkourability parkourability = Parkourability.get((EntityPlayer) (Object) this);
        if (parkourability == null) return;
        if (parkourability.getBehaviorEnforcer().cancelJump()) {
            ci.cancel();
        }
    }
}
