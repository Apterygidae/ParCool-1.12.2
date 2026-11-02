package com.alrex.parcool.mixin.client;

import com.alrex.parcool.client.RenderBehaviorEnforcer;
import com.alrex.parcool.common.action.impl.HideInBlock;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修掉「**藏进方块时第三人称相机被压到脸前**」✗
 *
 * <h3>★ 症状（实测 2026-10-02）</h3>
 * <p>站姿藏进方块（即藏身处是<b>竖直 2 格</b>）时，第三人称相机<b>钻进方块内部</b> ✗
 * 而「平躺藏进横向 2 格」时相机正常在外 ✓
 * <b>已到上游 1.16.5 实机确认：上游相机在外面，这是移植缺陷</b> ✓</p>
 *
 * <h3>★ 原因</h3>
 * <p>{@code EntityRenderer.orientCamera} 算第三人称距离时会从<b>玩家眼睛</b>向外打 8 条射线
 * 检测遮挡（反编译源码 {@code EntityRenderer.java:653}）：</p>
 * <pre>
 * RayTraceResult raytraceresult = this.mc.world.rayTraceBlocks(start, end);
 * if (raytraceresult != null) { 缩短相机距离 d3 }
 * </pre>
 * <p>而「竖直 2 格藏身处」里，玩家眼睛（约 1.62 高）正好<b>位于上方那一格里</b> ✗
 * → 射线<b>起点就在方块内部</b> → 立刻命中 → 相机距离被压到 0 ✗
 * → 相机贴在脸上，也就是「卡在方块内」✗✓✓✓</p>
 *
 * <h3>★ 修法</h3>
 * <p>{@code @Redirect} 掉这次射线检测，在「相机强制生效期间」<b>返回 {@code null}（无遮挡）</b> ✓
 * 原版的 {@code if (raytraceresult != null)} 自然不成立 → 相机保持正常距离 ✓
 * <b>只影响藏进方块的那段时间</b>，其余情况原样放行 ✓</p>
 *
 * <h3>🚨🚨 第一次写崩了游戏 —— INVOKE 的 <b>owner 类</b>写错（值得记住）</h3>
 * <p>第一版 target 写的是 <code>Lnet/minecraft/world/World;rayTraceBlocks(...)</code> ✗
 * 运行期报：</p>
 * <pre>
 * InjectionError: Critical injection failure: Redirector ... failed injection check,
 * (0/1) succeeded. **Scanned 0 target(s)**.
 * </pre>
 * <p><b>原因</b>：源码写的是 {@code this.mc.world.rayTraceBlocks(...)}，
 * 而 <b>{@code Minecraft.world} 的声明类型是 {@code WorldClient}</b> ✗
 * → 字节码里 INVOKE 的 owner 是 <b>{@code WorldClient}</b>，不是 {@code World} ✗
 * → <b>owner 不匹配 → 一个调用点都匹配不到</b> ✗✓✓</p>
 * <p><b>⭐ 教训 1</b>：{@code @Redirect}/{@code @Inject} 的 INVOKE target 必须写
 * <b>字节码里实际出现的那个 owner</b> —— 也就是<b>接收者表达式的静态类型</b>，
 * 而不是「这个方法定义在哪个类」✗
 * （{@code WorldClient} 继承 {@code World#rayTraceBlocks}，但调用点 owner 仍是 {@code WorldClient} ✓）</p>
 * <p><b>⭐ 教训 2（这条更狠）</b>：注入失败<b>直接让整个游戏起不来</b> ✗ ——
 * 因为 {@code EntityRenderer} 类转换失败 → 后续引用它的代码全部 {@code NoClassDefFoundError} ✗
 * → <b>本 mixin 加上 {@code require = 0}：再失配也只是这个修复"静默失效"，不会崩游戏</b> ✓
 * （{@code require = 0} 表示「允许 0 个匹配」；配合 refmap 自检仍能发现失配 ✓）</p>
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Redirect(
            method = "orientCamera",
            // require = 0：找不到也要放行，不崩游戏 ✓
            // expect  = 0：不限制匹配个数（默认若为 1，将来出现第二个调用点会报错 ✗）
            // ⭐ 这两个值与 Aqua 的 EntityRendererMixin 一致（那是本环境里能正常工作的参照 ✓）
            require = 0,
            expect = 0,
            at = @At(
                    value = "INVOKE",
                    // ⚠️ owner 必须是 WorldClient（= Minecraft.world 的声明类型），不是 World ✗
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;rayTraceBlocks(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/RayTraceResult;"
            )
    )
    /**
     * ⚠️⚠️ **handler 的第一个参数必须与 target 的 receiver 类型一致** ✗
     *
     * <p>写成 {@code World} 会在运行期抛
     * {@code InvalidInjectionException ... Injector.validateParams} ✗
     * —— 因为 receiver 是 {@code WorldClient} ✗</p>
     *
     * <p><b>⭐ 这是同一个坑的第二处</b>：改了 {@code target} 字符串里的 owner，
     * <b>handler 的参数也必须跟着改</b> ✗ —— 两处要<b>一致</b> ✓</p>
     *
     * <p><b>⭐ 另注</b>：{@code require = 0} 只挡「0 个匹配」✗，
     * <b>挡不住参数校验失败</b> ✗ —— 参数写错照样崩游戏 ✓</p>
     */
    private RayTraceResult parCool$ignoreCameraClipWhileHiding(WorldClient world, Vec3d start, Vec3d end) {
        RayTraceResult result = world.rayTraceBlocks(start, end);

        // 只有「相机被强制」期间（= 有动作在要求第三人称）才做特殊处理 ✓
        if (RenderBehaviorEnforcer.getEnforcedCameraType() == null) {
            return result;
        }

        // ★★ 命中「藏身方块自己」时，**越过它继续往后打**，而不是当作「无遮挡」✗
        //
        // 为什么不能直接 `return null` ✗（第一版就是这么写错的 ✗）：
        //   `rayTraceBlocks` 只返回【第一个】命中 ✗ ——
        //   藏身时眼睛在方块内部 → 第一个命中的就是**藏身方块**本身 ✗
        //   若直接返回 null（= 「前方无遮挡」）→ **后面的墙根本没被检查** ✗
        //   → `d3` 保持满值 4.0 → 相机穿进周围的墙 → **透视** ✗（实测确认 ✓）
        //
        // ✅ 正确：把射线起点挪到命中点外侧一点，**再打一次** ✗→✓
        //    这样藏身方块被跳过，而**后面的墙照常把相机挡住** ✓
        //    循环是为了「藏身处有多格」时把每一格都跳过 ✓（上限 4 次，防死循环 ✓）
        Vec3d rayDir = end.subtract(start);
        // ⚠️ 1.12.2 的 Vec3d **没有** lengthVector() ✗ → 手算，不依赖方法名 ✓
        double rayLen = Math.sqrt(rayDir.x * rayDir.x + rayDir.y * rayDir.y + rayDir.z * rayDir.z);
        if (rayLen < 1.0E-4D) {
            return result;
        }
        int guard = 0;
        while (result != null
                && result.typeOfHit == RayTraceResult.Type.BLOCK
                && HideInBlock.isHidingBlockAt(result.getBlockPos())
                && guard++ < 4) {
            // 从命中点沿射线方向挪出 0.01 格（足够离开该方块的碰撞箱 ✓）
            Vec3d nextStart = result.hitVec.add(rayDir.scale(0.01D / rayLen));
            result = world.rayTraceBlocks(nextStart, end);
        }
        return result;
    }
}
