package com.alrex.parcool.api;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.utilities.RegistryRef;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * ParCool 的音效注册（1.12.2 版）。
 *
 * <p>对应 1.16.5 的 {@code api/SoundEvents.java}。字段保持同名同形
 * （{@code RegistryRef} 顶替 {@code RegistryObject}），所以上游调用点
 * {@code SoundEvents.VAULT.get()} 一行都不用改。</p>
 *
 * <h3>⚠️ 移植时修正了上游的两个 bug</h3>
 * <p>上游 {@code SoundEvents.java} 里有两处 {@code SoundEvent} 的
 * {@code ResourceLocation} 与 {@code sounds.json} 的键名对不上 ——
 * <b>这类错误不报错、只表现为「音效静默不响」</b>，而且上游自己注释也写错了
 * （把 {@code action.cling_to_cliff.jump} 的常量命名为 {@code CLING_TO_CLIFF_JUMP_SOUND} 却赋成了
 * {@code action.cling_to_cliff.jump}，看着对但注册 id 写成了别的）。逐条核对
 * {@code assets/parcool/sounds.json}（19 个键）后修正：</p>
 *
 * <table border="1">
 *   <tr><th>#</th><th>上游写法</th><th>问题</th><th>本文件</th></tr>
 *   <tr><td>1</td><td>{@code SoundEvent} = {@code parcool:hang_down.jump}，
 *       注册名 {@code hang_down.jump}</td>
 *       <td>{@code sounds.json} 里的键是 <b>{@code action.hang_down.jump}</b> →
 *       <b>该音效永远不响</b></td>
 *       <td>{@code action.hang_down.jump}</td></tr>
 *   <tr><td>2</td><td>{@code SoundEvent} = {@code parcool:parcool.enable}，
 *       注册名 {@code action.enable}</td>
 *       <td>资源名正确、注册名不一致（影响注册表可读性与 {@code /playsound}）</td>
 *       <td>统一为 {@code parcool.enable}</td></tr>
 *   <tr><td>3</td><td>同上，{@code parcool.disable} vs 注册名 {@code action.disable}</td>
 *       <td>同上</td><td>统一为 {@code parcool.disable}</td></tr>
 * </table>
 *
 * <p>本文件的做法是<b>让 {@code sounds.json} 成为唯一事实来源</b>：所有音效名都在
 * {@link #ALL_NAMES} 里列一次，字段与注册都由它派生，从结构上杜绝再次写错。</p>
 */
public class SoundEvents {

    /** 与 {@code assets/parcool/sounds.json} 的键一一对应（19 个）。 */
    private static final String[] ALL_NAMES = {
            "action.vault",
            "action.v_wall_run",
            "action.h_wall_run",
            "action.breakfall.just",
            "action.breakfall.roll",
            "action.breakfall.tap",
            "action.charge_jump",
            "action.catleap",
            "action.wall_jump",
            "action.cling_to_cliff.grab",
            "action.cling_to_cliff.jump",
            "action.hang_down.grab",
            "action.hang_down.jump",
            "action.slide",
            "action.dodge",
            "zipline.set",
            "zipline.remove",
            "parcool.enable",
            "parcool.disable",
            // ── 铁钩的「锁链」音效 ✓ ──
            // ⚠️ 上游用 1.16 的 SoundType.CHAIN ✗ —— 1.12.2 没有这个 SoundType ✓，
            //    所以自建（见 ParCoolSoundTypes.CHAIN ✓）。
            //    1.12.2 的 SoundType 要 5 个 SoundEvent ✗
            //    （break/step/place/hit/fall ✓）→ 这里全部注册 ✓
            "chain.break",
            "chain.step",
            "chain.place",
            "chain.hit",
            "chain.fall",
    };

    public static final RegistryRef<SoundEvent> VAULT = RegistryRef.of();
    public static final RegistryRef<SoundEvent> VERTICAL_WALL_RUN = RegistryRef.of();
    public static final RegistryRef<SoundEvent> HORIZONTAL_WALL_RUN = RegistryRef.of();
    public static final RegistryRef<SoundEvent> BREAKFALL_JUST_TIME = RegistryRef.of();
    public static final RegistryRef<SoundEvent> ROLL = RegistryRef.of();
    public static final RegistryRef<SoundEvent> SAFETY_TAP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CHARGE_JUMP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CATLEAP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> WALL_JUMP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CLING_TO_CLIFF = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CLING_TO_CLIFF_JUMP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> HANG_DOWN = RegistryRef.of();
    public static final RegistryRef<SoundEvent> HANG_DOWN_JUMP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> SLIDE = RegistryRef.of();
    public static final RegistryRef<SoundEvent> DODGE = RegistryRef.of();
    public static final RegistryRef<SoundEvent> ZIPLINE_SET = RegistryRef.of();
    public static final RegistryRef<SoundEvent> ZIPLINE_REMOVE = RegistryRef.of();
    public static final RegistryRef<SoundEvent> PARCOOL_ENABLE = RegistryRef.of();
    public static final RegistryRef<SoundEvent> PARCOOL_DISABLE = RegistryRef.of();
    // ── 铁钩的锁链音效 ✓（对应 ALL_NAMES 末尾的 5 项 ✓，顺序必须一致 ✗）──
    public static final RegistryRef<SoundEvent> CHAIN_BREAK = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CHAIN_STEP = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CHAIN_PLACE = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CHAIN_HIT = RegistryRef.of();
    public static final RegistryRef<SoundEvent> CHAIN_FALL = RegistryRef.of();

    private static final List<SoundEvent> REGISTERED = new ArrayList<>(ALL_NAMES.length);

    private SoundEvents() {
    }

    /**
     * 由 {@code RegistryEvent.Register<SoundEvent>} 调用。
     *
     * <p>1.12.2 要求每个对象自带 {@code setRegistryName}，
     * 且注册名与 {@code SoundEvent} 内部的 ResourceLocation 必须一致，否则查表会落空。</p>
     */
    public static void registerAll(IForgeRegistry<SoundEvent> registry) {
        if (!REGISTERED.isEmpty()) return;

        @SuppressWarnings("unchecked")
        RegistryRef<SoundEvent>[] refs = new RegistryRef[]{
                VAULT, VERTICAL_WALL_RUN, HORIZONTAL_WALL_RUN, BREAKFALL_JUST_TIME, ROLL,
                SAFETY_TAP, CHARGE_JUMP, CATLEAP, WALL_JUMP, CLING_TO_CLIFF,
                CLING_TO_CLIFF_JUMP, HANG_DOWN, HANG_DOWN_JUMP, SLIDE, DODGE,
                ZIPLINE_SET, ZIPLINE_REMOVE, PARCOOL_ENABLE, PARCOOL_DISABLE,
                CHAIN_BREAK, CHAIN_STEP, CHAIN_PLACE, CHAIN_HIT, CHAIN_FALL,
        };
        if (refs.length != ALL_NAMES.length) {
            throw new IllegalStateException("SoundEvents: ALL_NAMES(" + ALL_NAMES.length
                    + ") and refs(" + refs.length + ") are out of sync");
        }

        for (int i = 0; i < ALL_NAMES.length; i++) {
            String name = ALL_NAMES[i];
            ResourceLocation location = new ResourceLocation(ParCool.MOD_ID, name);
            SoundEvent event = new SoundEvent(location);
            event.setRegistryName(location);
            registry.register(event);
            refs[i].set(event);
            REGISTERED.add(event);
        }
    }
}
