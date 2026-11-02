package com.alrex.parcool.common.action.impl;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.client.animation.impl.CrawlAnimator;
import com.alrex.parcool.client.input.KeyBindings;
import com.alrex.parcool.client.input.KeyRecorder;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.StaminaConsumeTiming;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ClientSetting;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.platform.ParCoolPlatform;
import com.fuzs.aquaacrobatics.entity.Pose;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteBuffer;

/**
 * Crawl —— 爬行（1.12.2 版，**第一个真正实现了的动作**）。
 *
 * <h3>★ 这个动作几乎全靠 Aqua Acrobatics 实现</h3>
 * <p>1.16 的爬行本质是「把玩家姿态设成 {@code Pose.SWIMMING}，然后由原版姿态系统
 * 负责压扁碰撞箱、降视线高度、改模型姿势」。<b>1.12.2 根本没有姿态系统</b> ——
 * 那正是 Aqua 补上的东西。所以本类：</p>
 * <ul>
 *   <li>{@code player.setPose(Pose.SWIMMING)} → {@link ParCoolPlatform#setPose}</li>
 *   <li>{@code player.isVisuallyCrawling()} → {@link ParCoolPlatform#isVisuallySwimming}（Aqua 的客户端判定）</li>
 *   <li>{@code player.getPose()} → {@link ParCoolPlatform#getPose}</li>
 * </ul>
 * <p><b>一行"自己实现爬行"的代码都没有。</b> 这正是「把 Aqua 当硬依赖、直接调它的 API」
 * 在具体动作上的收益。</p>
 *
 * <h3>1.12.2 的 API 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.getVehicle()}</td><td>{@code player.getRidingEntity()}</td></tr>
 *   <tr><td>{@code player.isInWaterOrBubble()}</td><td>{@link ParCoolPlatform#isInWaterOrBubble}</td></tr>
 *   <tr><td>{@code player.isFallFlying()}</td><td>{@link ParCoolPlatform#isFallFlying}</td></tr>
 *   <tr><td>{@code player.onClimbable()}</td><td>{@code player.isOnLadder()}</td></tr>
 *   <tr><td>{@code player.isOnGround()}</td><td>{@code player.onGround}</td></tr>
 *   <tr><td>{@code KeyBinding.isDown()}</td><td>{@code KeyBinding.isKeyDown()}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td><td>{@code ParCool.PROXY.isLocalPlayer(player)}</td></tr>
 *   <tr><td>{@code ParCoolConfig.Client.CrawlControl}（枚举配置项）</td>
 *       <td>{@code ParCoolConfig.Client.getCrawlControl()}（字符串存 + 解析，见该类注释）</td></tr>
 * </table>
 *
 * <h3>⚠️ 与 Dodge 的按键冲突</h3>
 * <p>上游 Crawl 与 Dodge 默认都绑 {@code R}。它的处理是
 * {@link #disambiguateCommands}：<b>只有蹲着时才让这个键触发爬行</b>，否则让给 Dodge。</p>
 *
 * <p><b>TODO(P5)</b>：上游在 {@code onWorkingTickInClient} 里挂 {@code CrawlAnimator}
 * （第三人称爬行的肢体动画）。动画器属 P5，暂不接 ——
 * <b>动作逻辑已完整可玩</b>，只是少了额外的肢体动画；Aqua 自身的姿态处理仍然生效。</p>
 */
public class Crawl extends Action {

    public enum ControlType {
        PressKey, Toggle
    }

