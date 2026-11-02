package com.alrex.parcool.client;

import com.alrex.parcool.ParCool;
import com.alrex.parcool.common.block.Blocks;
import com.alrex.parcool.common.item.Items;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/**
 * 物品模型的**显式注册** ✓ —— 1.12.2 版。
 *
 * <h3>★ 为什么必须显式注册</h3>
 * <p>1.12.2 的 {@code ItemModelMesher} 有两张**注册表** ✗：
 * {@code simpleShapes}（物品 → 模型位置 ✓）与 {@code shapers}（自定义形态 ✓）。
 * {@code getItemModel(...)} 查不到时**最后落到 missing model（粉黑棋盘 ✗）** ✓</p>
 * <p>Forge 的 {@code ModelLoader.loadItemModels()} 会**自动**为每个物品尝试两种位置 ✓：</p>
 * <ol>
 *   <li>先试 {@code blockstates/<名字>.json} 的 <b>{@code inventory} 变体</b> ✗</li>
 *   <li>再回退 {@code models/item/<名字>.json} ✓</li>
 * </ol>
 * <p>两步都失败才会记 {@code ItemLoadingException} ✗。</p>
 *
 * <h3>🚨 本工程为什么还要显式注册</h3>
 * <p>实测（2026-10-06）：<b>方块在世界里已正常 ✗，但物品栏内仍是粉黑 ✗</b>，
 * 且日志中<b>没有任何模型加载错误</b> ✓ ——
 * 说明模型「加载成功但物品没绑定到它」✗。</p>
 * <p>显式 {@code setCustomModelResourceLocation} 能**直接写进
 * {@code simpleShapes} 表** ✓，绕开自动回退链条上的不确定因素 ✓
 * （用户环境装有 <b>OptiFine</b> ✗，它会替换物品模型管线 ✓）。</p>
 *
 * <h3>★ 用法</h3>
 * <p>必须在 {@code ModelRegistryEvent} 里调 ✓ —— 该事件在资源重载**之前** ✗，
 * 注册早了会被清空 ✓。本类由 {@code ClientProxy.preInit} 挂到 Forge 总线上 ✓。</p>
 */
@SideOnly(Side.CLIENT)
public class ClientModelHandler {

    private ClientModelHandler() {
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        // ── 注意：方块**不需要**在这里注册 ✓ ──
        // 它们走 blockstates + 模型 ✓（实测已正常 ✓）

        // ① 方块对应的 ItemBlock ✓ —— 名字与方块一致 ✓
        registerIfPresent(Items.WOODEN_ZIPLINE_HOOK, "wooden_zipline_hook");
        registerIfPresent(Items.IRON_ZIPLINE_HOOK, "iron_zipline_hook");

        // ② 普通物品 ✓
        registerIfPresent(Items.ZIPLINE_ROPE, "zipline_rope");
        registerIfPresent(Items.PARCOOL_GUIDE, "parcool_guide");
    }

    /**
     * 把物品的 {@code meta 0} 绑到 {@code parcool:<name>} 的 {@code inventory} 变体上 ✓。
     *
     * <p><b>⚠️ 必须用 {@code isPresent()} 守卫</b> ✗ —— 本工程的 {@code RegistryRef.get()}
     * 在未赋值时<b>抛异常</b> ✗（不是返回 null ✓），直接调会把模型注册整个打断 ✓。</p>
     */
    private static void registerIfPresent(com.alrex.parcool.utilities.RegistryRef<Item> ref, String name) {
        if (!ref.isPresent()) return;
        Item item = ref.get();
        if (item == null) return;
        // ⚠️ 用 "inventory" 变体 ✗ —— 这正是 Forge 自动回退时**第一优先**尝试的那个 ✓，
        //    显式注册它可保证与 blockstates 的解析结果一致 ✓
        ModelLoader.setCustomModelResourceLocation(
                item, 0,
                new ModelResourceLocation(new ResourceLocation(ParCool.MOD_ID, name), "inventory"));
    }

    /** 备查：方块名与物品名的对照 ✓（两者在 1.12.2 必须同名 ✗）。 */
    public static String[] debugBlockItemNames() {
        return new String[]{Blocks.WOODEN_ZIPLINE_HOOK.get().getRegistryName().toString()};
    }

    // ── ★ 物品的**染色层**注册（滑索绳 ✓）────────────────────────────────
    //
    // ⚠️ 没有这一步，绳索的贴图会**保持原色**（看起来「所有颜色都一样」✗）——
    //    因为颜色的来源是 NBT ✓，而 NBT 要有人**读出来刷到贴图上** ✓。
    //
    // 1.16 vs 1.12.2：
    //   · 接口方法名：getColor(stack, i)  ✗  →  colorMultiplier(stack, tintIndex) ✓
    //   · 注册入口：Minecraft.getItemColors().register(handler, item)  ✗
    //              →  Minecraft.getMinecraft().getItemColors()
    //                 .registerItemColorHandler(handler, items...) ✓

    /**
     * 由 {@code ClientProxy.init()} 调用 ✓（**必须在 Minecraft 实例就绪之后** ✗）。
     */
    public static void registerItemColors() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc == null || mc.getItemColors() == null) return;
        if (!Items.ZIPLINE_ROPE.isPresent()) return;

        mc.getItemColors().registerItemColorHandler(
                ZiplineRopeColorHandler.INSTANCE,
                Items.ZIPLINE_ROPE.get());
    }

    /**
     * 绳索染色器 ✓ —— 只染 **{@code tintIndex == 0}** 那一层 ✓。
     *
     * <p>绳索模型是两层 ✓（见 {@code models/item/zipline_rope.json} ✓）：
     * {@code layer0} = **白色底绳** ✓ → 这一层被染成配置的颜色 ✓；
     * {@code layer1} = 花纹覆盖层 ✓ → 保持原色 ✓。</p>
     * <p>这是原版的染色范式 ✓（皮革盔甲同理 ✓）——
     * <b>上游也是 {@code i > 0 ? -1 : getColor(stack)}</b> ✓，语义一致 ✓。</p>
     */
    @SideOnly(Side.CLIENT)
    private static final class ZiplineRopeColorHandler
            implements net.minecraft.client.renderer.color.IItemColor {

        static final ZiplineRopeColorHandler INSTANCE = new ZiplineRopeColorHandler();

        /**
         * @param tintIndex 图层序号 ✓ —— {@code 0} = 底绳（要染 ✓），{@code >=1} = 覆盖层（不染 ✓）
         * @return {@code -1}（{@code 0xFFFFFFFF}）= **不染色，用贴图原色** ✓
         */
        @Override
        public int colorMultiplier(@Nonnull net.minecraft.item.ItemStack stack, int tintIndex) {
            if (tintIndex > 0) {
                return -1;   // 覆盖层：保持原色 ✓
            }
            return com.alrex.parcool.common.item.zipline.ZiplineRopeItem.getColor(stack); // 底绳：染 ✓
        }
    }
}
