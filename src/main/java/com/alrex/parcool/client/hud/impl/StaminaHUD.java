package com.alrex.parcool.client.hud.impl;

import com.alrex.parcool.client.hud.Position;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * **普通**体力 HUD ✓ —— 一条横条（对应配置 {@code stamina_hud_type = Normal} ✓）。
 *
 * <h3>⚠️ 1.12.2 的映射（整块对照）</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code @OnlyIn(Dist.CLIENT)}</td><td>{@code @SideOnly(Side.CLIENT)} ✗</td></tr>
 *   <tr><td>{@code extends AbstractGui} + {@code blit(stack, x,y,u,v,w,h,tw,th)}</td>
 *       <td>{@code Gui.drawModalRectWithCustomSizedTexture(x,y,u,v,w,h,tw,th)}<br>
 *           （1.12.2 是**静态方法** ✓，无需继承；无 {@code MatrixStack} ✗）</td></tr>
 *   <tr><td>{@code Minecraft.getInstance()}</td><td>{@code Minecraft.getMinecraft()} ✗</td></tr>
 *   <tr><td>{@code getTextureManager().bind(loc)}</td><td>{@code getTextureManager().bindTexture(loc)} ✗</td></tr>
 *   <tr><td>{@code Minecraft.getInstance().getWindow()} + {@code getGuiScaledWidth()}</td>
 *       <td>{@code event.getResolution()} + {@code getScaledWidth()} ✗（{@code ScaledResolution}）</td></tr>
 *   <tr><td>{@code Tuple.getA() / getB()}</td><td>{@code getFirst() / getSecond()} ✗</td></tr>
 *   <tr><td>{@code ClientPlayerEntity}</td><td>{@code EntityPlayerSP} ✗</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class StaminaHUD {

    public static final ResourceLocation STAMINA =
            new ResourceLocation("parcool", "textures/gui/stamina_bar.png");

    /** 阴影条的追赶系数 ✓（越小追得越慢 ✓）。 */
    private float shadowScale = 1f;
    /** 0,1,2 —— 体力条的「流动」动画帧 ✓（每 5 tick 换一帧 ✓）。 */
    private int renderGageType = 0;
    private int renderGageTick = 0;
    private float statusValue = 0f;
    private float oldStatusValue = 0f;
    private boolean showStatus = false;

    public void onTick(TickEvent.ClientTickEvent event, EntityPlayerSP player) {
        Parkourability parkourability = Parkourability.get(player);
        if (parkourability == null) return;

        // 每 5 tick 换一个「流动」帧 ✓
        if (++renderGageTick >= 5) {
            renderGageTick = 0;
            if (++renderGageType > 2) {
                renderGageType = 0;
            }
        }

        oldStatusValue = statusValue;
        boolean oldShowStatus = showStatus;
        showStatus = false;

        // 找出「当前正在做、且想显示状态条」的那一个动作 ✓
        for (Action a : parkourability.getList()) {
            if (a.wantsToShowStatusBar(player, parkourability)) {
                showStatus = true;
                statusValue = a.getStatusValue(player, parkourability);
                if (statusValue > 1f) {
                    statusValue = 1f;
                } else if (statusValue < 0f) {
                    statusValue = 0f;
                }
                break;
            }
        }
        // 刚出现状态条的那一帧，不要从 0 插值上来 ✓（否则会看到一条从无到有的拉伸 ✓）
        if (!oldShowStatus && showStatus) {
            oldStatusValue = statusValue;
        }
    }

    public void render(RenderGameOverlayEvent.Post event, Parkourability parkourability, IStamina stamina) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return;
        if (player.isCreative()) return;

        ScaledResolution resolution = event.getResolution();
        Position position = new Position(
                ParCoolConfig.Client.getAlignHorizontalStaminaHUD(),
                ParCoolConfig.Client.getAlignVerticalStaminaHUD(),
                ParCoolConfig.Client.Integers.HorizontalOffsetOfStaminaHUD.get(),
                ParCoolConfig.Client.Integers.VerticalOffsetOfStaminaHUD.get()
        );
        final int width = resolution.getScaledWidth();
        final int height = resolution.getScaledHeight();
        final int boxWidth = 91;
        final int boxHeight = 17;
        final Tuple<Integer, Integer> pos = position.calculate(boxWidth, boxHeight, width, height);
        // ⚠️ 1.12.2 的 Tuple 是 getFirst/getSecond ✗（1.16 是 getA/getB ✓）
        final int x = pos.getFirst();
        final int y = pos.getSecond();

        float staminaScale = (float) stamina.get() / stamina.getActualMaxStamina();
        float statusScale = showStatus
                ? MathUtil.lerp(oldStatusValue, statusValue, event.getPartialTicks())
                : 0f;

        if (staminaScale < 0) staminaScale = 0;
        if (staminaScale > 1) staminaScale = 1;

        Minecraft.getMinecraft().getTextureManager().bindTexture(STAMINA);

        // 外框 ✓
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 93, 17, 128, 128);
        if (!stamina.isExhausted()) {
            // 状态条（动作蓄力/冷却 ✓）
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 102, (int) Math.ceil(92 * statusScale), 17, 128, 128);
            // 落后于真实值的阴影条 ✓
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 85, Math.round(16 + 69 * shadowScale) + 1, 12, 128, 128);
            // 当前体力 ✓
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 17 * (renderGageType + 1), Math.round(16 + 69 * staminaScale) + 1, 12, 128, 128);
        } else {
            // 力竭：用另一套配色 ✓
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 68, Math.round(16 + 69 * staminaScale) + 1, 17, 128, 128);
        }
        // 阴影条向真实值追赶 ✓（指数逼近 ✓）
        shadowScale = staminaScale - (staminaScale - shadowScale) / 1.1f;
    }
}
