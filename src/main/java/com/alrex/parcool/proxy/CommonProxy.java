package com.alrex.parcool.proxy;

import com.alrex.parcool.common.network.NetworkHandler;
import net.minecraft.entity.player.EntityPlayer;
import com.alrex.parcool.ParCool;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * 公共代理。1.12.2 的 {@code @SidedProxy} 会在加载期分别实例化
 * {@link ClientProxy} 与 {@link ServerProxy}，因此这里可以安全地放「两侧共有」的逻辑，
 * 客户端专属逻辑全部下沉到 {@link ClientProxy}。
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // P1 阶段已接入：注册表（RegistryHandler）、属性挂载、配置加载（由 ParCool.preInit 调用）
        // P3 阶段已接入：Capability 注册（由 ParCool.preInit 调用）

        // ── 实体登记（滑索绳 ✓）──────────────────────────────────────────
        // ⚠️ 1.12.2 的实体**没有** RegistryEvent ✗ —— 走 EntityRegistry.registerModEntity ✓，
        //    它同时负责：登记类、分配实体 id、追踪范围、以及生成网络包 ✓
        //    （所以本工程的 ZiplineRopeEntity 不需要写 getAddEntityPacket ✗）
        net.minecraftforge.fml.common.registry.EntityRegistry.registerModEntity(
                com.alrex.parcool.common.entity.EntityType.ZIPLINE_ROPE_NAME,
                com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity.class,
                "zipline_rope",
                com.alrex.parcool.common.entity.EntityType.ZIPLINE_ROPE_ENTITY_ID,
                ParCool.INSTANCE,
                com.alrex.parcool.common.entity.EntityType.ZIPLINE_ROPE_TRACKING_RANGE,
                com.alrex.parcool.common.entity.EntityType.ZIPLINE_ROPE_UPDATE_FREQUENCY,
                true // 发速度同步包：绳索静止，但玩家骑乘时位置每 tick 变 ✓
        );
    }

    public void init() {
        // 网络通道必须双端建立，且早于任何发包（真正发包在玩家进服之后）
        NetworkHandler.registerMessages();
    }

    public void postInit() {
        // 收集 OreDictionary 里「可爬的杆」的额外注册（对应上游的 pole_climbable 标签）。
        // 必须放 postInit —— 各 mod 在自己 init 里注册，之后才收集得到 ✓
        com.alrex.parcool.common.tags.BlockTags.init();
        // P6 阶段在这里接入：Patchouli 手册依赖降级检查等
    }

    /** 对应 1.16.5 的 {@code ParCool.PROXY.ParCoolIsActive()}。 */
    public boolean parCoolIsActive() {
        return true;
    }

    /**
     * 判断是不是「客户端的本地玩家」。
     *
     * <p>对应 1.16.5 的 {@code PlayerEntity.isLocalPlayer()}。1.12.2 <b>没有</b>这个 API，
     * 而惯用的 {@code player instanceof EntityPlayerSP} 会让双端类去解析客户端类，
     * 在专用服务端上有 {@code NoClassDefFoundError} 风险。</p>
     *
     * <p>所以走 {@code @SidedProxy} 分派：公共侧恒 false，只有
     * {@link ClientProxy}（带 {@code @SideOnly(Side.CLIENT)}）才真正判断。
     * 这是<b>硬保证</b>，不依赖 FML 运行期剥离。</p>
     */
    public boolean isLocalPlayer(EntityPlayer player) {
        return false;
    }
}