    public boolean toggleStatus = false;

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
        Pose pose = ParCoolPlatform.getPose(player);
        return isActionInvoked()
                && disambiguateCommands(pose)
                && !parkourability.isDoingAny(Roll.class, Tap.class, ClingToCliff.class, Dive.class)
                && parkourability.get(Vault.class).getNotDoingTick() >= 8
                && !parkourability.get(HideInBlock.class).isDoing()
                && !parkourability.get(RideZipline.class).isDoing()
                && player.getRidingEntity() == null
                && (pose == Pose.STANDING || pose == Pose.CROUCHING)
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && !ParCoolPlatform.isFallFlying(player)
                && !player.isOnLadder()
                && (player.onGround || ParCoolConfig.Client.Booleans.EnableCrawlInAir.get());
    }

    @SideOnly(Side.CLIENT)
    private boolean isActionInvoked() {
        return (ParCoolConfig.Client.getCrawlControl() == ControlType.PressKey && KeyRecorder.keyCrawlState.isPressed())
                || (ParCoolConfig.Client.getCrawlControl() == ControlType.Toggle && toggleStatus);
    }

    /** 见类注释的「与 Dodge 的按键冲突」。 */
    @SideOnly(Side.CLIENT)
    private boolean disambiguateCommands(Pose pose) {
        return pose == Pose.CROUCHING || !KeyRecorder.keyDodge.isPressed();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        if (ParCool.PROXY.isLocalPlayer(player)) {
            if (ParCoolConfig.Client.getCrawlControl() == ControlType.Toggle) {
                if (KeyRecorder.keyCrawlState.isPressed()) {
                    toggleStatus = !toggleStatus;
                }
            } else {
                toggleStatus = false;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        // ⚠️ 这里<b>刻意不</b>看 Aqua 的 isVisuallySwimming()（上游看的是 1.16 原版的 isVisuallyCrawling）。
        //
        // 原因：Aqua 的 isVisuallySwimming() = isActuallySwimming() && !isInWater()，
        //       而本动作刚刚用 setForcingCrawling(true) 把玩家置成了 SWIMMING 姿态
        //       → 它会恒为 true → 旁路掉下面的按键检查 → 表现为「松开 C 也不起身」。
        //       （实测确认：这就是该 bug 的根因。）
        //
        // 上游那个旁路的本意是「玩家被地形困住 / 钻在低矮空间里时，松开按键也保持爬行」。
        // 在 1.12.2 这件事由 Aqua 自己负责 —— 它的 updatePose() 里有
        //     if (!noClip && !isRiding() && isResizingAllowed() && !isPoseClear(pose)) { ... pose1 = SWIMMING; }
        // 也就是「站不直就趴下」。所以去掉这个旁路<b>不会丢功能</b>。
        switch (ParCoolConfig.Client.getCrawlControl()) {
            case Toggle:
                if (!toggleStatus) return false;
                break;
            case PressKey:
                if (!KeyBindings.getKeyCrawl().isKeyDown()) return false;
                break;
            default:
                return false;
        }
        return !parkourability.get(Roll.class).isDoing()
                && !parkourability.get(Tap.class).isDoing()
                && !parkourability.get(ClingToCliff.class).isDoing()
                && !parkourability.get(Dive.class).isDoing()
                && parkourability.get(Vault.class).getNotDoingTick() >= 8
                && player.getRidingEntity() == null
                && !ParCoolPlatform.isInWaterOrBubble(player)
                && !ParCoolPlatform.isFallFlying(player)
                && !player.isOnLadder()
                && (player.onGround || ParCoolConfig.Client.Booleans.EnableCrawlInAir.get());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        Animation animation = Animation.get(player);
        if (animation != null && !animation.hasAnimator()) {
            animation.setAnimator(new CrawlAnimator());
        }
    }

    @Override
    public StaminaConsumeTiming getStaminaConsumeTiming() {
        return StaminaConsumeTiming.None;
    }

    /**
     * 真正的「爬行」——把姿态交给 Aqua。
     *
     * <h3>★ 为什么必须用 {@code setForcingCrawling} 而不是只 {@code setPose}</h3>
     * <p>Aqua 的 {@code EntityPlayerMixin} 在 {@code updateSize} 上做了
     * {@code @Inject(at = HEAD, cancellable = true)} 并 <b>cancel 掉原版实现</b>（{@code :391/:399}），
     * 也就是说 <b>pose 是 Aqua 每 tick 重新推导的派生状态</b>：</p>
     * <pre>
     * :419  } else if (this.isForcingCrawling() || this.isSwimming()) {
     *           pose = Pose.SWIMMING;
     * </pre>
     * <p>所以外部直接 {@code setPose(SWIMMING)} 会在下一 tick 被覆写回 {@code STANDING}
     * —— 实测表现就是「动作启动了（doing=true）但角色不趴下、碰撞箱不变」。</p>
     *
     * <p>Aqua 为此提供了 {@code setForcingCrawling(true)}：它置位 {@code TOGGLED_CRAWLING}
     * 数据管理器标志，让 {@code isForcingCrawling()} 恒真 → 上面的分支稳定命中
     * → <b>pose 被持续维持在 SWIMMING</b> ✓</p>
     *
     * <p>两个都调：{@code setPose} 让本 tick 立刻生效，{@code setForcingCrawling}
     * 让后续 tick 由 Aqua 维持。</p>
     *
     * <h3>⚠️ 它依赖 Aqua 的 {@code enableToggleCrawling = true}</h3>
     * <pre>
     * :181  public boolean canForceCrawling() {
     *           return ConfigHandler.MovementConfig.enableToggleCrawling && !isRiding() && ...
     *       }
     * :190  public void setForcingCrawling(boolean flag) {
     *           if (!canForceCrawling()) return;      // ← 被挡掉
     * </pre>
     * <p><b>所以 Aqua 的 {@code enableToggleCrawling} 必须保持 true</b>（它是 Aqua 给外部 mod 的
     * 「强制进入爬行」开关）。要解决的是它<b>默认按键与 ParCool 的 Crawl 撞车</b>这个问题
     * —— 把 Aqua 那个 toggle 的按键改绑或解绑，而<b>不要</b>关掉功能本身。</p>
     */
    @Override
    public void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
        player.setSprinting(false);
        ParCoolPlatform.setPose(player, Pose.SWIMMING);
        ParCoolPlatform.setForcingCrawling(player, true);
    }

    /** 动作结束时必须解除强制爬行，否则玩家会一直趴着。 */
    @Override
    public void onStop(EntityPlayer player) {
        ParCoolPlatform.setForcingCrawling(player, false);
    }
}
