package com.alrex.parcool.common.action;

import com.alrex.parcool.common.action.impl.BreakfallReady;
import com.alrex.parcool.common.action.impl.CatLeap;
import com.alrex.parcool.common.action.impl.ChargeJump;
import com.alrex.parcool.common.action.impl.ClimbPoles;
import com.alrex.parcool.common.action.impl.ClimbUp;
import com.alrex.parcool.common.action.impl.ClingToCliff;
import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.FastRun;
import com.alrex.parcool.common.action.impl.FastSwim;
import com.alrex.parcool.common.action.impl.Flipping;
import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.action.impl.HideInBlock;
import com.alrex.parcool.common.action.impl.HorizontalWallRun;
import com.alrex.parcool.common.action.impl.JumpFromBar;
import com.alrex.parcool.common.action.impl.QuickTurn;
import com.alrex.parcool.common.action.impl.RideZipline;
import com.alrex.parcool.common.action.impl.Roll;
import com.alrex.parcool.common.action.impl.SkyDive;
import com.alrex.parcool.common.action.impl.Slide;
import com.alrex.parcool.common.action.impl.Tap;
import com.alrex.parcool.common.action.impl.Vault;
import com.alrex.parcool.common.action.impl.VerticalWallRun;
import com.alrex.parcool.common.action.impl.WallJump;
import com.alrex.parcool.common.action.impl.WallSlide;
import com.alrex.parcool.common.action.instant.StartSwimByCrawl;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 全部动作的注册表（1.12.2 版）。
 *
 * <p>结构 1:1 照搬上游 {@code ActionList} —— {@link ActionRegistry} 携带
 * 「类 / 工厂 / 默认体力消耗」三项，并派生出 {@code ACTIONS}（类列表）与
 * {@code NAMES}（simpleName 列表）。</p>
 *
 * <h3>★ 顺序与默认体力消耗逐项照抄上游</h3>
 * <p>数组顺序决定动作索引，而索引用于网络同步与配置的 per-action 数组。
 * <b>保持与上游一致</b>，将来对照两边代码时「这个动作是几号」不用查表。</p>
 *
 * <h3>★ 为什么 27 个动作一次性全部注册，而其中大多数还是骨架</h3>
 * <p>因为动作之间<b>互相引用</b> —— {@code Crawl.canStart} 要查
 * {@code Roll}/{@code Tap}/{@code ClingToCliff}/{@code Dive}/{@code Vault}/{@code HideInBlock}/
 * {@code RideZipline} 的 {@code isDoing()}。</p>
 * <p>「只注册一部分」会带来两个问题：</p>
 * <ul>
 *   <li>引用方找不到类 → 编译不过</li>
 *   <li>或者索引与上游错位 → 网络/配置的编号对不上</li>
 * </ul>
 * <p>所以一次性全注册，未移植的用 {@link StubAction} 占位（{@code canStart} 恒 false）。
 * 这些占位<b>不会干扰真实动作</b>：对它们的 {@code isDoing()} 查询恒为 false，
 * 而那些查询在真实动作里几乎都是 {@code !xxx.isDoing()} 形式。</p>
 * <p>上游自己也用这个模式 —— {@code ClimbPoles}（「爬柱子」）在 1.16.5 里就从未实现，
 * 是个三方法全返回 {@code false}/{@code None} 的空壳。</p>
 *
 * <p><b>移植某个动作时</b>：只改那一个类文件（{@code extends StubAction} → {@code extends Action}
 * 并补实现），<b>本文件不需要动</b>。</p>
 */
public class ActionList {

