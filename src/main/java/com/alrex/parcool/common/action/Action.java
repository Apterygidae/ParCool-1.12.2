package com.alrex.parcool.common.action;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * 所有跑酷动作的抽象基类（1.12.2 版，1:1 移植自上游 {@code Action}）。
 *
 * <p>它是一台状态机：{@code doing}/{@code doingTick}/{@code notDoingTick}/{@code tickFromStarted}，
 * 外加一整套「开始/结束/每 tick」的分端回调。**业务动作只覆写需要的钩子，其余留空。**</p>
 *
 * <h3>1.12.2 的四处改写</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code @OnlyIn(Dist.CLIENT)}</td><td>{@code @SideOnly(Side.CLIENT)}</td></tr>
 *   <tr><td>{@code PlayerEntity} / {@code ClientPlayerEntity}</td>
 *       <td>{@link EntityPlayer} / {@link EntityPlayerSP}</td></tr>
 *   <tr><td>{@code player.isLocalPlayer()}</td>
 *       <td>{@link ParCool#PROXY}{@code .isLocalPlayer(player)} —— 见 {@code CommonProxy} 的说明，
 *           不能用 {@code instanceof EntityPlayerSP}（双端类解析客户端类的风险）</td></tr>
 *   <tr><td>{@code player.level.isClientSide()}</td><td>{@code !player.world.isRemote}</td></tr>
 * </table>
 *
 * <p><b>关于 {@code @SideOnly} 加在抽象方法上</b>：这是 1.12.2 的标准写法。
 * FML 的 SideTransformer 在专用服务端上会把标注成员<b>整个删掉</b>——
 * 抽象方法与子类里对应的覆写一起消失，子类仍然是具体类，不会变成抽象类。</p>
 */
public abstract class Action {

    private boolean doing = false;
    private int doingTick = 0;
    private int notDoingTick = 0;
    private int tickFromStarted = -1;

    public boolean isJustStarted() {
        return isDoing() && getDoingTick() == 0;
    }

    public int getDoingTick() {
        return doingTick;
    }

    public int getNotDoingTick() {
        return notDoingTick;
    }

    public boolean isDoing() {
        return doing;
    }

    public void tick() {
        if (doing) {
            doingTick++;
            notDoingTick = 0;
        } else {
            notDoingTick++;
            doingTick = 0;
        }
        if (tickFromStarted >= 0) {
            tickFromStarted++;
        }
    }

    /**
     * 开始动作。
     *
     * @param startInfo 动作自定义的启动参数；调用前后都会 {@code rewind()}，
     *                  所以各端回调可以各自从头读一遍（上游的设计，照搬）
     */
    public void start(EntityPlayer player, Parkourability parkourability, ByteBuffer startInfo, @Nullable IStamina stamina) {
        doing = true;
        tickFromStarted = 0;
        onStart(player, parkourability, startInfo);
        startInfo.rewind();
        if (ParCool.PROXY.isLocalPlayer(player)) {
            if (stamina == null) {
                stamina = IStamina.get(player);
            }
            if (stamina != null) {
                onStartInLocalClient(player, parkourability, stamina, startInfo);
            }
        } else {
            if (!player.world.isRemote) {
                onStartInServer(player, parkourability, startInfo);
            } else {
                onStartInOtherClient(player, parkourability, startInfo);
            }
        }
        startInfo.rewind();
    }

    public void finish(EntityPlayer player) {
        doing = false;
        if (ParCool.PROXY.isLocalPlayer(player)) {
            onStopInLocalClient(player);
        } else {
            if (!player.world.isRemote) {
                onStopInServer(player);
            } else {
                onStopInOtherClient(player);
            }
        }
        onStop(player);
    }

    // ── 必须实现 ────────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    public abstract boolean canStart(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo);

    @SideOnly(Side.CLIENT)
    public abstract boolean canContinue(EntityPlayer player, Parkourability parkourability, IStamina stamina);

    public abstract StaminaConsumeTiming getStaminaConsumeTiming();

    // ── 生命周期回调（按需覆写） ────────────────────────────────────────────

    public void onStart(EntityPlayer player, Parkourability parkourability, ByteBuffer startInfo) {
    }

    public void onStartInServer(EntityPlayer player, Parkourability parkourability, ByteBuffer startInfo) {
    }

    @SideOnly(Side.CLIENT)
    public void onStartInOtherClient(EntityPlayer player, Parkourability parkourability, ByteBuffer startInfo) {
    }

    @SideOnly(Side.CLIENT)
    public void onStartInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina, ByteBuffer startInfo) {
    }

    public void onStop(EntityPlayer player) {
    }

    public void onStopInServer(EntityPlayer player) {
    }

    @SideOnly(Side.CLIENT)
    public void onStopInOtherClient(EntityPlayer player) {
    }

    @SideOnly(Side.CLIENT)
    public void onStopInLocalClient(EntityPlayer player) {
    }

    // ── 每 tick 回调 ────────────────────────────────────────────────────────

    public void onWorkingTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    public void onWorkingTickInServer(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    public void onWorkingTickInClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    public void onWorkingTickInOtherClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    public void onWorkingTickInLocalClient(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    public void onTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    public void onServerTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    public void onClientTick(EntityPlayer player, Parkourability parkourability, IStamina stamina) {
    }

    @SideOnly(Side.CLIENT)
    public void onRenderTick(TickEvent.RenderTickEvent event, EntityPlayer player, Parkourability parkourability) {
    }

    // ── 同步 ────────────────────────────────────────────────────────────────

    /** 从网络读回自己的状态（服务端 → 客户端同步动作细节时用）。 */
    public void restoreSynchronizedState(ByteBuffer buffer) {
    }

    /** 把自己的状态写进网络缓冲。 */
    public void saveSynchronizedState(ByteBuffer buffer) {
    }

    // ── HUD ─────────────────────────────────────────────────────────────────

    @SideOnly(Side.CLIENT)
    public boolean wantsToShowStatusBar(EntityPlayerSP player, Parkourability parkourability) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    public float getStatusValue(EntityPlayerSP player, Parkourability parkourability) {
        return 0;
    }
}
