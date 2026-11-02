package com.alrex.parcool.client.renderer.entity;

import com.alrex.parcool.common.entity.zipline.ZiplineRopeEntity;
import com.alrex.parcool.common.zipline.Zipline;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 滑索绳索的渲染器 ✓ —— 1.12.2 版（**整类重写** ✗）。
 *
 * <h3>★ 它画什么</h3>
 * <p>把一条滑索画成**一根有颜色、会随弧度弯曲、有明暗交替的绳子** ✓：</p>
 * <ol>
 *   <li>按长度分段 ✗（每 0.6 格一段 ✓，最多 24 段 ✓）</li>
 *   <li>每段取曲线中点 ✓，按该处**斜率**算出绳子的倾斜偏移 ✓</li>
 *   <li>相邻两段交替明暗 ✗（{@code colorScale} 1.0 / 0.8 ✓）——
 *       这一步让绳子看起来有**绞合纹理感** ✓（否则是一条死板的色带 ✗）</li>
 *   <li>每段的亮度按它在索上的**插值位置**从起点向终点渐变 ✓</li>
 * </ol>
 *
 * <h3>🚨 1.12.2 的整条渲染管线都不同 ✗</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code EntityRenderer<T>} + {@code EntityRendererManager}</td>
 *       <td>{@link Render}&lt;T&gt; + {@link RenderManager} ✓</td></tr>
 *   <tr><td>{@code MatrixStack} / {@code Matrix4f}</td>
 *       <td><b>{@code GlStateManager} 的矩阵栈</b> ✗（{@code pushMatrix/translate/popMatrix} ✓）</td></tr>
 *   <tr><td>{@code IRenderTypeBuffer} + {@code RenderType}</td>
 *       <td><b>都没有</b> ✗ → {@link Tessellator} + {@link BufferBuilder} 直接画 ✓</td></tr>
 *   <tr><td>{@code IVertexBuilder}</td>
 *       <td><b>没有</b> ✗ → {@code BufferBuilder} 的链式调用 ✓</td></tr>
 *   <tr><td>{@code LightTexture.pack(block, sky)}</td>
 *       <td>{@code BufferBuilder.lightmap(sky, block)} ✓（参数是 lightmap 纹理坐标 ✗，见类尾说明 ✓）</td></tr>
 *   <tr><td>{@code entity.level.getBrightness(LightType.SKY, pos)}</td>
 *       <td>{@code world.getLightFromNeighbors(pos)} ✓
 *           → 一个 int：<b>高 16 位=天空、低 16 位=方块</b> ✗ → 自己拆 ✓</td></tr>
 *   <tr><td>{@code Vector3f}（顶点用）</td>
 *       <td><b>没有</b> ✗ → 直接用 {@code float[]} 存 ✓</td></tr>
 *   <tr><td>{@code getTextureLocation}</td><td>{@code getEntityTexture} ✓（且是**抽象**的 ✗）</td></tr>
 *   <tr><td>{@code RenderTypes.ZIPLINE_2D/3D}</td>
 *       <td><b>没有 RenderType 体系</b> ✗ → 用 {@code GlStateManager} 的开关组合达成同样效果 ✓</td></tr>
 * </table>
 *
 * <h3>★ 顶点格式的选择</h3>
 * <p>用 {@code DefaultVertexFormats.POSITION_TEX_LMAP_COLOR} ✓ ——
 * 它是 1.12.2 里唯一同时含「位置 + UV + 光照 + 颜色」的现成格式 ✓
 * （没有纯「位置+颜色+光照」的 ✓）。UV 固定填 {@code (0,0)} 即可 ✓ ——
 * 因为关闭了贴图 ✓，UV 不参与采样 ✓。</p>
 *
 * <p><b>⚠️ 顶点调用顺序必须与格式的元素顺序一致</b> ✗：
 * {@code pos → tex → lightmap → color} ✓，写错会错位（表现为颜色/亮度乱掉 ✓）。</p>
 *
 * <p><b>⚠️ 本批只实现 2D 版</b> ✗ —— 上游还有一个 {@code Enable3DRenderingForZipline}
 * 配置项切换 3D 版（四棱柱 ✓），本工程的配置里暂时没有这一项 ✓，
 * 所以先固定用 2D（四边形带 ✓）—— **视觉上已经是一根完整的绳子** ✓。</p>
 */