    public static final List<ActionRegistry<? extends Action>> ACTION_REGISTRIES = Arrays.asList(
            // ── 顺序与默认体力消耗照抄上游，勿随意调整 ────────────────────────
            new ActionRegistry<>(BreakfallReady.class, BreakfallReady::new, 0),
            new ActionRegistry<>(CatLeap.class, CatLeap::new, 200),
            new ActionRegistry<>(ChargeJump.class, ChargeJump::new, 100),
            new ActionRegistry<>(ClimbPoles.class, ClimbPoles::new, 0),
            new ActionRegistry<>(ClimbUp.class, ClimbUp::new, 150),
            new ActionRegistry<>(ClingToCliff.class, ClingToCliff::new, 2),
            new ActionRegistry<>(HideInBlock.class, HideInBlock::new, 0),
            new ActionRegistry<>(Crawl.class, Crawl::new, 0),
            new ActionRegistry<>(Dive.class, Dive::new, 0),
            new ActionRegistry<>(Dodge.class, Dodge::new, 80),
            new ActionRegistry<>(FastRun.class, FastRun::new, 2),
            new ActionRegistry<>(FastSwim.class, FastSwim::new, 3),
            new ActionRegistry<>(Flipping.class, Flipping::new, 80),
            new ActionRegistry<>(HangDown.class, HangDown::new, 3),
            new ActionRegistry<>(HorizontalWallRun.class, HorizontalWallRun::new, 2),
            new ActionRegistry<>(JumpFromBar.class, JumpFromBar::new, 100),
            new ActionRegistry<>(QuickTurn.class, QuickTurn::new, 0),
            new ActionRegistry<>(RideZipline.class, RideZipline::new, 2),
            new ActionRegistry<>(Roll.class, Roll::new, 100),
            new ActionRegistry<>(SkyDive.class, SkyDive::new, 0),
            new ActionRegistry<>(Slide.class, Slide::new, 0),
            new ActionRegistry<>(Tap.class, Tap::new, 100),
            new ActionRegistry<>(Vault.class, Vault::new, 50),
            new ActionRegistry<>(VerticalWallRun.class, VerticalWallRun::new, 150),
            new ActionRegistry<>(WallJump.class, WallJump::new, 120),
            new ActionRegistry<>(WallSlide.class, WallSlide::new, 8),

            new ActionRegistry<>(StartSwimByCrawl.class, StartSwimByCrawl::new, 0)
    );

    private static final HashMap<Class<? extends Action>, Short> INDEX_MAP;
    private static final TreeMap<String, Short> NAME_2_INDEX_MAP;

    public static final List<Class<? extends Action>> ACTIONS
            = ACTION_REGISTRIES.stream().map(ActionRegistry::getClassInstance).collect(Collectors.toList());

    public static final List<String> NAMES = ACTIONS.stream().map(Class::getSimpleName).collect(Collectors.toList());

    static {
        INDEX_MAP = new HashMap<>((int) (ACTIONS.size() * 1.5) + 1);
        NAME_2_INDEX_MAP = new TreeMap<>();
        for (Class<? extends Action> action : ACTIONS) {
            short index = (short) ACTIONS.indexOf(action);
            INDEX_MAP.put(action, index);
            NAME_2_INDEX_MAP.put(action.getSimpleName(), index);
        }
    }

    private ActionList() {
    }

    public static short getIndexOf(Class<? extends Action> action) {
        return INDEX_MAP.getOrDefault(action, (short) -1);
    }

    @Nullable
    public static Class<? extends Action> getByIndex(int index) {
        if (index < 0 || index >= ACTIONS.size()) return null;
        return ACTIONS.get(index);
    }

    @Nullable
    public static Class<? extends Action> getByName(String name) {
        Short index = NAME_2_INDEX_MAP.get(name);
        if (index == null) return null;
        return ACTIONS.get(index);
    }

    /** 每个玩家一份动作实例（由 {@code Parkourability} 构造时调用）。 */
    public static List<Action> constructActionsList() {
        List<Action> list = new ArrayList<>(ACTION_REGISTRIES.size());
        for (ActionRegistry<? extends Action> registry : ACTION_REGISTRIES) {
            list.add(registry.createInstance());
        }
        return list;
    }

    public static int getSize() {
        return ACTION_REGISTRIES.size();
    }

    public static class ActionRegistry<T extends Action> {
        private final Class<T> classInstance;
        private final Supplier<T> factory;
        private final int defaultStaminaConsumption;

        public ActionRegistry(Class<T> action, Supplier<T> constructor, int defaultStaminaConsumption) {
            this.classInstance = action;
            this.factory = constructor;
            this.defaultStaminaConsumption = defaultStaminaConsumption;
        }

        public Class<T> getClassInstance() {
            return classInstance;
        }

        public T createInstance() {
            return factory.get();
        }

        public int getDefaultStaminaConsumption() {
            return defaultStaminaConsumption;
        }
    }
}
