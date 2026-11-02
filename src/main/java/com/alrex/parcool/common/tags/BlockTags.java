package com.alrex.parcool.common.tags;

import com.alrex.parcool.ParCool;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import java.util.HashSet;
import java.util.Set;

/**
 * 方块「标签」的 1.12.2 替代方案。
 *
 * <h3>⚠️ 为什么需要这个类</h3>
 * <p>上游用 <b>1.13+ 的方形标签系统</b>（{@code data/<modid>/tags/blocks/*.json}）来表达
 * 「哪些方块能藏进去 / 能爬」：</p>
 * <pre>
 * // 1.16.5
 * block.getTags().contains(BlockTags.HIDE_ABLE)
 * </pre>
 * <p><b>1.12.2 完全没有这套东西</b>（1.13 才引入）—— 这是本项目里唯一一处
 * 「不是换个名字、而是整块概念缺失」的地方 ✗ 必须<b>重新设计</b> ✓</p>
 *
 * <h3>★★ 1.12.2 的等价物：{@code OreDictionary} + 内置默认集合</h3>
 * <p>Forge 1.12.2 的 {@code OreDictionary} 就是「标签」在当时的形态 ——
 * 其它 mod 可以 {@code OreDictionary.registerOre("parcoolPoleClimbable", block)} 注册进来，
 * 我们这边查 {@code getOres} 即可 ✓ <b>零新依赖</b> ✓</p>
 *
 * <h3>★ 一个关键事实：上游的 {@code pole_climbable} 标签<b>是空的</b></h3>
 * <p>核实结果（2026-10-01）：上游只提供了 {@code hide_able.json}
 * （内容是 {@code hay_block} + {@code #minecraft:leaves}），
 * <b>{@code pole_climbable.json} 根本不存在</b> ✗</p>
 * <p>也就是说这个标签是纯粹的<b>「逃生口」</b> —— 留给整合包作者/mod 往里面加方块 ✓
 * 「爬杆」的主路径完全不依赖它，而是靠<b>方块类型判定</b>
 * （见 {@code LivingEntityMixin#isCustomLadder}）✓</p>
 * <p>所以本类的 {@link #isPoleClimbable} 默认返回 {@code false}，
 * 只有当别的 mod 主动注册了才会为真 ✓ <b>与上游行为一致</b> ✓</p>
 *
 * <h3>初始化时机</h3>
 * <p>{@code OreDictionary} 的条目由各 mod 在自己的 init 里注册 → 所以本类<b>在 postInit 收集</b>
 * （见 {@code CommonProxy}）。收集前查询会得到空集合，只走类型判定那条路 ✓ 不会报错 ✓</p>
 */
public final class BlockTags {

    /** 能爬的「杆」的额外方块（对应上游的 {@code pole_climbable} 标签）。 */
    public static final String ORE_POLE_CLIMBABLE = "parcoolPoleClimbable";

    /** 可以「藏进去」的额外方块（对应上游的 {@code hide_able} 标签）。 */
    public static final String ORE_HIDE_ABLE = "parcoolHideAble";

    private static final Set<Block> POLE_CLIMBABLE_EXTRA = new HashSet<>();
    private static final Set<Block> HIDE_ABLE_EXTRA = new HashSet<>();

    private BlockTags() {
    }

    /** 由 {@code CommonProxy.postInit()} 调用 —— 此时各 mod 的 OreDictionary 条目都已注册 ✓ */
    public static void init() {
        POLE_CLIMBABLE_EXTRA.clear();
        HIDE_ABLE_EXTRA.clear();

        collect(ORE_POLE_CLIMBABLE, POLE_CLIMBABLE_EXTRA);
        collect(ORE_HIDE_ABLE, HIDE_ABLE_EXTRA);

        if (!POLE_CLIMBABLE_EXTRA.isEmpty()) {
            ParCool.LOGGER.info("[ParCool] Pole-climbable extra blocks from OreDictionary '{}': {} entries",
                    ORE_POLE_CLIMBABLE, POLE_CLIMBABLE_EXTRA.size());
        }
        if (!HIDE_ABLE_EXTRA.isEmpty()) {
            ParCool.LOGGER.info("[ParCool] Hide-able extra blocks from OreDictionary '{}': {} entries",
                    ORE_HIDE_ABLE, HIDE_ABLE_EXTRA.size());
        }
    }

    private static void collect(String oreName, Set<Block> target) {
        for (ItemStack stack : OreDictionary.getOres(oreName, false)) {
            if (stack.isEmpty()) continue;
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block == null || block == Blocks.AIR) continue;
            target.add(block);
        }
    }

    /**
     * 该方块是否被「额外注册」为可爬的杆（对应上游的 {@code pole_climbable} 标签）。
     *
     * <p>⚠️ 上游那个标签<b>没提供 json</b>，所以默认恒 false ✓</p>
     */
    public static boolean isPoleClimbable(Block block) {
        return POLE_CLIMBABLE_EXTRA.contains(block);
    }

    /**
     * 该方块是否能「藏进去」（对应上游的 {@code hide_able} 标签）。
     *
     * <h3>★ 内置默认集合（照抄上游 json 的内容）</h3>
     * <p>上游 {@code data/parcool/tags/blocks/hide_able.json} 的内容是：</p>
     * <pre>
     * { "values": [ "minecraft:hay_block", "#minecraft:leaves" ] }
     * </pre>
     * <p>1.12.2 没有标签系统 → 这里用「等价代码」表达：</p>
     * <ul>
     *   <li>{@code minecraft:hay_block} → {@code block == Blocks.HAY_BLOCK} ✓</li>
     *   <li>{@code #minecraft:leaves} → <b>{@code block instanceof BlockLeaves}</b> ✓
     *       （1.12.2 里所有树叶都继承 {@code BlockLeaves}，一个类型判断就覆盖了「所有树叶」✓）</li>
     * </ul>
     * <p>再加上 {@code OreDictionary} 的额外注册（给 mod / 整合包用 ✓）✓</p>
     */
    public static boolean isHideAble(Block block) {
        if (block == Blocks.HAY_BLOCK) return true;
        if (block instanceof BlockLeaves) return true;
        return HIDE_ABLE_EXTRA.contains(block);
    }
}
