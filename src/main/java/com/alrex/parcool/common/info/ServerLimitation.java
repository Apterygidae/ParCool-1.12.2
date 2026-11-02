package com.alrex.parcool.common.info;

import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.ActionList;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.server.limitation.Limitation;
import com.alrex.parcool.server.limitation.Limitations;
import net.minecraft.entity.player.EntityPlayerMP;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;

/**
 * 「服务端限制」的可同步快照（1.12.2 版）。
 *
 * <p>与上游同构：{@code Default}（未同步占位）、{@code Remote}（真实值），
 * 以及 {@code writeTo}/{@code readFrom} 的字节格式 —— 协议层可直接复用。</p>
 *
 * <h3>★ 上游语义（本类现已完全对齐，此前是占位实现）</h3>
 * <p>{@code ServerLimitation} <b>不是</b>「服务端配置的镜像」，而是
 * 「<b>被管理员实际施加的限制</b>」的结果。默认（{@code limitation_imposed=false}）
 * 返回的是<b>最有利于玩家</b>的值，服务端配置里的 {@code Server.*}
 * <b>一条都不生效</b>。要生效必须：① 打开 {@code limitation_imposed}，
 * 或 ② 用 {@code /parcool limitation ...} 逐项施加。这不是 bug，是上游的设计 ——
 * 见 {@code Limitation.readFromServerConfig()} 里 {@code enabled = LimitationEnabled.get()} 那一行。</p>
 * <ul>
 *   <li>{@code Remote()} 的初值取「最有利」：布尔取 {@code AdvantageousValue}，
 *       数值按 {@code Advantageous} 取 {@code Max} / {@code Min}（<b>不是 DefaultValue</b>）</li>
 *   <li>{@code apply(limitation)} 只会把值<b>收紧</b>（布尔取与、数值取更不利的一侧）</li>
 *   <li>{@code Default}（未同步）反过来取<b>最不利</b>：布尔取 {@code !AdvantageousValue}，
 *       数值取 {@code Max}/{@code Min} 的另一头 + 动作全禁 → 同步到达前动作一律不可用</li>
 * </ul>
 */
public abstract class ServerLimitation {

    private static class Default extends ServerLimitation {
        @Override
        public boolean isPermitted(Class<? extends Action> action) {
            return false;
        }

        @Override
        public int getStaminaConsumptionOf(Class<? extends Action> action) {
            return Integer.MAX_VALUE;
        }

        @Override
        public Boolean get(ParCoolConfig.Server.Booleans item) {
            return !item.AdvantageousValue;
        }

        @Override
        public Integer get(ParCoolConfig.Server.Integers item) {
            return item.Advantageous == ParCoolConfig.AdvantageousDirection.Lower ? item.Max : item.Min;
        }

        @Override
        public Double get(ParCoolConfig.Server.Doubles item) {
            return item.Advantageous == ParCoolConfig.AdvantageousDirection.Lower ? item.Max : item.Min;
        }

        @Override
        public boolean isSynced() {
            return false;
        }
    }

    private static class Remote extends ServerLimitation {
        private final boolean[] actionPossibilities = new boolean[ActionList.ACTIONS.size()];
        private final int[] leastStaminaConsumptions = new int[ActionList.ACTIONS.size()];
        private final EnumMap<ParCoolConfig.Server.Booleans, Boolean> booleans = new EnumMap<>(ParCoolConfig.Server.Booleans.class);
        private final EnumMap<ParCoolConfig.Server.Integers, Integer> integers = new EnumMap<>(ParCoolConfig.Server.Integers.class);
        private final EnumMap<ParCoolConfig.Server.Doubles, Double> doubles = new EnumMap<>(ParCoolConfig.Server.Doubles.class);

