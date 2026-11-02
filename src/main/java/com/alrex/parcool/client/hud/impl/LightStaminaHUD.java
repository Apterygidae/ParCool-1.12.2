package com.alrex.parcool.client.hud.impl;

import com.alrex.parcool.api.Effects;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.alrex.parcool.config.ParCoolConfig;
import com.alrex.parcool.utilities.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * **轻量**体力 HUD ✓ —— 10 个小图标，紧贴饥饿条**上方** ✓（**默认样式** ✓）。
 *
 * <p>平时会淡出 ✓；只在「体力刚变化过」或「有动作要显示状态」时才出现 ✓
 * （除非配置 {@code show_light_hud_always} 打开 ✓）。</p>
 *
 * <h3>⚠️ 1.12.2 的映射（本类特有的几处）</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code player.level.getGameTime()}</td><td>{@code player.world.getTotalWorldTime()} ✗</td></tr>
 *   <tr><td>{@code player.getRandom()}</td><td>{@code player.getRNG()} ✗</td></tr>
 *   <tr><td>{@code player.hasEffect(potion)}</td><td>{@code player.isPotionActive(potion)} ✗</td></tr>
 *   <tr><td><b>{@code ForgeIngameGui.right_height}</b></td>
 *       <td><b>{@code GuiIngameForge.right_height}</b> ✗★（**两个类名正好相反** ✗）<br>
 *           = {@code public static int} ✓（默认 39；渲染完右下角 HUD 后 +10 ✓）</td></tr>
 *   <tr><td>{@code event.getWindow().getGuiScaledWidth()}</td>
 *       <td>{@code event.getResolution().getScaledWidth()} ✗</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public class LightStaminaHUD {

    private long lastStaminaChangedTick = 0;
    /** 1 = 正在恢复，-1 = 正在消耗，0 = 没变化 ✓。 */
    private int lastChangingSign = 0;
    private int changingSign = 0;
    private long changingTimeTick = 0;
    private int randomOffset = 0;
    private boolean justBecameMax = false;

    private float statusValue = 0f;
    private float oldStatusValue = 0f;
    private boolean showStatus = false;

    public void onTick(TickEvent.ClientTickEvent event, EntityPlayerSP player) {
        IStamina stamina = IStamina.get(player);
        Parkourability parkourability = Parkourability.get(player);
        if (stamina == null || parkourability == null) return;

        // ── 体力变化方向 / 持续时间（决定「跳动」动画 ✓）──
        changingSign = (int) Math.signum(stamina.get() - stamina.getOldValue());
        final long gameTime = player.world.getTotalWorldTime();
        if (changingSign != lastChangingSign) {
            lastChangingSign = changingSign;
            changingTimeTick = 0;
        } else {
            changingTimeTick++;
        }

        // 消耗时图标的随机抖动 ✓
        if (player.getRNG().nextInt(5) == 0) {
            randomOffset += player.getRNG().nextBoolean() ? 1 : -1;
        } else {
            randomOffset = 0;
        }

        if (stamina.get() != stamina.getOldValue() || stamina.isExhausted()) {
            lastStaminaChangedTick = gameTime;
        }
        justBecameMax = stamina.getOldValue() < stamina.get() && stamina.get() == stamina.getActualMaxStamina();

        // ── 动作状态条（与 StaminaHUD 同逻辑 ✓）──
        oldStatusValue = statusValue;
        boolean oldShowStatus = showStatus;
        showStatus = false;
        if (ParCoolConfig.Client.Booleans.ShowActionStatusBar.get()) {
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
        }
        if (!oldShowStatus && showStatus) {
            oldStatusValue = statusValue;
        }
    }

    public void render(RenderGameOverlayEvent.Post event, Parkourability parkourability, IStamina stamina) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null || player.isCreative()) return;

        // ⚠️ 1.12.2：isPotionActive ✗（1.16 是 hasEffect ✓）
        final boolean inexhaustible = player.isPotionActive(Effects.INEXHAUSTIBLE.get());
        final boolean exhausted = stamina.isExhausted();

        // 平时淡出：40 tick 内没变化、且没在显示状态 → 不画 ✓
        if (!showStatus) {
            long gameTime = player.world.getTotalWorldTime();
            if (gameTime - lastStaminaChangedTick > 40
                    && !ParCoolConfig.Client.Booleans.ShowLightStaminaHUDAlways.get()) {
                return;
            }
        }

        float staminaScale = (float) stamina.get() / stamina.getActualMaxStamina();
        if (staminaScale < 0) staminaScale = 0;
        if (staminaScale > 1) staminaScale = 1;
        staminaScale *= 10f;

        float statusScale = showStatus
                ? MathUtil.lerp(oldStatusValue, statusValue, event.getPartialTicks()) * 10f
                : 0f;

        Minecraft mc = Minecraft.getMinecraft();
        int scaledWidth = event.getResolution().getScaledWidth();
        int scaledHeight = event.getResolution().getScaledHeight();

        mc.getTextureManager().bindTexture(StaminaHUD.STAMINA);

        // ★ 紧贴饥饿条左侧上方 ✓ —— 用 GuiIngameForge.right_height 参与垂直堆叠 ✓
        int baseX = scaledWidth / 2 + 91 + ParCoolConfig.Client.Integers.HorizontalOffsetOfLightStaminaHUD.get();
        int baseY = scaledHeight - GuiIngameForge.right_height
                + ParCoolConfig.Client.Integers.VerticalOffsetOfLightStaminaHUD.get();

        for (int i = 0; i < 10; i++) {
            int x = baseX - i * 8 - 9;
            int offsetY = 0;
            int textureX;

            if (inexhaustible) {
                if (showStatus) {
                    textureX = (statusScale > i + 0.9f) ? 90 : 0;
                } else {
                    textureX = 54;
                }
            } else {
                if (exhausted) {
                    textureX = 27;
                } else if (statusScale > i + 0.9f) {
                    textureX = 90;
                } else {
                    textureX = 0;
                }
            }

            if (justBecameMax) {
                textureX = 81;
            } else if (staminaScale < i) {              // 空格
                textureX += 18;
            } else if (staminaScale < i + 0.5f) {       // 半格
                textureX += 9;
            }

            if (justBecameMax) {
                offsetY = -1;
            } else if (changingSign == 1) {
                if ((changingTimeTick & 31) == i) {     // 恢复时逐格「跳动」✓
                    offsetY = -1;
                }
            } else if (i + 1 > staminaScale && staminaScale > i && changingSign == -1) {
                offsetY = randomOffset;                 // 消耗时末端抖动 ✓
            }

            Gui.drawModalRectWithCustomSizedTexture(x, baseY + offsetY, textureX, 119, 9, 9, 128, 128);
        }

        // ★ 让后续 HUD（如模组列表）继续往上堆叠 ✓
        GuiIngameForge.right_height += 10;
    }
}