@SideOnly(Side.CLIENT)
public class ZiplineRopeRenderer extends Render<ZiplineRopeEntity> {

    /** 绳宽的一半 ✓（单位：方块 ✓ —— 0.075 约等于 1.2 像素 ✓）。 */
    private static final float ROPE_HALF_WIDTH = 0.075f;

    /** √2 ✓ —— 上游用 0.075/√2 让四边形的对角线宽度正好等于 0.075 ✓。 */
    private static final float INV_SQRT_2 = 1f / 1.41421356f;

    /** 每段绳的最大长度 ✓（段数 = 总长 / 这个值 ✓）。 */
    private static final double SEGMENT_LENGTH = 0.6;

    /** 段数上限 ✓（防止超长索产生天量顶点 ✓）。 */
    private static final int MAX_SEGMENTS = 24;

    /** 不用真贴图 ✓，但 {@code getEntityTexture} 是抽象方法必须给一个 ✓。 */
    private static final ResourceLocation DUMMY_TEXTURE = new ResourceLocation("textures/entity/arrow");

    public ZiplineRopeRenderer(RenderManager renderManager) {
        super(renderManager);
    }

    @Nonnull
    @Override
    protected ResourceLocation getEntityTexture(@Nonnull ZiplineRopeEntity entity) {
        return DUMMY_TEXTURE;
    }

    /**
     * 可见性判定 ✓ —— **照搬上游的「摄像机到绳索直线的距离」算法**。
     *
     * <p><b>⚠️ 上游这段写在实体的 {@code shouldRender(x,y,z)} 里</b> ✗ ——
     * 本工程的 {@code ZiplineRopeEntity} 移植时没带那个方法 ✓
     * （1.12.2 的 `Render` 默认实现只做「视距 + 视锥」判断 ✗，不认自定义视距 ✓），
     * 所以这里**内联**实现 ✓，效果等价 ✓，且不必改动实体 ✓。</p>
     *
     * <p>算法：把「起点→终点」看作一条无限直线 ✓，求摄像机到它的最短距离 ✓，
     * 小于最大水平距离就渲染 ✓（比包围盒判定更贴合细长的索 ✓）。</p>
     */
    @Override
    public boolean shouldRender(@Nonnull ZiplineRopeEntity entity, @Nonnull net.minecraft.client.renderer.culling.ICamera camera,
                               double camX, double camY, double camZ) {
        BlockPos start = entity.getStartPos();
        BlockPos end = entity.getEndPos();
        if (start.equals(BlockPos.ORIGIN) && end.equals(BlockPos.ORIGIN)) return false;

        double offX = end.getX() - start.getX();
        double offY = end.getY() - start.getY();
        double offZ = end.getZ() - start.getZ();
        double baseX = start.getX() - camX;
        double baseY = start.getY() - camY;
        double baseZ = start.getZ() - camZ;
        double lenSqr = offX * offX + offY * offY + offZ * offZ;
        if (lenSqr < 1.0E-6) return true;   // 退化成一个点 → 只要在附近就画 ✓

        double t = -(offX * baseX + offY * baseY + offZ * baseZ) / lenSqr;
        double nearX = offX * t + start.getX();
        double nearY = offY * t + start.getY();
        double nearZ = offZ * t + start.getZ();
        double dx = nearX - camX;
        double dy = nearY - camY;
        double dz = nearZ - camZ;
        double distSqr = dx * dx + dy * dy + dz * dz;

        double max = Zipline.MAXIMUM_HORIZONTAL_DISTANCE;
        return distSqr < max * max;
    }

