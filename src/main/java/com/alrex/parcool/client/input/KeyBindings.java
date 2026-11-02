package com.alrex.parcool.client.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * ParCool 的按键绑定与「当前移动意图」（1.12.2 版）。
 *
 * <p>对应上游 {@code KeyBindings}。这是 P4-B 的第一步 —— 动作要靠它判断
 * 「玩家想往哪走、按了什么键」。</p>
 *
 * <h3>⚠️ 1.12.2 没有 {@code KeyConflictContext} / {@code KeyModifier}</h3>
 * <p>上游用它们定义了 <b>Ctrl+P</b>（启用）和 <b>Alt+P</b>（打开设置）两个组合键：</p>
 * <pre>
 * new KeyBinding("key.parcool.Enable", KeyConflictContext.UNIVERSAL, KeyModifier.CONTROL,
 *                InputMappings.Type.KEYSYM, GLFW_KEY_P, "key.categories.parcool")
 * </pre>
 * <p><b>1.12.2 的 {@code KeyBinding} 只有 {@code (String name, int keyCode, String category)} 一个构造器</b>，
 * 既没有冲突上下文也没有修饰键。因此本工程改为：<b>两个绑定都注册为 P，由代码检查修饰键来区分</b>，
 * 并提供 {@link #isEnableKeyPressed()} / {@link #isOpenSettingsKeyPressed()} 两个入口
 * （{@code KeyRecorder} 用它们而不是裸的 {@code isPressed()}）。语义与上游等价。</p>
 *
 * <h3>其余替换</h3>
 * <table border="1">
 *   <tr><th>1.16.5</th><th>1.12.2</th></tr>
 *   <tr><td>{@code Minecraft.getInstance()} / {@code mc.options}</td>
 *       <td>{@code Minecraft.getMinecraft()} / {@code mc.gameSettings}</td></tr>
 *   <tr><td>{@code GLFW.GLFW_KEY_*}</td><td>{@code org.lwjgl.input.Keyboard.KEY_*}</td></tr>
 *   <tr><td>{@code InputMappings.Type.MOUSE} + button</td>
 *       <td><b>负键码</b>：{@code -100 + button}（1.12.2 的约定，右键 = -99）</td></tr>
 *   <tr><td>{@code player.input.getMoveVector()}</td>
 *       <td>{@code player.movementInput.moveStrafe / moveForward}</td></tr>
 *   <tr><td>{@code player.input.left/right/jumping}</td>
 *       <td>{@code gameSettings.keyBindLeft/Right/Jump.isKeyDown()}</td></tr>
 *   <tr><td>{@code ClientRegistry} 注册挂在 {@code FMLClientSetupEvent}</td>
 *       <td>挂在 {@code ClientProxy.init()}（{@code FMLInitializationEvent}）</td></tr>
 * </table>
 */
@SideOnly(Side.CLIENT)
public final class KeyBindings {

    public static final String CATEGORY = "key.categories.parcool";

    /** 1.12.2 约定：鼠标键用负键码，-100 是左键，-99 是右键。 */
    private static final int MOUSE_BUTTON_RIGHT = -99;

    private static final KeyBinding keyBindEnable = new KeyBinding("key.parcool.Enable", Keyboard.KEY_P, CATEGORY);
    private static final KeyBinding keyBindCrawl = new KeyBinding("key.parcool.Crawl", Keyboard.KEY_C, CATEGORY);
    private static final KeyBinding keyBindGrabWall = new KeyBinding("key.parcool.ClingToCliff", MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyBinding keyBindBreakfall = new KeyBinding("key.parcool.Breakfall", Keyboard.KEY_R, CATEGORY);
    private static final KeyBinding keyBindFastRunning = new KeyBinding("key.parcool.FastRun", Keyboard.KEY_LCONTROL, CATEGORY);
    private static final KeyBinding keyBindFlipping = new KeyBinding("key.parcool.Flipping", Keyboard.KEY_NONE, CATEGORY);
    private static final KeyBinding keyBindVault = new KeyBinding("key.parcool.Vault", MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyBinding keyBindDodge = new KeyBinding("key.parcool.Dodge", Keyboard.KEY_R, CATEGORY);
    private static final KeyBinding keyBindRideZipline = new KeyBinding("key.parcool.RideZipline", MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyBinding keyBindWallJump = new KeyBinding("key.parcool.WallJump", Keyboard.KEY_SPACE, CATEGORY);
    private static final KeyBinding keyBindHangDown = new KeyBinding("key.parcool.HangDown", MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyBinding keyBindWallSlide = new KeyBinding("key.parcool.WallSlide", MOUSE_BUTTON_RIGHT, CATEGORY);
    private static final KeyBinding keyBindHideInBlock = new KeyBinding("key.parcool.HideInBlock", Keyboard.KEY_C, CATEGORY);
    private static final KeyBinding keyBindHorizontalWallRun = new KeyBinding("key.parcool.HorizontalWallRun", Keyboard.KEY_R, CATEGORY);
    private static final KeyBinding keyBindQuickTurn = new KeyBinding("key.parcool.QuickTurn", Keyboard.KEY_NONE, CATEGORY);
    /** 上游是 Alt+P；这里注册为 P，由 {@link #isOpenSettingsKeyPressed()} 判 Alt。 */
    private static final KeyBinding keyBindOpenSettings = new KeyBinding("key.parcool.openSetting", Keyboard.KEY_P, CATEGORY);

    private static final Vec3d FORWARD_VECTOR = new Vec3d(0, 0, 1);

    private KeyBindings() {
    }

    /** 在 {@code ClientProxy.init()} 里调用。 */
    public static void register() {
        ClientRegistry.registerKeyBinding(keyBindEnable);
        ClientRegistry.registerKeyBinding(keyBindCrawl);
        ClientRegistry.registerKeyBinding(keyBindGrabWall);
        ClientRegistry.registerKeyBinding(keyBindBreakfall);
        ClientRegistry.registerKeyBinding(keyBindFastRunning);
        ClientRegistry.registerKeyBinding(keyBindDodge);
        ClientRegistry.registerKeyBinding(keyBindRideZipline);
        ClientRegistry.registerKeyBinding(keyBindWallSlide);
        ClientRegistry.registerKeyBinding(keyBindWallJump);
        ClientRegistry.registerKeyBinding(keyBindVault);
        ClientRegistry.registerKeyBinding(keyBindHorizontalWallRun);
        ClientRegistry.registerKeyBinding(keyBindHideInBlock);
        ClientRegistry.registerKeyBinding(keyBindOpenSettings);
        ClientRegistry.registerKeyBinding(keyBindQuickTurn);
        ClientRegistry.registerKeyBinding(keyBindFlipping);
        ClientRegistry.registerKeyBinding(keyBindHangDown);
    }

    // ── 修饰键辅助（顶替 1.13+ 的 KeyModifier） ─────────────────────────────

    private static boolean isCtrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private static boolean isAltDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }

    /** 启用/停用 ParCool：Ctrl+P（上游语义）。 */
    public static boolean isEnableKeyPressed() {
        return isCtrlDown() && keyBindEnable.isPressed();
    }

    /** 打开设置界面：Alt+P（上游语义）。 */
    public static boolean isOpenSettingsKeyPressed() {
        return isAltDown() && !isCtrlDown() && keyBindOpenSettings.isPressed();
    }

    // ── 原生键（1.16 用 gameSettings 的既有键） ─────────────────────────────

    public static KeyBinding getKeySprint() {
        return Minecraft.getMinecraft().gameSettings.keyBindSprint;
    }

    public static KeyBinding getKeySneak() {
        return Minecraft.getMinecraft().gameSettings.keyBindSneak;
    }

    /** 跳跃键「按住」状态。上游读 {@code player.input.jumping}；1.12.2 读按键本身更稳。 */
    public static boolean isKeyJumpDown() {
        return Minecraft.getMinecraft().gameSettings.keyBindJump.isKeyDown();
    }

    // ── 移动意图 ────────────────────────────────────────────────────────────

    /**
     * 当前的水平移动方向（单位向量，仅水平分量）。
     *
     * <p>上游用 {@code player.input.getMoveVector()} 拿到 {@code (strafe, forward)}；
     * 1.12.2 对应 {@code player.movementInput.moveStrafe / moveForward}。</p>
     */
    public static Vec3d getCurrentMoveVector() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return Vec3d.ZERO;
        float strafe = player.movementInput.moveStrafe;
        float forward = player.movementInput.moveForward;
        if (strafe == 0 && forward == 0) return Vec3d.ZERO;
        double length = Math.sqrt(strafe * strafe + forward * forward);
        return new Vec3d(strafe / length, 0, forward / length);
    }

    public static Vec3d getForwardVector() {
        return FORWARD_VECTOR;
    }

    public static boolean isAnyMovingKeyDown() {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        return settings.keyBindLeft.isKeyDown()
                || settings.keyBindRight.isKeyDown()
                || settings.keyBindForward.isKeyDown()
                || settings.keyBindBack.isKeyDown();
    }

    public static boolean isLeftAndRightDown() {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;
        return settings.keyBindLeft.isKeyDown() && settings.keyBindRight.isKeyDown();
    }

    public static boolean isKeyForwardDown() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput.moveForward > 0;
    }

    public static boolean isKeyBackDown() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput.moveForward < 0;
    }

    public static boolean isKeyLeftDown() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput.moveStrafe > 0;
    }

    public static boolean isKeyRightDown() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        return player != null && player.movementInput.moveStrafe < 0;
    }

    // ── 各动作键的取值（供 KeyRecorder 使用） ───────────────────────────────

    public static KeyBinding getKeyBindEnable() { return keyBindEnable; }
    public static KeyBinding getKeyCrawl() { return keyBindCrawl; }
    public static KeyBinding getKeyQuickTurn() { return keyBindQuickTurn; }
    public static KeyBinding getKeyGrabWall() { return keyBindGrabWall; }
    public static KeyBinding getKeyVault() { return keyBindVault; }
    public static KeyBinding getKeyActivateParCool() { return keyBindOpenSettings; }
    public static KeyBinding getKeyBreakfall() { return keyBindBreakfall; }
    public static KeyBinding getKeyFastRunning() { return keyBindFastRunning; }
    public static KeyBinding getKeyDodge() { return keyBindDodge; }
    public static KeyBinding getKeyRideZipline() { return keyBindRideZipline; }
    public static KeyBinding getKeyWallSlide() { return keyBindWallSlide; }
    public static KeyBinding getKeyHangDown() { return keyBindHangDown; }
    public static KeyBinding getKeyHideInBlock() { return keyBindHideInBlock; }
    public static KeyBinding getKeyHorizontalWallRun() { return keyBindHorizontalWallRun; }
    public static KeyBinding getKeyWallJump() { return keyBindWallJump; }
    public static KeyBinding getKeyFlipping() { return keyBindFlipping; }
}