        Remote() {
            Arrays.fill(actionPossibilities, true);
            Arrays.fill(leastStaminaConsumptions, 0);
            for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
                booleans.put(item, item.AdvantageousValue);
            }
            for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
                integers.put(item, item.Advantageous == ParCoolConfig.AdvantageousDirection.Higher ? item.Max : item.Min);
            }
            for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
                doubles.put(item, item.Advantageous == ParCoolConfig.AdvantageousDirection.Higher ? item.Max : item.Min);
            }
        }

        @Override
        public boolean isPermitted(Class<? extends Action> action) {
            short index = ActionList.getIndexOf(action);
            return index < 0 || actionPossibilities[index];
        }

        @Override
        public int getStaminaConsumptionOf(Class<? extends Action> action) {
            short index = ActionList.getIndexOf(action);
            return index < 0 ? 0 : leastStaminaConsumptions[index];
        }

        @Override
        public Boolean get(ParCoolConfig.Server.Booleans item) {
            return booleans.get(item);
        }

        @Override
        public Integer get(ParCoolConfig.Server.Integers item) {
            return integers.get(item);
        }

        @Override
        public Double get(ParCoolConfig.Server.Doubles item) {
            return doubles.get(item);
        }

        @Override
        public boolean isSynced() {
            return true;
        }

        /** 叠加一份限制 —— 只会把值收紧，永远比当前更不利。 */
        void apply(Limitation limitation) {
            for (int i = 0; i < ActionList.ACTIONS.size(); i++) {
                if (this.actionPossibilities[i]) {
                    this.actionPossibilities[i] = limitation.isPermitted(ActionList.ACTIONS.get(i));
                }
                this.leastStaminaConsumptions[i] = Math.max(
                        this.leastStaminaConsumptions[i],
                        limitation.getLeastStaminaConsumption(ActionList.ACTIONS.get(i))
                );
            }
            for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
                if (this.booleans.get(item) == item.AdvantageousValue) {
                    this.booleans.put(item, limitation.get(item));
                }
            }
            for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
                this.integers.put(
                        item,
                        item.Advantageous == ParCoolConfig.AdvantageousDirection.Higher ?
                                Math.min(limitation.get(item), this.integers.get(item)) :
                                Math.max(limitation.get(item), this.integers.get(item))
                );
            }
            for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
                this.doubles.put(
                        item,
                        item.Advantageous == ParCoolConfig.AdvantageousDirection.Higher ?
                                Math.min(limitation.get(item), this.doubles.get(item)) :
                                Math.max(limitation.get(item), this.doubles.get(item))
                );
            }
        }
    }

    /** 未同步时的占位实例：一切从紧。 */
    public static final ServerLimitation UNSYNCED_INSTANCE = new Default();

    public abstract boolean isPermitted(Class<? extends Action> action);

    public abstract int getStaminaConsumptionOf(Class<? extends Action> action);

    public abstract Boolean get(ParCoolConfig.Server.Booleans item);

    public abstract Integer get(ParCoolConfig.Server.Integers item);

    public abstract Double get(ParCoolConfig.Server.Doubles item);

    /** 是否已同步（客户端据此决定要不要继续请求同步）。 */
    public abstract boolean isSynced();

    /**
     * 由<b>服务端</b>计算该玩家最终生效的限制：全局限制（若启用）+ 该玩家名下的各条限制。
     *
     * <p>这是上游 {@code ServerLimitation.get(ServerPlayerEntity)} 的 1.12.2 版。
     * 会读 {@code Limitations} 的缓存（必要时从磁盘 load）→
     * <b>调用前必须确保 {@code Limitations.init(...)} 已执行</b>（服务端启动时）。</p>
     */
    public static ServerLimitation get(EntityPlayerMP player) {
        Collection<Limitation> limitations = Limitations.getLimitationsOf(player.getUniqueID());

        Remote instance = new Remote();
        if (Limitations.getGlobalLimitation().isEnabled()) {
            instance.apply(Limitations.getGlobalLimitation());
        }
        for (Limitation limitation : limitations) {
            if (limitation.isEnabled()) {
                instance.apply(limitation);
            }
        }
        return instance;
    }

    public void writeTo(ByteBuffer buffer) {
        for (Class<? extends Action> action : ActionList.ACTIONS) {
            buffer.put((byte) (isPermitted(action) ? 1 : 0));
            buffer.putInt(getStaminaConsumptionOf(action));
        }
        for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
            buffer.put((byte) (get(item) ? 1 : 0));
        }
        for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
            buffer.putInt(get(item));
        }
        for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
            buffer.putDouble(get(item));
        }
    }

    public static ServerLimitation readFrom(ByteBuffer buffer) {
        Remote instance = new Remote();
        for (int i = 0; i < instance.actionPossibilities.length; i++) {
            instance.actionPossibilities[i] = buffer.get() != 0;
            instance.leastStaminaConsumptions[i] = buffer.getInt();
        }
        for (ParCoolConfig.Server.Booleans item : ParCoolConfig.Server.Booleans.values()) {
            instance.booleans.put(item, item.readFromBuffer(buffer));
        }
        for (ParCoolConfig.Server.Integers item : ParCoolConfig.Server.Integers.values()) {
            instance.integers.put(item, item.readFromBuffer(buffer));
        }
        for (ParCoolConfig.Server.Doubles item : ParCoolConfig.Server.Doubles.values()) {
            instance.doubles.put(item, item.readFromBuffer(buffer));
        }
        return instance;
    }
}
