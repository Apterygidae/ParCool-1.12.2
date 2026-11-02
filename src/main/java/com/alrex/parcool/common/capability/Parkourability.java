package com.alrex.parcool.common.capability;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.ActionList;
import com.alrex.parcool.common.action.AdditionalProperties;
import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.alrex.parcool.common.capability.capabilities.Capabilities;
import com.alrex.parcool.common.info.ActionInfo;
import com.alrex.parcool.common.info.ClientSetting;
import com.alrex.parcool.common.info.ServerLimitation;
import com.alrex.parcool.common.network.SyncClientInformationMessage;
import com.alrex.parcool.config.ParCoolConfig;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;

/**
 * 每个玩家一份的「跑酷状态」（1.12.2 版，1:1 移植）。
 *
 * <p>它持有：26 个动作实例（{@code ActionList.constructActionsList()}）、
 * 衍生计时器（{@code AdditionalProperties}）、行为强制器（{@code BehaviorEnforcer}）、
 * 以及「客户端设置 × 服务端限制」的合成视图（{@code ActionInfo}）。</p>
 *
 * <p>1.12.2 差异：{@code LazyOptional.orElse(null)} → {@code getCapability(...)} 直接返回或 null。</p>
 *
 * <p><b>TODO(P2-B)</b>：{@code trySyncLimitation} / {@code limitationIsNotSynced} 需要
 * {@code SyncClientInformationMessage}，网络包接入后补上（见 {@code NetworkHandler} 的编号表）。</p>
 */
public class Parkourability {

    @Nullable
    public static Parkourability get(EntityPlayer player) {
        return player.getCapability(Capabilities.PARKOURABILITY_CAPABILITY, null);
    }

    private final ActionInfo info;
    private final AdditionalProperties properties = new AdditionalProperties();
    private final BehaviorEnforcer enforcer = new BehaviorEnforcer();
    private final List<Action> actions = ActionList.constructActionsList();
    private final HashMap<Class<? extends Action>, Action> actionsMap;
    private int synchronizeTrialCount = 0;

    public Parkourability() {
        actionsMap = new HashMap<>((int) (actions.size() * 1.5) + 1);
        for (Action action : actions) {
            actionsMap.put(action.getClass(), action);
        }
        info = new ActionInfo();
    }

    @SuppressWarnings("unchecked")
    public <T extends Action> T get(Class<T> action) {
        T value = (T) actionsMap.get(action);
        if (value == null) {
            throw new IllegalArgumentException("The Action instance is not registered: " + action.getSimpleName());
        }
        return value;
    }

    public short getActionID(Action instance) {
        return ActionList.getIndexOf(instance.getClass());
    }

    @Nullable
    public Action getActionFromID(short id) {
        if (0 <= id && id < actions.size()) {
            return actions.get(id);
        }
        return null;
    }

    public AdditionalProperties getAdditionalProperties() {
        return properties;
    }

    public BehaviorEnforcer getBehaviorEnforcer() {
        return enforcer;
    }

    public ActionInfo getActionInfo() {
        return info;
    }

    public ClientSetting getClientInfo() {
        return info.getClientSetting();
    }

    public ServerLimitation getServerLimitation() {
        return info.getServerLimitation();
    }

    public List<Action> getList() {
        return actions;
    }

    /** 玩家换维度/重生时把两侧同步信息复制过去（上游同一套做法）。 */
    public void CopyFrom(Parkourability original) {
        getActionInfo().setClientSetting(original.getActionInfo().getClientSetting());
        getActionInfo().setServerLimitation(original.getActionInfo().getServerLimitation());
    }

    public boolean isDoingNothing() {
        for (Action action : actions) {
            if (action.isDoing()) return false;
        }
        return true;
    }

    /**
     * 布尔项取两侧的合成值。
     * {@code AdvantageousValue} 决定「偏向玩家」还是「偏向限制」。
     */
    public boolean getLimitedValue(ParCoolConfig.Client.Booleans client, ParCoolConfig.Server.Booleans server) {
        if (server.AdvantageousValue) {
            return getClientInfo().get(client) && getServerLimitation().get(server);
        } else {
            return !(getClientInfo().get(client) || getServerLimitation().get(server));
        }
    }

    public int getLimitedValue(ParCoolConfig.Client.Integers client, ParCoolConfig.Server.Integers server) {
        if (server.Advantageous == ParCoolConfig.AdvantageousDirection.Higher) {
            return Math.min(getClientInfo().get(client), getServerLimitation().get(server));
        } else {
            return Math.max(getClientInfo().get(client), getServerLimitation().get(server));
        }
    }

    public double getLimitedValue(ParCoolConfig.Client.Doubles client, ParCoolConfig.Server.Doubles server) {
        if (server.Advantageous == ParCoolConfig.AdvantageousDirection.Higher) {
            return Math.min(getClientInfo().get(client), getServerLimitation().get(server));
        } else {
            return Math.max(getClientInfo().get(client), getServerLimitation().get(server));
        }
    }

    /** 请求与服务端重新同步限制（用于「本地设置改了」或「同步过期」）。 */
    @SideOnly(Side.CLIENT)
    public void trySyncLimitation(EntityPlayerSP player) {
        synchronizeTrialCount++;
        // P2-B 补齐：真的发出同步请求 ✓
        //
        // ⚠️⚠️ 此前这里是空实现 ✗（只累加计数 ✗）→ 引发实测 bug：
        //   玩家死亡重生后，capability 的状态被从 original 拷贝过来 ✗，
        //   若拷贝过去的是「未同步」的 limitation ✗ → 所有跑酷功能失效 ✗，
        //   而唯一的自愈路径（这个方法）又什么都不做 ✗
        //   → 重试满 5 次后彻底放弃 → 聊天框一直提示 not_synced ✗
        SyncClientInformationMessage.sync(player, true);
    }

    @SideOnly(Side.CLIENT)
    public int getSynchronizeTrialCount() {
        return synchronizeTrialCount;
    }

    public void incrementSynchronizeTrialCount() {
        synchronizeTrialCount++;
    }

    /**
     * 限制是否未同步。
     *
     * <p><b>★ 顺带重置重试计数</b>：一旦同步成功就把计数归零 ✓ ——
     * 否则「曾经失同步 → 重试满 5 次 ✗」之后，<b>将来再次失同步就永久没有自愈机会了</b> ✗
     * （死亡重生正是「再次失同步」的典型场景 ✗）</p>
     */
    @SideOnly(Side.CLIENT)
    public boolean limitationIsNotSynced() {
        boolean notSynced = !getServerLimitation().isSynced();
        if (!notSynced) {
            synchronizeTrialCount = 0;
        }
        return notSynced;
    }

    @SafeVarargs
    public final Boolean isDoingAny(Class<? extends Action>... actions) {
        for (Class<? extends Action> action : actions) {
            if (get(action).isDoing()) {
                return true;
            }
        }
        return false;
    }

    /** 便捷入口：不担心 null 的取法。 */
    public static boolean isDoing(EntityPlayer player, Class<? extends Action> action) {
        Parkourability parkourability = get(player);
        return parkourability != null && parkourability.get(action).isDoing();
    }

    /** 供自检打印用。 */
    public static String describe() {
        return "actions=" + ActionList.getSize() + ", modActive=" + ParCool.isActive();
    }
}