    @Override
    public void doRender(@Nonnull ZiplineRopeEntity entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        BlockPos start = entity.getStartPos();
        BlockPos end = entity.getEndPos();
        if (start.equals(BlockPos.ORIGIN) && end.equals(BlockPos.ORIGIN)) return;

        // ── 颜色 ✓（配置里存的 RGB，拆成 0..1 ✓）
        int color = entity.getColor();
        float red = ((0xFF0000 & color) >> 16) / 255f;
        float green = ((0x00FF00 & color) >> 8) / 255f;
        float blue = (0x0000FF & color) / 255f;

        Zipline zipline = entity.getZipline();
        Vec3d startPos = zipline.getStartPos();
        // ⚠️ doRender 给的是「实体相对摄像机的坐标」✗，
        //    而曲线点是**世界坐标** ✓ → 要换算成相对起点 ✓（下面 translate 到起点 ✓）
        Vec3d startOffset = startPos.subtract(entity.posX, entity.posY, entity.posZ);
        Vec3d offset = zipline.getOffsetToEndFromStart();

        // ── 光照 ✓：取两端的亮度，沿索插值 ✓
        int startLight = getPackedLight(entity, start);
        int endLight = getPackedLight(entity, end);

        // ── 分段 ✓
        int divisionCount = Math.min((int) Math.ceil(offset.length() / SEGMENT_LENGTH), MAX_SEGMENTS);
        if (divisionCount <= 0) return;

        // 水平单位向量 ✓（用来算绳宽的垂直偏移方向 ✓）
        double offsetXZ = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        float unitX = offsetXZ < 1.0E-4 ? 1f : (float) (offset.x / offsetXZ);
        float unitZ = offsetXZ < 1.0E-4 ? 0f : (float) (offset.z / offsetXZ);

        GlStateManager.pushMatrix();
        try {
            // 把原点挪到索的起点 ✓（之后所有顶点都用「相对起点」的坐标 ✓）
            GlStateManager.translate(x + startOffset.x, y + startOffset.y, z + startOffset.z);

            // ⚠️★ 标准前置：关掉 GL 的固定管线光照 ✗★
            //   实测（2026-10-11）绳索三通道被**一致地**乘了约 0.47 ✗
            //   —— 那是「顶点色 × GL 光照」的典型特征 ✓。
            //   `disableStandardItemLighting()` 会同时关掉：
            //     GL_LIGHTING / GL_LIGHT0 / GL_LIGHT1 / **GL_COLOR_MATERIAL** ✗
            //   （最后一个最关键 ✓：开着它时，顶点色会被当成「材质颜色」再乘一次光照 ✓）
            //
            //   ⚠️⚠️★ 而它【必须】在渲染结束时恢复 ✗★（2026-10-11 实测事故 ✓）
            //   原版是在**整个实体批次的外层**包一对开关 ✗：
            //     // EntityRenderer.java:1463-1470
            //     RenderHelper.enableStandardItemLighting();
            //     renderglobal.renderEntities(...);          // ← 渲染【所有】实体
            //     RenderHelper.disableStandardItemLighting();
            //   → 我在批次中间关掉它 ✗ → **排在后面的生物全部失去光照** ✓
            //     症状：生物渲染成**纯色扁平块**（无明暗）✓
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();

            // ⚠️★ 这里的三个开关要和上游的 RenderType 严格对应 ✗★
            //   上游 ZIPLINE_2D = NO_TEXTURE + NO_CULL + LIGHTMAP ✓
            //   —— 注意它**没有** TRANSLUCENT ✗，即**不透明** ✓：
            //      · 会正常写深度缓冲 ✓ → 水/其他透明物**不会**透过来 ✓
            //      · 不走 alpha 混合 ✓ → 颜色不会被 blend 拉暗 ✓
            //   （我上一版加了 enableBlend + depthMask(false) ✗ ——
            //     结果就是「水面透过绳索」+「绳索发黑」两个症状 ✓）
            GlStateManager.disableTexture2D();
            GlStateManager.disableCull();
            // ⚠️ color() 放在 disableTexture2D **之后** ✗ ——
            //    有些驱动在切纹理状态时会重置当前颜色 ✓，顺序反了会丢掉白色 ✓
            GlStateManager.color(1f, 1f, 1f, 1f);

            // ── ★ 3D / 2D 分支（配置切换 ✓，上游默认 3D ✗）──────────────
            // 上游的 RenderType 差异也只有一处 ✓：3D 开 CULL（有背面剔除 ✓），
            // 2D 关 CULL（薄片要两面可见 ✓）。
            boolean render3d = com.alrex.parcool.config.ParCoolConfig.Client.Booleans
                    .Enable3DRenderingForZipline.get();
            if (render3d) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);

            for (int seg = 0; seg < divisionCount; seg++) {
                // 相邻段交替明暗 ✓ → 视觉上形成绞合纹理 ✓
                float shade = (seg % 2 == 0) ? 1f : 0.8f;
                float r = red * shade, g = green * shade, b = blue * shade;

                if (render3d) {
                    emitSegment3D(buffer, zipline, seg, divisionCount,
                            unitX, unitZ, startLight, endLight, r, g, b);
                } else {
                    // 2D：一条带子由「两个子四边形」组成 ✓（正反两个方向的倾斜 ✓），
                    // 这样从任何角度看都是一条完整的绳 ✓（上游同此 ✓）
                    emitSegment(buffer, zipline, seg, divisionCount,
                            unitX, unitZ, startLight, endLight, r, g, b, true);
                    emitSegment(buffer, zipline, seg, divisionCount,
                            unitX, unitZ, startLight, endLight, r, g, b, false);
                }
            }

            tessellator.draw();

            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
        } finally {
            // ⚠️★ 必须把 GL 光照**恢复**回去 ✗★（否则排在后面的生物全没光照 ✓）
            //   放 `finally` 里 ✗ —— 中途抛异常也要恢复 ✓，
            //   否则一次渲染失败会**污染整帧剩余的所有实体** ✓
            //   原版在实体批次外层已经 enable 过一次 ✓ → 这里恢复 = 回到原状 ✓
            net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    /**
     * 画一段绳 ✓（一个四边形带 ✓）。
     *
     * <p>几何全部照搬上游 ✓：绳宽在「垂直于索的水平方向」上展开 ✓，
     * 同时按该处的**斜率**在竖直方向做补偿 ✓，
     * 否则索在陡峭处会看起来变细 ✗。</p>
     *
     * @param tiltUp 两个子带分别向两侧倾斜 ✓（合起来是个 X 形截面 ✓，各角度看都饱满 ✓）
     */
    private void emitSegment(BufferBuilder buffer, Zipline zipline,
                             int seg, int divisionCount,
                             float unitX, float unitZ,
                             int startLight, int endLight,
                             float r, float g, float b,
                             boolean tiltUp) {
        // 两个端点 ✓
        for (int k = 0; k < 2; k++) {
            float t = (float) (seg + k) / divisionCount;

            int light = lerpLight(startLight, endLight, t);
            Vec3d mid = zipline.getMidPointOffsetFromStart(t);

            // 斜率补偿 ✓（索越陡，竖直方向的偏移越小、水平方向越大 ✓）
            float slope = zipline.getSlope(t);
            float slopeInv = (float) MathHelper.fastInvSqrt(slope * slope + 1);
            float yOffset = ROPE_HALF_WIDTH * slopeInv * INV_SQRT_2;
            float baseX = unitX * ROPE_HALF_WIDTH * slope * slopeInv * INV_SQRT_2;
            float baseZ = unitZ * ROPE_HALF_WIDTH * slope * slopeInv * INV_SQRT_2;
            float sign = tiltUp ? 1f : -1f;
            float sideX = sign * unitZ * ROPE_HALF_WIDTH * INV_SQRT_2;
            float sideZ = sign * -unitX * ROPE_HALF_WIDTH * INV_SQRT_2;

            // 正/反两个顶点 ✗ —— 顺序对四边形绕序有影响 ✓，照搬上游的分支 ✓
            if (k == 0) {
                vertex(buffer, mid.x + baseX + sideX, mid.y - yOffset, mid.z + baseZ + sideZ, light, r, g, b);
                vertex(buffer, mid.x - baseX - sideX, mid.y + yOffset, mid.z - baseZ - sideZ, light, r, g, b);
            } else {
                vertex(buffer, mid.x - baseX - sideX, mid.y + yOffset, mid.z - baseZ - sideZ, light, r, g, b);
                vertex(buffer, mid.x + baseX + sideX, mid.y - yOffset, mid.z + baseZ + sideZ, light, r, g, b);
            }
        }
    }

    /**
     * 画一段**立体**的绳 ✓（四棱柱的一段 ✓）—— 上游的 3D 分支 ✓。
     *
     * <p>与 2D 的区别 ✗：2D 用「两个倾斜相反的薄四边形」凑出「各角度看都饱满」的假象 ✓；
     * 3D 直接给出**真正的四个侧面** ✓ —— 因此各面朝向不同、受光不同，
     * 看起来**更立体也更亮** ✓（这正是上游默认开 3D 的原因 ✓）。</p>
     *
     * <p><b>⚠️ 3D 的横向偏移没有正负号</b> ✗（2D 才有 {@code tiltUp} 的 ± 分支 ✓），
     * 照搬上游 ✓。首尾两段还各补一个**端面封口** ✓，否则会看见柱子内部 ✗。</p>
     */
    private void emitSegment3D(BufferBuilder buffer, Zipline zipline,
                               int seg, int divisionCount,
                               float unitX, float unitZ,
                               int startLight, int endLight,
                               float r, float g, float b) {
        // [组(0=近端/1=远端)][角(4)][xyz]
        final float[][] xs = new float[2][4];
        final float[][] ys = new float[2][4];
        final float[][] zs = new float[2][4];
        final int[] lights = new int[2];

        for (int k = 0; k < 2; k++) {
            float t = (float) (seg + k) / divisionCount;
            lights[k] = lerpLight(startLight, endLight, t);
            Vec3d mid = zipline.getMidPointOffsetFromStart(t);

            float slope = zipline.getSlope(t);
            float slopeInv = (float) MathHelper.fastInvSqrt(slope * slope + 1);
            float yOff = ROPE_HALF_WIDTH * slopeInv * INV_SQRT_2;
            float baseX = unitX * ROPE_HALF_WIDTH * slope * slopeInv * INV_SQRT_2;
            float baseZ = unitZ * ROPE_HALF_WIDTH * slope * slopeInv * INV_SQRT_2;
            // ⚠️ 3D 这两个偏移**不带符号** ✗（上游如此 ✓）
            float xOff = unitZ * ROPE_HALF_WIDTH * INV_SQRT_2;
            float zOff = -unitX * ROPE_HALF_WIDTH * INV_SQRT_2;

            xs[k][0] = (float) (mid.x - baseX + xOff);
            ys[k][0] = (float) (mid.y + yOff);
            zs[k][0] = (float) (mid.z - baseZ + zOff);

            xs[k][1] = (float) (mid.x - baseX - xOff);
            ys[k][1] = (float) (mid.y + yOff);
            zs[k][1] = (float) (mid.z - baseZ - zOff);

            xs[k][2] = (float) (mid.x + baseX - xOff);
            ys[k][2] = (float) (mid.y - yOff);
            zs[k][2] = (float) (mid.z + baseZ - zOff);

            xs[k][3] = (float) (mid.x + baseX + xOff);
            ys[k][3] = (float) (mid.y - yOff);
            zs[k][3] = (float) (mid.z + baseZ + zOff);
        }

        // ── 四个侧面 ✓（每个面由「近端两角 + 远端两角」构成 ✓）──
        for (int i = 0; i < 4; i++) {
            // ⚠️ 别用 a/b 当角索引 ✗ —— b 已被颜色参数占用 ✓
            int cur = i, next = (i + 1) % 4;
            vertex(buffer, xs[0][cur], ys[0][cur], zs[0][cur], lights[0], r, g, b);
            vertex(buffer, xs[0][next], ys[0][next], zs[0][next], lights[0], r, g, b);
            vertex(buffer, xs[1][next], ys[1][next], zs[1][next], lights[1], r, g, b);
            vertex(buffer, xs[1][cur], ys[1][cur], zs[1][cur], lights[1], r, g, b);
        }

        // ── 首尾端面封口 ✓（否则能看进柱子内部 ✗）──
        if (seg == 0) {
            // 起点端面：绕序与侧面相反 ✓（朝外 ✓）
            vertex(buffer, xs[0][3], ys[0][3], zs[0][3], lights[0], r, g, b);
            vertex(buffer, xs[0][2], ys[0][2], zs[0][2], lights[0], r, g, b);
            vertex(buffer, xs[0][1], ys[0][1], zs[0][1], lights[0], r, g, b);
            vertex(buffer, xs[0][0], ys[0][0], zs[0][0], lights[0], r, g, b);
        } else if (seg == divisionCount - 1) {
            vertex(buffer, xs[1][0], ys[1][0], zs[1][0], lights[1], r, g, b);
            vertex(buffer, xs[1][1], ys[1][1], zs[1][1], lights[1], r, g, b);
            vertex(buffer, xs[1][2], ys[1][2], zs[1][2], lights[1], r, g, b);
            vertex(buffer, xs[1][3], ys[1][3], zs[1][3], lights[1], r, g, b);
        }
    }

    /** 单个顶点 ✓ —— ⚠️ 调用顺序必须匹配 {@code POSITION_TEX_LMAP_COLOR} ✗。 */
    private static void vertex(BufferBuilder buffer, double x, double y, double z, int light,
                               float r, float g, float b) {
        buffer.pos(x, y, z)
                .tex(0, 0)                                  // 关掉贴图了 ✓，UV 随便填 ✓
                .lightmap(skyOf(light), blockOf(light))     // ★ 参数是 **lightmap 纹理坐标** ✗，不是 0-15 等级 ✓
                .color(r, g, b, 1f)
                .endVertex();
    }

    // ── ★ 光照拆分：**照搬原版 `Particle` 的写法** ✗ ──────────────────
    //
    // 原版唯一的权威范式（Particle.java:203-205 / renderParticle ✓）：
    //     int i = getBrightnessForRender();   // = world.getCombinedLight(pos, 0)
    //     int j = i >> 16 & 65535;            // 天空
    //     int k = i & 65535;                  // 方块
    //     ... .lightmap(j, k)
    //
    // ⚠️★ 我前两版都用 `>> 20 & 0xF` / `>> 4 & 0xF` 去取【0-15 的原始等级】✗ ——
    //   而 `lightmap()` 的入参其实是 **lightmap 纹理的 UV 坐标** ✗，
    //   期望的是**已经左移放大过**的值（原版这里的 `>> 16` 正把 `sky << 20` 变成 `sky << 4` ✓）。
    //   → 我传的 0-15 比正确值**小了 16 倍** ✗ → 采样到光照纹理最暗的一角 → **整根绳子发暗** ✓
    //   （16×16 的光照纹理里，U=sky*16、V=block*16 ✓）

    private static int skyOf(int packedLight) {
        return packedLight >> 16 & 0xFFFF;
    }

    private static int blockOf(int packedLight) {
        return packedLight & 0xFFFF;
    }

    private static int packLight(int sky, int block) {
        return sky << 16 | block;
    }

    /** 两端光照线性插值 ✓（在**已经放大过**的值上插值 ✓，与原版的量纲一致 ✓）。 */
    private static int lerpLight(int from, int to, float t) {
        int sky = (int) MathHelper.clamp(skyOf(from) + (skyOf(to) - skyOf(from)) * t, 0, 0xFFFF);
        int block = (int) MathHelper.clamp(blockOf(from) + (blockOf(to) - blockOf(from)) * t, 0, 0xFFFF);
        return packLight(sky, block);
    }

    /**
     * 取某点的**打包**光照 ✓。
     *
     * <h3>⚠️⚠️ 必须用 {@code getCombinedLight}，不能用 {@code getLightFromNeighbors} ✗</h3>
     * <p>两者返回的东西**完全不是一回事** ✗（这是我第二版仍然纯黑的原因 ✓）：</p>
     * <ul>
     *   <li>{@code getLightFromNeighbors(pos)} ✗ → 内部走 {@code Chunk.getLightSubtracted}
     *       ✓ —— 返回的是**单个 0-15 左右的合并等级** ✗
     *       （天空与方块取较大者、再减去 {@code skylightSubtracted} ✓），
     *       **不是**打包值 ✗</li>
     *   <li>{@code getCombinedLight(pos, 0)} ✓ → 才是 {@code sky << 20 | block << 4} ✓</li>
     * </ul>
     * <p>我按 {@code >> 20} 去拆一个 0-15 的小整数 ✗ → 恒为 0 → **绳索全黑** ✓</p>
     */
    private static int getPackedLight(ZiplineRopeEntity entity, BlockPos pos) {
        return entity.world.getCombinedLight(pos, 0);
    }
}
