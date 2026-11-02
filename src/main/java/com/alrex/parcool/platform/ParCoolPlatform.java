package com.alrex.parcool.platform;

import com.fuzs.aquaacrobatics.client.entity.IPlayerSPSwimming;
import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * <b>Aqua Acrobatics 硬依赖 API 的唯一收口点。</b>
 *
 * <p>为什么必须有这个类：ParCool 1.16.5 里有 8 个文件直接调用
 * {@code player.isSwimming()} / {@code getDimensions(Pose)} / {@code isVisuallyCrawling()} 这类
 * 1.13+ 才有的引擎 API。在 1.12.2 上它们全部由 Aqua 提供，调用点散落各处就会变成
 * 20 多处 {@code (IPlayerResizeable) player} 强转 —— 一旦 Aqua 换实现或我们换依赖方式，
 * 改动面无法收敛。</p>
 *
 * <p>因此约定：<b>ParCool 里任何地方都不允许直接写 {@code (IPlayerResizeable) player}，
 * 一律走本类。</b>这样将来若要改依赖方式，只改这一个文件。</p>
 *
 * <p>关于强转的安全性：Aqua 通过 mixin 把 {@link IPlayerResizeable} 加到了
 * {@code EntityPlayer} 上，而 {@code aquaacrobatics} 是 FML 强制的前置
 * （见 {@code ParCool.DEPENDENCIES}），所以运行期这个强转<b>永远成立</b>。
 * 唯一例外是专用服务端的某些早期阶段 —— 见 {@link #isReady(EntityPlayer)}。</p>
 *
 * <p><b>1.16.5 → Aqua 语义映射表</b>（移植时逐条对照）：</p>
 * <table border="1">
 *   <tr><th>ParCool 1.16.5</th><th>本类</th></tr>
 *   <tr><td>{@code player.isSwimming()}</td><td>{@link #isSwimming}</td></tr>
 *   <tr><td>{@code player.isVisuallyCrawling()}</td><td>{@link #isForcingCrawling} / {@link #isVisuallySwimming}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link #isFallFlying}（1.12.2 原生 isElytraFlying + Pose）</td></tr>
 *   <tr><td>{@code player.getDimensions(Pose.STANDING).height}</td><td>{@link #getSize}</td></tr>
 *   <tr><td>{@code player.getPose()}</td><td>{@link #getPose}</td></tr>
 *   <tr><td>{@code player.setSwimming(b)}</td><td>{@link #setSwimming}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link #isInWaterOrBubble}（降级为 isInWater）</td></tr>
 * </table>
 */
public final class ParCoolPlatform {

    private ParCoolPlatform() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // 基础转换
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 唯一的强转入口。约定：全工程只有这一处出现 {@code (IPlayerResizeable)}。
     */
    public static IPlayerResizeable resizeable(EntityPlayer player) {
        return (IPlayerResizeable) player;
    }

    @SideOnly(Side.CLIENT)
    public static IPlayerSPSwimming spSwimming(EntityPlayerSP player) {
        return (IPlayerSPSwimming) player;
    }

    /**
     * 玩家是否已经具备 Aqua 的姿态能力。
     *
     * <p>正常情况下永远是 true。但 Aqua 的部分字段是在 {@code <init>} 的 RETURN 处才初始化的，
     * 而 {@code EntityPlayer} 在专用服务端启动阶段可能被提前构造 —— 那时 Aqua 的 mixin 已经生效
     * （接口在类上），但 {@code POSE} 这个 DataParameter 可能还没注册完。用这个判断兜住。</p>
     */
    public static boolean isReady(EntityPlayer player) {
        if (player == null) return false;
        if (!(player instanceof IPlayerResizeable)) return false;
        try {
            // getPose() 读的是 Aqua 的 DataParameter，未初始化时会抛异常
            return resizeable(player).getPose() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Pose / 尺寸
    // ────────────────────────────────────────────────────────────────────────

    public static Pose getPose(EntityPlayer player) {
        return resizeable(player).getPose();
    }

    public static void setPose(EntityPlayer player, Pose pose) {
        resizeable(player).setPose(pose);
    }

    public static EntitySize getSize(EntityPlayer player, Pose pose) {
        return resizeable(player).getSize(pose);
    }

    /** 对应 1.16 的 {@code player.getDimensions(player.getPose()).height}。 */
    public static float getHeight(EntityPlayer player) {
        return resizeable(player).getHeight();
    }

    /** 对应 1.16 的 {@code player.getDimensions(player.getPose()).width}。 */
    public static float getWidth(EntityPlayer player) {
        return resizeable(player).getWidth();
    }

    /** 该姿态在当前空间下是否放得下（1.16 里由引擎隐式处理，1.12.2 要自己问）。 */
    public static boolean isPoseClear(EntityPlayer player, Pose pose) {
        return resizeable(player).isPoseClear(pose);
    }

    // ────────────────────────────────────────────────────────────────────────
    // 游泳 / 爬行 / 潜行
    // ────────────────────────────────────────────────────────────────────────

    public static boolean isSwimming(EntityPlayer player) {
        return resizeable(player).isSwimming();
    }

    public static void setSwimming(EntityPlayer player, boolean flag) {
        resizeable(player).setSwimming(flag);
    }

    /** 「真的在游」：包含姿态判定，比 {@link #isSwimming} 更严格。 */
    public static boolean isActuallySwimming(EntityPlayer player) {
        return resizeable(player).isActuallySwimming();
    }

    /** 泳姿动画插值系数（0~1），Aqua 的模型动画也在用同一个值。 */
    public static float getSwimAnimation(EntityPlayer player, float partialTicks) {
        return resizeable(player).getSwimAnimation(partialTicks);
    }

    public static boolean canSwim(EntityPlayer player) {
        return resizeable(player).canSwim();
    }

    public static boolean canForceCrawling(EntityPlayer player) {
        return resizeable(player).canForceCrawling();
    }

    public static boolean isForcingCrawling(EntityPlayer player) {
        return resizeable(player).isForcingCrawling();
    }

    /**
     * 对应 1.16 的 {@code player.isVisuallyCrawling()}。
     * 注意：Aqua 侧需要客户端标记，服务端调用会得到 false。
     */
    public static void setForcingCrawling(EntityPlayer player, boolean flag) {
        resizeable(player).setForcingCrawling(flag);
    }

    /** 真实潜行状态。⚠️ Aqua 把 {@code isSneaking()} 与这个方法的语义对调了，判断潜行必须用这个。 */
    public static boolean isActuallySneaking(EntityPlayer player) {
        return resizeable(player).isActuallySneaking();
    }

    /** 是否允许改变碰撞箱（有别的 mod 干扰尺寸时为 false）。 */
    public static boolean isResizingAllowed(EntityPlayer player) {
        return resizeable(player).isResizingAllowed();
    }

    @SideOnly(Side.CLIENT)
    public static boolean isVisuallySwimming(EntityPlayer player) {
        if (!(player instanceof IPlayerResizeable)) return false;
        return resizeable(player).isVisuallySwimming();
    }

    @SideOnly(Side.CLIENT)
    public static boolean getEyesInWaterPlayer(EntityPlayer player) {
        return resizeable(player).getEyesInWaterPlayer();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 1.13+ 语义的适配（1.12.2 没有对应方法，这里给出等价实现）
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 对应 1.16 的 {@code player.isFallFlying()}。
     * 1.12.2 原生只有 {@code isElytraFlying()}，这里额外并入 Aqua 的姿态判定。
     */
    public static boolean isFallFlying(EntityPlayer player) {
        if (player.isElytraFlying()) return true;
        return isReady(player) && getPose(player) == Pose.FALL_FLYING;
    }

    /**
     * 对应 1.16 的 {@code player.isInWaterOrBubble()}。
     * 「气泡柱」是 1.13 概念，Aqua 的 {@code bubbleColumns} 默认关闭，所以降级为 {@code isInWater()}。
     */
    public static boolean isInWaterOrBubble(EntityPlayer player) {
        return player.isInWater();
    }

    /**
     * 对应 1.16 的 {@code player.isInLava()}（1.12.2 同名，仅为统一收口而保留）。
     */
    public static boolean isInLava(EntityPlayer player) {
        return player.isInLava();
    }
}
