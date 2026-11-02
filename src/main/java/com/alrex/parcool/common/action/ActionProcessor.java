package com.alrex.parcool.common.action;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import com.alrex.parcool.common.capability.Animation;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.common.info.ClientSetting;
import com.alrex.parcool.common.network.SyncActionStateMessage;
import com.alrex.parcool.common.network.SyncStaminaMessage;
import com.alrex.parcool.common.network.SyncStaminaToClientMessage;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.BufferUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * 动作系统的每 tick 驱动（1.12.2 版，1:1 移植）。
 *
 * <p>这是整个动作系统的心脏。每 tick 对每个玩家做：</p>
 * <ol>
 *   <li>体力 tick</li>
 *   <li>客户端：动画 tick</li>
 *   <li>「本地玩家」检查限制是否已同步（没同步就请求，最多试 5 次）</li>
 *   <li>更新衍生计时器（{@code AdditionalProperties}）</li>
 *   <li>逐个动作：抛事件 → {@code tick} → 分端回调 → 「能否继续 / 能否开始」判定
 *       → 成功则扣体力并记进同步包</li>
 *   <li>「本地玩家」把攒下的动作变化与体力发出去</li>
 *   <li>服务端：驱动 {@code SyncStaminaToClientMessage.tick()}（合并发送）</li>
 * </ol>
 *
 * <h3>1.12.2 替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code event.side == LogicalSide.CLIENT}</td><td>{@code event.side == Side.CLIENT}</td></tr>
 *   <tr><td>{@code @OnlyIn(Dist.CLIENT)}</td><td>{@code @SideOnly(Side.CLIENT)}</td></tr>
 *   <tr><td>{@code player.tickCount}</td><td>{@code player.ticksExisted}</td></tr>
 *   <tr><td>{@code player.displayClientMessage(c, false)}</td><td>{@code player.sendStatusMessage(c, false)}</td></tr>
 *   <tr><td>{@code new TranslationTextComponent(key)}</td><td>{@code new TextComponentTranslation(key)}</td></tr>
 *   <tr><td>{@code .withStyle(TextFormatting.X)}</td>
 *       <td>{@code .setStyle(new Style().setColor(TextFormatting.X))}</td></tr>
 *   <tr><td>{@code Minecraft.getInstance().player} / {@code clientPlayer.level.players()}</td>
 *       <td>{@code Minecraft.getMinecraft().player} / {@code clientPlayer.world.playerEntities}</td></tr>
 *   <tr><td>{@code ClientPlayerEntity} / {@code AbstractClientPlayerEntity}</td>
 *       <td>{@code EntityPlayerSP} / {@code AbstractClientPlayer}</td></tr>
 * </table>
 *
 * <p>事件钩子用<b>实例方法</b>（上游如此），所以注册时需要一个实例 ——
 * 见 {@link #INSTANCE}。</p>
 */
public class ActionProcessor {

    /** 供事件总线注册用（{@code EventBusForgeRegistry}）。 */
    public static final ActionProcessor INSTANCE = new ActionProcessor();

    private final ByteBuffer bufferOfPostState = ByteBuffer.allocate(128);
    private final ByteBuffer bufferOfPreState = ByteBuffer.allocate(128);
    private final ByteBuffer bufferOfStarting = ByteBuffer.allocate(128);
    private int staminaSyncCoolTimeTick = 0;

    private ActionProcessor() {
    }

    private static boolean parCool$isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static boolean parCool$isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    /** 诊断：本地玩家的坐标/速度/朝向一旦出现 NaN 或 ∞ 就打印现场（最多 5 次）。定位后删 ✓ */
    private static int parCool$nanDiagCount = 0;

    public static void parCool$checkFinite(EntityPlayer player, Parkourability parkourability) {
        if (parCool$nanDiagCount >= 5) return;
        // ★★ 服务端的 isMovePlayerPacketInvalid 有【两个】判非法条件 ✓：
        //    ① 坐标/朝向非有限值（NaN / ∞）✗
        //    ② **坐标绝对值 > 3.0E7（3000 万）** ✗
        //    第一版诊断只查了 ① ✗ → 实测「诊断计数 = 0 但仍然被踢」✗
        //    → 说明命中的是 ② ✓ 现在两个都查 ✓
        final double LIMIT = 3.0E7D;
        boolean nonFinite =
                !parCool$isFinite(player.posX) || !parCool$isFinite(player.posY) || !parCool$isFinite(player.posZ)
                        || !parCool$isFinite(player.motionX) || !parCool$isFinite(player.motionY) || !parCool$isFinite(player.motionZ)
                        || !parCool$isFinite(player.rotationYaw) || !parCool$isFinite(player.rotationPitch);
        boolean tooFar =
                Math.abs(player.posX) > LIMIT || Math.abs(player.posY) > LIMIT || Math.abs(player.posZ) > LIMIT;
        if (!nonFinite && !tooFar) return;
        parCool$nanDiagCount++;
        StringBuilder active = new StringBuilder();
        for (Action a : parkourability.getList()) {
            if (a.isDoing()) active.append(a.getClass().getSimpleName()).append(' ');
        }
        System.out.println("[ParCool/DIAG] INVALID MOVE PACKET STATE #" + parCool$nanDiagCount
                + (nonFinite ? " [NON-FINITE]" : " [COORD>3e7]")
                + " pos=(" + player.posX + ", " + player.posY + ", " + player.posZ + ")"
                + " motion=(" + player.motionX + ", " + player.motionY + ", " + player.motionZ + ")"
                + " yaw=" + player.rotationYaw + " pitch=" + player.rotationPitch
                + " | elytraFlying=" + player.isElytraFlying()
                + " | onGround=" + player.onGround
                + " | activeActions=[" + active.toString().trim() + "]");
    }

    /**
     * 修「**鞘翅飞行时视角正对上下 → 坐标变成 NaN → 被服务端踢出**」✗
     *
     * <h3>★ 症状（实测 2026-10-04）</h3>
     * <p>用鞘翅向上飞（或向下俯冲）时，被踢出并提示
     * 「收到了包含非法玩家移动的数据包」✗
     * （服务端的 {@code isMovePlayerPacketInvalid} 一旦发现坐标/朝向非有限值就立刻踢 ✗）</p>
     *
     * <h3>★ 病因：1.12.2 鞘翅飞行代码里的一条<b>无守卫除法</b></h3>
     * <pre>
     * double d6 = Math.sqrt(vec3d.x*vec3d.x + vec3d.z*vec3d.z);   // 视线方向的【水平分量】
     * ...
     * if (f &lt; 0.0F) {                                             // f = pitch，向上飞时为负
     *     double d10 = d8 * (-MathHelper.sin(f)) * 0.04D;
     *     this.motionX -= vec3d.x * d10 / d6;                      // ★ 这里没有 d6 &gt; 0 守卫 ✗
     *     this.motionZ -= vec3d.z * d10 / d6;
     * }
     * </pre>
     * <p><b>仰望正上方（pitch = -90°）时，视线的水平分量恰好为 0 ✗</b>
     * → {@code d6 = 0} → {@code 0.0 / 0.0} = <b>NaN</b> ✗
     * → {@code motionX/motionZ} 变 NaN → 坐标变 NaN → <b>被踢</b> ✗</p>
     * <p>（上面那条 {@code if (this.motionY < 0.0D && d6 > 0.0D)} 分支<b>是有</b>守卫的，
     * 唯独这一条漏了 ✓ —— 典型的原版疏漏 ✗）</p>
     *
     * <h3>★ 诊断证据（2026-10-04 早先那次，已抓到现场）</h3>
     * <pre>
     * pos=(NaN, 17.20, NaN)  motion=(0.0, -0.078, 0.0)
     * pitch=-90.0  elytraFlying=true  activeActions=[]      ← ParCool 完全没参与 ✓
     * </pre>
     *
     * <h3>✅ 修法：不让 pitch 精确等于 ±90°</h3>
     * <p>把 pitch 夹到 ±89.999° —— 视觉上<b>完全不可察觉</b>（0.001° ✓），
     * 但能让 {@code d6} 保持 &gt; 0，从根上避开那条除法 ✓</p>
     * <p><b>⭐ 为什么选「夹 pitch」而不是改那段除法</b>：
     * 1.12.2 的 {@code EntityPlayer#travel} 被 <b>Aqua Acrobatics 整体重写并 cancel</b> ✗ ——
     * 实际跑的可能是 Aqua 那份 ✗，注入点不好挑 ✓；
     * 而「夹 pitch」<b>与代码来源无关</b>，无论谁实现 travel 都有效 ✓</p>
     *
     * <p><b>⚠️ 这是修「原版/上游的 bug」</b>，不是 ParCool 逻辑 ✗ ——
     * 单独成方法，注释齐全，将来要移除很容易 ✓</p>
     */
    private static void parCool$fixElytraNanPitch(EntityPlayer player) {
        // ① 兜底恢复：若位置**已经是 NaN**（说明上一 tick 已经被除零污染 ✗），
        //    用上一 tick 的有效坐标把它拉回来 ✓
        //    ⚠️ 必须做：一旦 posX/posZ 变 NaN，光靠"夹 pitch"是救不回来的 ✗
        //    （NaN 会一直传播 —— NaN + 任何数 = NaN ✗）
        if (!parCool$isFinite(player.posX) || !parCool$isFinite(player.posZ)) {
            double x = parCool$isFinite(player.lastTickPosX) ? player.lastTickPosX : 0.0D;
            double z = parCool$isFinite(player.lastTickPosZ) ? player.lastTickPosZ : 0.0D;
            double y = parCool$isFinite(player.posY) ? player.posY : 0.0D;
            player.setPosition(x, y, z);
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            return;
        }

        // ② 预防：鞘翅飞行时不让 pitch 恰好 ±90°（见方法上方长注释 ✓）
        if (!player.isElytraFlying()) return;
        float pitch = player.rotationPitch;
        if (pitch <= -89.999f) {
            player.rotationPitch = -89.999f;
        } else if (pitch >= 89.999f) {
            player.rotationPitch = 89.999f;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        // ★★ 鞘翅 NaN 修正**必须在 START 阶段做** ✗→✓
        //
        // 🚨 实测教训（2026-10-04 22:31）：第一版把它放在 END ✗ → **完全无效** ✗
        //   因为 END 时本 tick 的 `travel` 已经跑完（NaN 已产生 ✗），
        //   客户端的位置包也已经发出去了 ✗
        //   → 表现：「修复还是不起作用」✗
        //   而 START 在 `onLivingUpdate`/`travel` **之前** ✓ → 能真正拦住 ✓
        //
        // ⚠️ 注意：本方法原来在 START 直接 return ✗ —— 所以这里要与修正合并 ✓
        if (event.phase == TickEvent.Phase.START) {
            parCool$fixElytraNanPitch(event.player);
            return;
        }

        EntityPlayer player = event.player;
        IStamina stamina = IStamina.get(player);
        if (stamina == null) return;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        boolean inClient = event.side == Side.CLIENT;
        boolean inServer = !inClient;

        // ── 前置 ────────────────────────────────────────────────────────────
        stamina.tick();
        if (inClient) {
            onTick$doPreprocessInClient(event, parkourability);
        }

        List<Action> actions = parkourability.getList();
        boolean needSync = ParCool.PROXY.isLocalPlayer(player);
        SyncActionStateMessage.Encoder builder = SyncActionStateMessage.Encoder.reset();

        // ── 诊断：抓「玩家状态变成非有限值」的瞬间（鞘翅向上飞被踢，定位后删）──
        //    服务端的 isMovePlayerPacketInvalid() 只要发现坐标或朝向是 NaN/∞ 就立刻踢人 ✓
        //    这里每 tick 检查本地玩家，抓到现场就把【当时在跑的动作】一并打出来 ✓
        if (needSync) {
            parCool$checkFinite(player, parkourability);
            parCool$fixElytraNanPitch(player);
        }
        // ── 诊断结束 ──

        if (needSync) {
            onTick$checkLimitationSynchronization(player, parkourability);
        }

        parkourability.getAdditionalProperties().onTick(player, parkourability);

        for (Action action : actions) {
            MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Tick.Pre(player, action));
            processAction(player, parkourability, stamina, builder, inClient, action);
            MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Tick.Post(player, action));
        }

        if (needSync) {
            onTick$sendSynchronizationPacket(player, parkourability, stamina, builder);
            if (stamina.isImposingExhaustionPenalty()
                    && parkourability.getClientInfo().get(ParCoolConfig.Client.Booleans.EnableStaminaExhaustionPenalty)) {
                player.setSprinting(false);
            }
        }

        // ── BehaviorEnforcer 的「应用层」：集中在这一处做等价操作 ✓ ──────────────
        //
        // 上游用 5 个 mixin 来消费这些标记（见移植方案 §4.3）。
        // 但本工程的做法是「**在动作自己的 tick 里直接做**」——更轻、少一个 mixin 失败面 ✓
        //
        // ⚠️ 必须放在动作循环【之后】✗→✓：标记是在各动作的 onStart* 里登记进去的，
        //    循环前读只能读到「上一 tick 的登记」✗
        BehaviorEnforcer enforcer = parkourability.getBehaviorEnforcer();

        // ★ 屏蔽潜行 —— 登记点 3 处：ClingToCliff（挂墙时不缩身子）/
        //   HangDown（挂杆）/ HideInBlock（藏身）
        //   上游对应 mixin：ClientPlayerEntityMixin 注入 isShiftKeyDown ✓
        //   ⚠️ 本工程【不注入 isSneaking】✗ —— Aqua 把 isSneaking/isActuallySneaking 语义对调了
        //      （见 §4.3），注入会打架 ✗ → 改为直接设状态 ✓
        if (enforcer.cancelSneak()) {
            player.setSneaking(false);
        }

        // ★ 屏蔽鞘翅 —— 登记点 2 处：WallJump（跳离墙后别变成滑翔）/ ClingToCliff
        //   上游对应 mixin：PlayerEntityMixin 注入 tryToStartFallFlying ✓
        //   ⚠️ 1.12.2 **没有** tryToStartFallFlying ✗ ——
        //   它的起飞是「客户端在 EntityPlayerSP:1065 发 START_FALL_FLYING 包 → 服务端 setFlag(7,true)」，
        //   没有单一可注入的方法 ✗
        //   → 改为把 flag 压回 false ✓ 但 **setFlag 是 Entity 的 protected** ✗，
        //     这里调不到 → 放到 EntityPlayerMixin 里做 ✓（见那个文件）

        // ⚠️ 未实现的两项（1.12.2 无对应行为，已核实）：
        //   · cancelDescendFromEdge（上游 isStayingOnGroundSurface）
        //     —— 1.12.2 **没有**「潜行不走下台阶」这个特性（1.14+ 才引入 ✗）
        //        唯一的登记点（Dodge 的 CanGetOffStepsWhileDodge=false）在本版本无可屏蔽的对象 ✓
        //   · cancelSprint / setMarkerEnforceMovePoint
        //     —— 全工程【无人登记】✓ 故无需应用层 ✓

        if (inServer) {
            SyncStaminaToClientMessage.tick();
        }
    }

    @SideOnly(Side.CLIENT)
    private void onTick$doPreprocessInClient(TickEvent.PlayerTickEvent event, Parkourability parkourability) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.player;
        Animation animation = Animation.get(player);
        if (animation == null) return;
        animation.tick(player, parkourability);
    }

    /**
     * 限制还没同步时反复请求（最多 5 次，之后报错并停止 —— 与上游一致）。
     * 这是「客户端设置 → 服务端」链路失效时的自愈机制。
     *
     * <h3>🚨 检查周期从 256 tick 改成 20 tick（实测问题）</h3>
     * <p>上游写的是 {@code ticksExisted % 256 == 0} ✗ ——
     * 意味着<b>最坏要等 12.8 秒</b>才会发起第一次重试 ✗</p>
     * <p><b>实测（2026-10-04 21:38 日志）</b>：掉出世界重生后 ——</p>
     * <pre>
     * 21:38:52  服务端主动下发（PlayerEvent.Clone）→ 客户端已同步 ✓
     * 21:39:04  ✗ not_synced —— 客户端此时【又重建了一次能力实例】✗
     *           （日志里这 12 秒内没有任何 setServerLimitation ✗
     *             → 不是"被替换"，是"新实例的默认值是 UNSYNCED"✓）
     * </pre>
     * <p>根因：<b>客户端玩家的重建发生在我们那次下发【之后】</b> ✗ ——
     * 于是刚同步好的实例又被换掉了 ✓
     * → 而下一个检查点在 256 tick 之后 → <b>十几秒功能失效</b> ✗</p>
     * <p><b>修法</b>：把检查周期压到 20 tick（1 秒 / 次 ✓）——
     * 代价可忽略（{@code limitationIsNotSynced()} 只是读一个布尔 ✗），
     * 但重生后能在 1 秒内自动恢复 ✓
     * 失败重试上限仍是 5 次 ✓ 且成功时会归零 ✓（见 {@code limitationIsNotSynced}）</p>
     */
    @SideOnly(Side.CLIENT)
    private void onTick$checkLimitationSynchronization(EntityPlayer player, Parkourability parkourability) {
        if (!ParCool.PROXY.isLocalPlayer(player)) return;
        if (!(player instanceof EntityPlayerSP)) return;
        if (!parkourability.limitationIsNotSynced()) return;

        int trialCount = parkourability.getSynchronizeTrialCount();
        // ★ 首次【立刻】尝试 ✓，之后每 20 tick 重试一次（最多 5 次 ✓）
        //   —— 重生后能在【1 秒内】恢复，而不是等十几秒 ✗
        //   （trialCount == 0 = 本轮首次；成功会归零 ✓ 见 limitationIsNotSynced）
        //   前 5 tick 不动作：给 join 时的正常同步留出到达时间 ✓
        if (player.ticksExisted <= 5) return;
        if (trialCount != 0 && player.ticksExisted % 20 != 0) return;

        if (trialCount < 5) {
            parkourability.trySyncLimitation((EntityPlayerSP) player);
            // ★★ 只在【重试】时才提示用户（trialCount > 0 ✓）
            //
            // 为什么：首次尝试（trialCount == 0）几乎总是**同一个 tick 内就成功** ✓
            //   —— 典型场景就是「重生后客户端重建能力」✗
            //   实测（2026-10-04 21:49 日志）：死亡 → 重建 → 首次请求 → 成功，
            //   **全部发生在同一秒** ✓ 却仍弹了 not_synced ✗ → 用户以为是错误 ✗
            // 只有首次失败、开始重试时，才说明链路真的有问题 → 那时提示才有意义 ✓
            if (trialCount > 0 && ParCoolConfig.Client.Booleans.ShowAutoResynchronizationNotification.get()) {
                player.sendStatusMessage(
                        new TextComponentTranslation("parcool.message.error.limitation.not_synced"), false);
            }
            // 同理：首次尝试用 INFO，重试才用 WARN ✓（避免每次重生都刷一条 WARN ✗）
            if (trialCount > 0) {
                ParCool.LOGGER.log(Level.WARN, "ParCool Limitation still not synced, retrying... (attempt " + (trialCount + 1) + "/5)");
            } else {
                ParCool.LOGGER.log(Level.INFO, "ParCool Limitation is not synced (usually right after respawn). Requesting...");
            }
        } else if (trialCount == 5) {
            parkourability.incrementSynchronizeTrialCount();
            player.sendStatusMessage(
                    new TextComponentTranslation("parcool.message.error.limitation.fail_sync")
                            .setStyle(new Style().setColor(TextFormatting.DARK_RED)),
                    false);
            ParCool.LOGGER.log(Level.ERROR, "Failed to synchronize ParCool Limitation. "
                    + "There may be problems about server connection. "
                    + "Please report to the developer after checking connection");
        }
    }

    @SideOnly(Side.CLIENT)
    private void onTick$sendSynchronizationPacket(EntityPlayer player, Parkourability parkourability,
                                                 IStamina stamina, SyncActionStateMessage.Encoder builder) {
        if (!builder.isEmpty()) {
            SyncActionStateMessage.sync(player, builder);
        }

        staminaSyncCoolTimeTick++;
        // 限制还没同步时不要发体力 —— 否则服务端会用错误的设置处理
        if (!parkourability.limitationIsNotSynced() && (staminaSyncCoolTimeTick > 5 || stamina.wantToConsumeOnServer())) {
            staminaSyncCoolTimeTick = 0;
            SyncStaminaMessage.sync(player);
        }
    }

    private void processAction(EntityPlayer player, Parkourability parkourability, IStamina stamina,
                               SyncActionStateMessage.Encoder builder, boolean inClientSide, Action action) {
        boolean needSync = ParCool.PROXY.isLocalPlayer(player);

        if (needSync) {
            saveSynchronizationState(action, bufferOfPreState);
        }
        action.tick();

        action.onTick(player, parkourability, stamina);
        if (inClientSide) {
            action.onClientTick(player, parkourability, stamina);
        } else {
            action.onServerTick(player, parkourability, stamina);
        }

        if (needSync) {
            checkAndChangeActionState(player, parkourability, stamina, action, builder);
        }

        if (action.isDoing()) {
            action.onWorkingTick(player, parkourability, stamina);
            if (inClientSide) {
                action.onWorkingTickInClient(player, parkourability, stamina);
                if (needSync) {
                    action.onWorkingTickInLocalClient(player, parkourability, stamina);
                    if (action.getStaminaConsumeTiming() == StaminaConsumeTiming.OnWorking) {
                        stamina.consume(parkourability.getActionInfo().getStaminaConsumptionOf(action.getClass()));
                    }
                } else {
                    action.onWorkingTickInOtherClient(player, parkourability, stamina);
                }
            } else {
                action.onWorkingTickInServer(player, parkourability, stamina);
            }
        }

        if (needSync) {
            saveSynchronizationState(action, bufferOfPostState);

            // 只有状态真的变了才发 —— 这是这个包不刷屏的关键
            if (!BufferUtil.haveSameContents(bufferOfPreState, bufferOfPostState)) {
                bufferOfPostState.rewind();
                builder.appendSyncData(parkourability, action, bufferOfPostState);
                bufferOfPreState.clear();
                bufferOfPostState.clear();
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void checkAndChangeActionState(EntityPlayer player, Parkourability parkourability, IStamina stamina,
                                          Action action, SyncActionStateMessage.Encoder builder) {
        // ★ 本地玩家的 ClientSetting 自愈
        //
        // ActionInfo.can() = ParCool.isActive() && ClientSetting.getPossibilityOf(action)
        //                    && ServerLimitation.isPermitted(action)
        // 而 ClientSetting 的初值是 UNSYNCED_INSTANCE，其 getPossibilityOf() 恒返回 false
        // → 只要没被初始化过，**所有动作的 can() 都会恒为 false，按键完全没反应**。
        //
        // 上游在 PlayerJoinHandler 里做这件事（进服时设一次）。1.12.2 这里多加一道保险：
        // 每 tick 比对一次引用（O(1)），既能覆盖「进世界」，也能覆盖「重生/capability 被重建」
        // —— 因为 Parkourability 是挂在实体上的，实体重建时它跟着重建。
        // 放在本方法里还顺带满足项目约定：客户端专属逻辑只出现在 @SideOnly(CLIENT) 的方法中。
        if (parkourability.getClientInfo() == ClientSetting.UNSYNCED_INSTANCE) {
            parkourability.getActionInfo().setClientSetting(ClientSetting.readFromLocalConfig());
        }

        if (action.isDoing()) {
            boolean canContinue = parkourability.getActionInfo().can(action.getClass())
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToContinueEvent(player, action))
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToContinue(player, action))
                    && action.canContinue(player, parkourability, stamina);
            if (!canContinue) {
                MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Pre(player, action));
                action.finish(player);
                MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.StopEvent(player, action));
                MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Finish.Post(player, action));
                builder.appendFinishMsg(parkourability, action);
            }
        } else {
            bufferOfStarting.clear();
            boolean start = !player.isSpectator()
                    && parkourability.getActionInfo().can(action.getClass())
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStartEvent(player, action))
                    && !MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.TryToStart(player, action))
                    && action.canStart(player, parkourability, stamina, bufferOfStarting);
            bufferOfStarting.flip();
            if (start) {
                MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Start.Pre(player, action));
                action.start(player, parkourability, bufferOfStarting, stamina);
                MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.StartEvent(player, action));
                MinecraftForge.EVENT_BUS.post(new ParCoolActionEvent.Start.Post(player, action));
                if (action.getStaminaConsumeTiming() == StaminaConsumeTiming.OnStart) {
                    stamina.consume(parkourability.getActionInfo().getStaminaConsumptionOf(action.getClass()));
                }
                builder.appendStartData(parkourability, action, bufferOfStarting);
            }
        }
    }

    private void saveSynchronizationState(Action action, ByteBuffer buffer) {
        buffer.clear();
        action.saveSynchronizedState(buffer);
        buffer.flip();
    }

    // ── 渲染帧驱动 ──────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        EntityPlayerSP clientPlayer = Minecraft.getMinecraft().player;
        if (clientPlayer == null) return;
        for (EntityPlayer player : clientPlayer.world.playerEntities) {
            Parkourability parkourability = Parkourability.get(player);
            if (parkourability == null) continue;
            for (Action action : parkourability.getList()) {
                action.onRenderTick(event, player, parkourability);
            }
            Animation animation = Animation.get(player);
            if (animation == null) continue;
            animation.onRenderTick(event, player, parkourability);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onViewRender(EntityViewRenderEvent.CameraSetup event) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return;
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;
        Animation animation = Animation.get(player);
        if (animation == null) return;
        animation.cameraSetup(event, player, parkourability);
    }
}
