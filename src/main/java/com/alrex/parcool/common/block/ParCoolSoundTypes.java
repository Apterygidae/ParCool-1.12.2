package com.alrex.parcool.common.block;

import com.alrex.parcool.api.SoundEvents;
import net.minecraft.block.SoundType;

/**
 * 本 mod 自建的方块音效组 ✓ —— 1.12.2 版。
 *
 * <h3>★ 为什么需要这个类</h3>
 * <p>上游铁钩用的是 <b>1.16 的 {@code SoundType.CHAIN}</b> ✗（锁链音）✓，
 * 但 <b>1.12.2 没有 {@code CHAIN}</b> ✗（原版链子方块是 1.16 才加的 ✓）——
 * 可用的只有 {@code WOOD/GROUND/PLANT/STONE/METAL/GLASS/CLOTH/SAND/SNOW/
 * LADDER/ANVIL/SLIME} 共 12 种 ✓。
 * 直接用 {@code METAL} 听感不对 ✗，所以**自建一个** ✓。</p>
 *
 * <h3>★ 1.12.2 的 {@code SoundType} 构造需要 5 个 SoundEvent ✗</h3>
 * <p>{@code (float volume, float pitch, SoundEvent breakSound, stepSound,
 * placeSound, hitSound, fallSound)} ✓ —— <b>比 1.16 多一个 {@code fallSound}</b> ✗。</p>
 *
 * <h3>🚨 关键：必须在<b>运行时</b>构造，不能在建方块时构造 ✗</h3>
 * <p>{@code SoundType} 要的是 **{@code SoundEvent} 实例** ✗，
 * 而它**只有注册之后才有值** ✗（本工程的 {@code SoundEvents} 用
 * {@link SoundEvents#registerAll} 在 {@code RegistryEvent.Register<SoundEvent>} 时填充 ✓）。</p>
 *
 * <p><b>⚠️⚠️ 我原先的假设是错的 ✗</b>（实测崩溃 2026-10-11 11:46）：<br>
 * 我以为「Forge 保证 {@code Register<SoundEvent>} 早于 {@code Register<Block>}」✗ ——
 * <b>实际上 Forge 对各个 {@code RegistryEvent} 的先后<b>没有任何保证</b></b> ✗，
 * 方块注册时 SoundEvent 可能还没填 ✓。当时的堆栈：</p>
 * <pre>
 * IllegalStateException: RegistryRef has not been populated yet.
 *     at ParCoolSoundTypes.chain
 *     at IronZiplineHookBlock.&lt;init&gt;
 *     at Blocks.registerAll → RegistryHandler.onRegisterBlocks
 * </pre>
 *
 * <p><b>✅ 正解：不在构造期取，改用运行时的 {@code Block#getSoundType(...)} 覆写 ✓</b>
 * —— 那时两类注册都早已完成 ✓。见 {@code IronZiplineHookBlock} 里带惰性缓存的实现 ✓。</p>
 *
 * <p>本类因此**只保留工厂方法** ✓，不持有任何静态 SoundType 字段 ✓。</p>
 */
public class ParCoolSoundTypes {

    /**
     * 「锁链」音效组 ✓ —— 用于铁制钩子。
     *
     * <p><b>⚠️ 每次调用都新建一个 {@code SoundType} 对象</b> ✗ ——
     * 可接受 ✓：方块只在注册时构造一次 ✓（不像 {@code getSoundType} 那样每帧调 ✓）。
     * 这样写是为了避开「静态字段 + 注册时序」的坑 ✓。</p>
     *
     * <p>音量/音调取 {@code 1.0 / 1.0} ✓ —— 与 1.16 的 {@code SoundType.CHAIN} 默认一致 ✓。</p>
     */
    public static SoundType chain() {
        return new SoundType(1.0F, 1.0F,
                SoundEvents.CHAIN_BREAK.get(),
                SoundEvents.CHAIN_STEP.get(),
                SoundEvents.CHAIN_PLACE.get(),
                SoundEvents.CHAIN_HIT.get(),
                SoundEvents.CHAIN_FALL.get());
    }

    private ParCoolSoundTypes() {
    }
}
