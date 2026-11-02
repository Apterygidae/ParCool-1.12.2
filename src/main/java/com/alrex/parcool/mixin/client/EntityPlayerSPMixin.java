package com.alrex.parcool.mixin.client;

import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.MoverType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code BehaviorEnforcer} 的**应用层** ✓ —— 1.12.2 版。
 *
 * <h3>★ 它为什么存在</h3>
 * <p>很多动作需要**强制**玩家的位置或状态 ✓（滑索把玩家拖着走、藏方块时不许潜行……✓），
 * 但那些状态**不能靠常规物理实现** ✗ —— 因为原版的 {@code move()} / {@code travel()}
 * 每 tick 都会覆盖掉 ✓。</p>
 * <p>本工程的做法：动作只往 {@code BehaviorEnforcer} 里**挂标记** ✓，
 * 真正的「替换位移 / 拦截疾跑」**在这里做** ✓
 * （对应上游的 {@code ClientPlayerEntityMixin} ✓）。</p>
 *
 * <h3>★ 四个注入（与上游一一对应 ✓）</h3>
 * <table border="1">
 *   <tr><th>1.16.5 上游</th><th>1.12.2</th></tr>
 *   <tr><td>{@code move(MoverType, Vector3d)}</td>
 *       <td>{@code move(MoverType, double, double, double)} ✗（坐标拆成三个参数 ✓）</td></tr>
 *   <tr><td>{@code isShiftKeyDown()}</td><td>{@code isSneaking()} ✗</td></tr>
 *   <tr><td>{@code aiStep()}</td><td>{@code onLivingUpdate()} ✗</td></tr>
 *   <tr><td>{@code setSprinting(boolean)}</td><td>✓ 同名 ✓</td></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@code EntityPlayerSP} ✗（在 client 包 ✓）</td></tr>
 * </table>
 *
 * <p><b>⚠️ {@code super.move(...)} 在 mixin 里是合法的</b> ✓ ——
 * mixin 类**继承目标类** ✓，所以 {@code super.move} 落到
 * {@code Entity#move(MoverType, double, double, double)} ✓，不会递归 ✓。</p>
 *
 * <p><b>⚠️ 只在客户端注入</b> ✗ —— 这个 mixin 定义在配置的 {@code client} 段 ✓，
 * 专用服务端不会加载 ✓（{@code EntityPlayerSP} 本身也是客户端类 ✓）。</p>
 */
@Mixin(EntityPlayerSP.class)
public class EntityPlayerSPMixin {

    @Shadow
    public net.minecraft.util.MovementInput movementInput;

    /**
     * 上一 tick 是否在疾跑 ✓ —— 用于 {@link #onLivingUpdate} 里**维持**疾跑 ✓。
     *
     * <p>{@b ⚠️ 实例字段} ✗：Mixin 会给每个玩家对象各存一份 ✓
     * （不能用 static ✗，否则多个客户端玩家会互相干扰 ✓）。</p>
     */
    private boolean parcoolOldSprinting = false;

    /**
     * ★ 位移替换 ✓ —— 本类**最核心**的一个注入。
     *
     * <p>两条分支 ✓（与上游一致 ✓）：</p>
     * <ol>
     *   <li>{@code getEnforcedPosition()} 不为 null ✓ —— <b>直接瞬移</b> ✗
     *       （藏进方块用 ✓）—— 连包围盒一起挪 ✓，再让实体按新盒子同步位置 ✓</li>
     *   <li>{@code getEnforcedMovePoint()} 不为 null 且是**自身移动** ✓ ——
     *       <b>把这一 tick 的位移换成"朝目标点"</b> ✗ ✓（滑索拖拽用 ✓）</li>
     * </ol>
     *
     * <p>{@code casuable = true} 是必需的 ✓ —— 要能取消原版自己的位移 ✓。</p>
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    public void onMove(MoverType type, double x, double y, double z, CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        // ── 分支 ①：强制位置（直取坐标 ✓，常用于「藏进方块」✓）──
        Vec3d enforcedPos = parkourability.getBehaviorEnforcer().getEnforcedPosition();
        if (enforcedPos != null) {
            ci.cancel();
            Vec3d delta = enforcedPos.subtract(player.getPositionVector());
            // ⚠️ 1.12.2 叫 setEntityBoundingBox ✗（不是 setBoundingBox ✓）
            player.setEntityBoundingBox(player.getEntityBoundingBox().offset(delta.x, delta.y, delta.z));
            // ⚠️ 1.12.2 没有 setLocationFromBoundingbox ✗ → 用 setPosition ✓
            player.setPosition(player.posX, player.posY, player.posZ);
            return;
        }

        // ⚠️ 只在「自身移动」时接管 ✗ —— 被活塞推、被载具带时不能抢 ✓
        if (type != MoverType.SELF) return;

        // ── 分支 ②：强制移动点（本 tick 直接朝目标飞过去 ✓，滑索用 ✓）──
        Vec3d enforcedMovePos = parkourability.getBehaviorEnforcer().getEnforcedMovePoint();
        if (enforcedMovePos != null) {
            ci.cancel();
            Vec3d delta = enforcedMovePos.subtract(player.getPositionVector());

            // ⚠️★ 关键：`cancel()` 之后**原版的碰撞检测就没跑了** ✗★
            //   而 `Entity.move` 用的是**传入的 x/y/z 参数** ✗（不是 motion ✗），
            //   所以也不能靠"改 motion 再放行"绕过 ✓。
            //   → `collidedHorizontally / collidedVertically` 会**永远是 false** ✗
            //     → 动作里「撞到就脱手」的判定**永不触发** ✓
            //   ✅ 所以这里**手动做一次碰撞检测** ✓，并把结果写回原版的碰撞标记 ✓
            AxisAlignedBB targetBox = player.getEntityBoundingBox().offset(delta.x, delta.y, delta.z);
            boolean blocked = !player.world.getCollisionBoxes(player, targetBox).isEmpty();

            if (blocked) {
                // ⚠️★★ 撞到时就【不要再移动】✗★★（2026-10-11 二次修正 ✓）
                //   上一版我"照做位移 ✗，让动作下一 tick 判定脱手" ✓ —— **那是错的** ✗：
                //     · 玩家被**硬塞进方块内部** ✗
                //     · 原版物理下一 tick 把他**挤出来** ✗
                //     · 动作又把他**拖进去** ✗ → **原地来回抽插** ✓
                //   现在的行为 ✓：
                //     · 如实置位碰撞标记 ✗ → 动作下一 tick 就会**正常脱手** ✓
                //     · **原地不动** ✗ → 没有"被塞进去"这回事 ✓
                //     · 速度清零 ✓ → 原版物理不会把玩家弹开 ✓
                player.collidedHorizontally = true;
                player.collidedVertically = false;
                player.motionX = 0;
                player.motionY = 0;
                player.motionZ = 0;
                return;   // ⚠️ ci.cancel() 已生效 ✓ → 本 tick 玩家原地不动 ✓
            }

            player.collidedHorizontally = false;
            player.collidedVertically = false;

            // 没被挡 → 正常沿索前进 ✓
            player.motionX = delta.x;
            player.motionY = delta.y;
            player.motionZ = delta.z;
            player.setEntityBoundingBox(targetBox);
            player.setPosition(player.posX + delta.x, player.posY + delta.y, player.posZ + delta.z);
        }
    }

    /**
     * 拦截潜行 ✓（对应上游的 {@code isShiftKeyDown} ✓）。
     *
     * <p>1.12.2 里真正被各处调用的是 {@code isSneaking()} ✗ ——
     * 而 {@code EntityPlayerSP} **自己覆写了它** ✓，所以注入这个类就够 ✓。</p>
     */
    @Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
    public void onIsSneaking(CallbackInfoReturnable<Boolean> cir) {
        Parkourability parkourability = Parkourability.get((EntityPlayerSP) (Object) this);
        if (parkourability == null) return;
        if (parkourability.getBehaviorEnforcer().cancelSneak()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 拦截疾跑 ✓。
     *
     * <p>对应上游的 {@code setSprinting} 注入 ✓ —— 滑索、翻滚等动作期间禁止疾跑 ✓。</p>
     */
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    public void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        Parkourability parkourability = Parkourability.get((EntityPlayerSP) (Object) this);
        if (parkourability != null && parkourability.getBehaviorEnforcer().cancelSprint()) {
            ci.cancel();
        }
    }

    /**
     * 疾跑的**维持** ✓（对应上游的 {@code aiStep} ✓）。
     *
     * <p>原版只有在「前进且饱食度够」时才保持疾跑 ✓；
     * 某些动作（如疾跑 FastRun ✓）希望玩家**松开前进键也能继续跑一会儿** ✗，
     * 于是这里把上一 tick 的疾跑状态**重新塞回去** ✓。</p>
     *
     * <p><b>⚠️ 只在本地玩家身上做</b> ✗ —— 其他玩家由服务端同步 ✓。</p>
     */
    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    public void onLivingUpdate(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (!player.world.isRemote) return;

        // 原版认为「不该疾跑」的条件 ✓（没有前进输入、或饱食度 ≤ 6 且不能飞 ✓）
        boolean noForward = this.movementInput.moveForward == 0.0F;
        boolean lowFood = !(player.getFoodStats().getFoodLevel() > 6.0F || player.capabilities.allowFlying);
        boolean shouldNotSprint = noForward || lowFood || (player.isInWater() && !player.isInsideOfMaterial(net.minecraft.block.material.Material.WATER));

        // 上一 tick 还在跑、且本 tick 没有"必须停"的硬理由 → 把疾跑恢复回去 ✓
        if (parcoolOldSprinting && !shouldNotSprint) {
            player.setSprinting(true);
        }
        parcoolOldSprinting = player.isSprinting();
    }
}
