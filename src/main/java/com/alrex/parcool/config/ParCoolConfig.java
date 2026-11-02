package com.alrex.parcool.config;

import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.ActionList;
import com.alrex.parcool.common.capability.IStamina;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/**
 * ParCool 配置中心（1.12.2 版，底层 Configuration）。
 *
 * 结构：Client / Server 两个静态内部类，各含三个枚举
 * （Booleans / Integers / Doubles），全部实现 ConfigItem。
 * 注册由 Forge14ConfigManager.registerAll() 驱动。
 */
public class ParCoolConfig {

    public enum AdvantageousDirection {
        Lower, Higher
    }

    public enum ConfigGroup {
        Animation, CameraAnimation, HUD, Modifier, Control, Stamina, Other
    }

    private static Property[] actionPossibilities = new Property[0];
    private static Property[] actionStaminaConsumptions = new Property[0];
    private static Property[] serverActionPermissions = new Property[0];
    private static Property[] serverStaminaConsumptions = new Property[0];
    private static Property[] animatorPossibilities = new Property[0];

    static void registerPerAction(Configuration configuration) {
        int size = ActionList.getSize();
        actionPossibilities = new Property[size];
        actionStaminaConsumptions = new Property[size];
        serverActionPermissions = new Property[size];
        serverStaminaConsumptions = new Property[size];

        for (int i = 0; i < size; i++) {
            Class<? extends Action> action = ActionList.getByIndex(i);
            if (action == null) continue;
            String name = action.getSimpleName();

            actionPossibilities[i] = Forge14ConfigManager.defineBoolean(
                    configuration, "Client." + ParCoolConfig.ConfigGroup.Control,
                    "Enable the action: " + name, "action_possibility." + name, true);

            actionStaminaConsumptions[i] = Forge14ConfigManager.defineInt(
                    configuration, "Client." + ParCoolConfig.ConfigGroup.Stamina,
                    "Stamina consumed by the action: " + name,
                    "action_stamina_consumption." + name,
                    ActionList.ACTION_REGISTRIES.get(i).getDefaultStaminaConsumption(), 0, 100000);

            serverActionPermissions[i] = Forge14ConfigManager.defineBoolean(
                    configuration, "Server." + ParCoolConfig.ConfigGroup.Control,
                    "Permit the action on this server: " + name, "action_permission." + name, true);

            serverStaminaConsumptions[i] = Forge14ConfigManager.defineInt(
                    configuration, "Server." + ParCoolConfig.ConfigGroup.Stamina,
                    "Server-side stamina consumption of the action: " + name,
                    "action_stamina_consumption." + name,
                    ActionList.ACTION_REGISTRIES.get(i).getDefaultStaminaConsumption(), 0, 100000);
        }
    }

    static void registerPerAnimator(Configuration configuration) {
        int size = com.alrex.parcool.client.animation.AnimatorList.getSize();
        animatorPossibilities = new Property[size];
        for (int i = 0; i < size; i++) {
            Class<?> animator = com.alrex.parcool.client.animation.AnimatorList.getByIndex(i);
            if (animator == null) continue;
            String name = animator.getSimpleName();
            animatorPossibilities[i] = Forge14ConfigManager.defineBoolean(
                    configuration, "Client." + ParCoolConfig.ConfigGroup.Animation,
                    "Enable the animation: " + name, "animator_possibility." + name, true);
        }
    }

    public static boolean getPossibilityOf(Class<? extends Action> action) {
        int idx = ActionList.getIndexOf(action);
        if (idx < 0 || idx >= actionPossibilities.length) return true;
        return Forge14ConfigManager.getBoolean(actionPossibilities[idx], true);
    }

    public static int getStaminaConsumptionOf(Class<? extends Action> action) {
        int idx = ActionList.getIndexOf(action);
        if (idx < 0 || idx >= actionStaminaConsumptions.length) return 0;
        return Forge14ConfigManager.getInt(actionStaminaConsumptions[idx], 0);
    }

    public static boolean getServerPermitted(Class<? extends Action> action) {
        int idx = ActionList.getIndexOf(action);
        if (idx < 0 || idx >= serverActionPermissions.length) return true;
        return Forge14ConfigManager.getBoolean(serverActionPermissions[idx], true);
    }

    public static int getServerStaminaConsumption(Class<? extends Action> action) {
        int idx = ActionList.getIndexOf(action);
        if (idx < 0 || idx >= serverStaminaConsumptions.length) return 0;
        return Forge14ConfigManager.getInt(serverStaminaConsumptions[idx], 0);
    }

    public static boolean canAnimate(Class<? extends com.alrex.parcool.client.animation.Animator> animator) {
        int idx = com.alrex.parcool.client.animation.AnimatorList.getIndex(animator);
        if (idx < 0 || idx >= animatorPossibilities.length) return true;
        return Forge14ConfigManager.getBoolean(animatorPossibilities[idx], true);
    }

    public static class Client {

        static final String CATEGORY_PREFIX = "Client.";

        private static Property staminaTypeProperty;
        private static Property crawlControlProperty;
        private static Property fastRunControlProperty;
        private static Property vaultAnimationModeProperty;
        private static Property clingToCliffControlProperty;
        private static Property wallJumpControlProperty;
        private static Property hWallRunControlProperty;
        private static Property flipControlProperty;

        // ── P5 体力 HUD 的枚举型配置 ✓ ──────────────────────────────────
        //   上游用 ForgeConfigSpec.defineEnum ✓；1.12.2 没有 ✗ →
        //   改用 defineString 存枚举名的字符串 ✓，读取时 valueOf ✓
        //   （与上面 crawl_control 等同一套做法 ✓）
        private static Property staminaHUDTypeProperty;
        private static Property alignHStaminaHUDProperty;
        private static Property alignVStaminaHUDProperty;

        static void registerExtra(Configuration configuration) {
            staminaTypeProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Stamina.name(),
                    "Which stamina implementation to use on this client: Default (ParCool's own stamina bar) or Hunger (use the hunger bar).\n[Requires re-entering the world to take effect!]",
                    "used_stamina", IStamina.Type.Default);
            crawlControlProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Control.name(),
                    "How Crawl is triggered: PressKey (hold the key) or Toggle (press to toggle).",
                    "crawl_control", com.alrex.parcool.common.action.impl.Crawl.ControlType.PressKey);
            fastRunControlProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Control.name(),
                    "How FastRun is triggered: PressKey (hold the key), Toggle, or Auto (always while sprinting).",
                    "fast-run_control", com.alrex.parcool.common.action.impl.FastRun.ControlType.PressKey);
            vaultAnimationModeProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Animation.name(),
                    "Vault animation: SpeedVault, KongVault, or Dynamic (select dynamically).",
                    "vault_animation_mode", com.alrex.parcool.common.action.impl.Vault.TypeSelectionMode.Dynamic);
            clingToCliffControlProperty = clingToCliffControlInit(configuration);
            wallJumpControlProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Control.name(),
                    "How WallJump is triggered: PressKey (press) or ReleaseKey (release).",
                    "wall-jump_control", com.alrex.parcool.common.action.impl.WallJump.ControlType.PressKey);
            hWallRunControlProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Control.name(),
                    "How HorizontalWallRun is triggered: PressKey (hold the key) or Auto.",
                    "h-wall-run_control", com.alrex.parcool.common.action.impl.HorizontalWallRun.ControlType.PressKey);
            flipControlProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Control.name(),
                    "How Flipping is triggered: PressRightAndLeft, TapMovementAndJump, or PressFlippingKey.",
                    "flip_control", com.alrex.parcool.common.action.impl.Flipping.ControlType.TapMovementAndJump);

            // ── P5 体力 HUD（上游在 "Stamina_HUD_Configuration" 分组里 ✓，这里同组 ✓）──
            staminaHUDTypeProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.HUD.name(),
                    "Stamina HUD type: Normal (bar), Light (icons) or Hide.",
                    "stamina_hud_type",
                    com.alrex.parcool.client.hud.impl.HUDType.Light);
            alignHStaminaHUDProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.HUD.name(),
                    "Horizontal alignment of the normal stamina HUD: Left or Right.",
                    "align_h_s_hud",
                    com.alrex.parcool.client.hud.Position.Horizontal.Right);
            alignVStaminaHUDProperty = Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.HUD.name(),
                    "Vertical alignment of the normal stamina HUD: Top or Bottom.",
                    "align_v_s_hud",
                    com.alrex.parcool.client.hud.Position.Vertical.Bottom);
        }

        private static Property clingToCliffControlInit(Configuration configuration) {
            return Forge14ConfigManager.defineEnum(
                    configuration, CATEGORY_PREFIX + ParCoolConfig.ConfigGroup.Control.name(),
                    "How ClingToCliff is triggered: PressKey (hold the key) or Toggle (press to toggle).",
                    "cling_to_cliff_control", com.alrex.parcool.common.action.impl.ClingToCliff.ControlType.PressKey);
        }

        public static IStamina.Type getStaminaType() {
            return IStamina.Type.valueOf(Forge14ConfigManager.getString(staminaTypeProperty, IStamina.Type.Default.name()));
        }

        public static com.alrex.parcool.common.action.impl.Crawl.ControlType getCrawlControl() {
            return com.alrex.parcool.common.action.impl.Crawl.ControlType.valueOf(
                    Forge14ConfigManager.getString(crawlControlProperty, com.alrex.parcool.common.action.impl.Crawl.ControlType.PressKey.name()));
        }

        public static com.alrex.parcool.common.action.impl.FastRun.ControlType getFastRunControl() {
            return com.alrex.parcool.common.action.impl.FastRun.ControlType.valueOf(
                    Forge14ConfigManager.getString(fastRunControlProperty, com.alrex.parcool.common.action.impl.FastRun.ControlType.PressKey.name()));
        }

        public static com.alrex.parcool.common.action.impl.Vault.TypeSelectionMode getVaultAnimationMode() {
            return com.alrex.parcool.common.action.impl.Vault.TypeSelectionMode.valueOf(
                    Forge14ConfigManager.getString(vaultAnimationModeProperty, com.alrex.parcool.common.action.impl.Vault.TypeSelectionMode.Dynamic.name()));
        }

        public static com.alrex.parcool.common.action.impl.ClingToCliff.ControlType getClingToCliffControl() {
            return com.alrex.parcool.common.action.impl.ClingToCliff.ControlType.valueOf(
                    Forge14ConfigManager.getString(clingToCliffControlProperty, com.alrex.parcool.common.action.impl.ClingToCliff.ControlType.PressKey.name()));
        }

        public static com.alrex.parcool.common.action.impl.WallJump.ControlType getWallJumpControl() {
            return com.alrex.parcool.common.action.impl.WallJump.ControlType.valueOf(
                    Forge14ConfigManager.getString(wallJumpControlProperty, com.alrex.parcool.common.action.impl.WallJump.ControlType.PressKey.name()));
        }

        public static com.alrex.parcool.common.action.impl.HorizontalWallRun.ControlType getHWallRunControl() {
            return com.alrex.parcool.common.action.impl.HorizontalWallRun.ControlType.valueOf(
                    Forge14ConfigManager.getString(hWallRunControlProperty, com.alrex.parcool.common.action.impl.HorizontalWallRun.ControlType.PressKey.name()));
        }

        public static com.alrex.parcool.common.action.impl.Flipping.ControlType getFlipControl() {
            return com.alrex.parcool.common.action.impl.Flipping.ControlType.valueOf(
                    Forge14ConfigManager.getString(flipControlProperty, com.alrex.parcool.common.action.impl.Flipping.ControlType.TapMovementAndJump.name()));
        }

        // ── P5 体力 HUD 的枚举读取 ✓ ────────────────────────────────────

        /** 体力 HUD 的显示样式（{@code stamina_hud_type} ✓）。 */
        public static com.alrex.parcool.client.hud.impl.HUDType getStaminaHUDType() {
            return com.alrex.parcool.client.hud.impl.HUDType.valueOf(
                    Forge14ConfigManager.getString(staminaHUDTypeProperty, com.alrex.parcool.client.hud.impl.HUDType.Light.name()));
        }

        /** 普通体力条的水平对齐（{@code align_h_s_hud} ✓）。 */
        public static com.alrex.parcool.client.hud.Position.Horizontal getAlignHorizontalStaminaHUD() {
            return com.alrex.parcool.client.hud.Position.Horizontal.valueOf(
                    Forge14ConfigManager.getString(alignHStaminaHUDProperty, com.alrex.parcool.client.hud.Position.Horizontal.Right.name()));
        }

        /** 普通体力条的垂直对齐（{@code align_v_s_hud} ✓）。 */
        public static com.alrex.parcool.client.hud.Position.Vertical getAlignVerticalStaminaHUD() {
            return com.alrex.parcool.client.hud.Position.Vertical.valueOf(
                    Forge14ConfigManager.getString(alignVStaminaHUDProperty, com.alrex.parcool.client.hud.Position.Vertical.Bottom.name()));
        }

        public enum Booleans implements ConfigItem<Boolean> {
            ParCoolIsActive(
                    ParCoolConfig.ConfigGroup.Other, "Enable ParCool on this client. Turn this off to make all parkour actions unavailable.", "parcool_activation", true
            ),
            EnableAnimation(
                    ParCoolConfig.ConfigGroup.Animation, "Enable third person animations.", "enable_animation", true
            ),
            EnableFPVAnimation(
                    ParCoolConfig.ConfigGroup.Animation, "Enable first person view animations.", "enable_fpv_animation", true
            ),
            EnableStaminaExhaustionPenalty(
                    ParCoolConfig.ConfigGroup.Stamina, "Apply the penalty (cannot sprint) while stamina is exhausted.", "enable_stamina_exhaustion_penalty", true
            ),
            ShowAutoResynchronizationNotification(
                    ParCoolConfig.ConfigGroup.Other, "Show a message when ParCool limitation is being re-synchronized.", "notify_limitation_auto_resync", true
            ),
            InfiniteStamina(
                    ParCoolConfig.ConfigGroup.Stamina, "Infinite Stamina. This needs a permission from the server, even in single player.\\nPlease check the [server] section of \\'parcool.cfg\\'", "infinite_stamina", false
            ),
            InfiniteStaminaWhenCreative(
                    ParCoolConfig.ConfigGroup.Stamina, "Infinite Stamina while the player is in creative mode.", "infinite_stamina_if_creative_mode", true
            ),
            EnableCrawlInAir(
                    ParCoolConfig.ConfigGroup.Control, "Allow Crawl to start and continue while not on the ground.", "enable_crawl_in_air", false
            ),
            SubstituteSprintForFastRun(
                    ParCoolConfig.ConfigGroup.Control, "Enable players to do actions needing Fast-Running by sprint.", "substitute_sprint", false
            ),
            EnableLeanAnimationOfFastRun(
                    ParCoolConfig.ConfigGroup.Animation, "Enable lean animation while FastRun.", "enable_lean_animation_fast_run", true
            ),
            VaultKeyPressedNeeded(
                    ParCoolConfig.ConfigGroup.Control, "Make Vault need Vault Key Pressed.", "vault_needs_key_pressed", false
            ),
            EnableVaultInAir(
                    ParCoolConfig.ConfigGroup.Control, "Enable Vault in air.", "enable_vault_in_air", true
            ),
            EnableActionSounds(
                    ParCoolConfig.ConfigGroup.Other, "Enable sounds triggered by Action.", "enable_sounds", true
            ),
            EnableCameraAnimationOfVault(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable animation of camera by Vault.", "enable_camera_animation_vault", false
            ),
            EnableActionParticles(
                    ParCoolConfig.ConfigGroup.Other, "Enable particles triggered by Action.", "enable_particles", true
            ),
            EnableRollWhenCreative(
                    ParCoolConfig.ConfigGroup.Control, "Enable Roll when creative mode (experimental).", "enable_roll_creative", false
            ),
            EnableCameraAnimationOfRolling(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable animation of camera by Rolling.", "enable_camera_rotation_roll", false
            ),
            EnableJustTimeEffectOfBreakfall(
                    ParCoolConfig.ConfigGroup.Other, "Enable just timing effect of Breakfall.", "enable_just_time_effect_breakfall", true
            ),
            EnableActionParticlesOfJustTimeBreakfall(
                    ParCoolConfig.ConfigGroup.Other, "Enable particles triggered by just-time breakfall.", "enable_particles_jt_breakfall", true
            ),
            EnableDoubleTappingForDodge(
                    ParCoolConfig.ConfigGroup.Control, "Enable double-tapping ctrl for Dodge.", "enable_double_tapping_for_dodge", false
            ),
            CanGetOffStepsWhileDodge(
                    ParCoolConfig.ConfigGroup.Control, "Enable getting off steps while doing dodge.", "can_get_off_steps_while_dodge", false
            ),
            EnableCameraAnimationOfDodge(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable rotation of camera by Dodge.", "enable_camera_rotation_dodge", false
            ),
            EnableWallJumpCooldown(
                    ParCoolConfig.ConfigGroup.Control, "Enable cooldown of wall jump.", "enable_wall_jump_cooldown", true
            ),
            EnableCameraAnimationOfBackWallJump(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable rotation of camera by Backward Wall-Jump.", "enable_camera_rotation_back_wall_jump", true
            ),
            EnableCameraAnimationOfHWallRun(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable animation of camera by Horizontal-WallRun.", "enable_camera_animation_h-wall-run", true
            ),
            EnableCameraAnimationOfHangDown(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable animation of camera by Hang-Down.", "enable_camera_animation_hang-down", true
            ),
            EnableCameraAnimationOfFlipping(
                    ParCoolConfig.ConfigGroup.CameraAnimation, "Enable rotation of camera by Flipping.", "enable_camera_rotation_flipping", false
            ),
            HideInBlockSneakNeeded(
                    ParCoolConfig.ConfigGroup.Control, "Make HideInBlock need player sneaking.", "hideinblock_needs_sneaking", true
            ),
            EnableFallingAnimation(
                    ParCoolConfig.ConfigGroup.Animation, "Enable custom animation of falling.", "enable_falling_animation", true
            ),
            Enable3DRenderingForZipline(
                    ParCoolConfig.ConfigGroup.Other, "Enable block like rendering of zipline.", "enable_3d_render_zipline", true
            ),
            // ── P5 体力 HUD 的三个开关 ✓（键名与上游一致 ✓）──────────────
            HideStaminaHUDWhenStaminaIsInfinite(
                    // ⚠️ 上游这里写的是 null ✗ → 界面 tooltip 会显示 "No tooltip defined." ✗
                    //    （Forge 的回落链：`<key>.tooltip` → `Property.getComment()` → 都没有就报这句）
                    //    本工程补上注释 ✓ —— 中英两侧的英文兜底都靠它 ✓
                    ParCoolConfig.ConfigGroup.HUD, "Hide the stamina HUD while stamina is infinite.",
                    "hide_hud_if_stamina_infinite", true
            ),
            ShowActionStatusBar(
                    ParCoolConfig.ConfigGroup.HUD, "Stamina HUD shows action charge rate, cool time or etc", "show_action_status_bar", true
            ),
            ShowLightStaminaHUDAlways(
                    ParCoolConfig.ConfigGroup.HUD, "Light stamina HUD shows always", "show_light_hud_always", false
            );

            public final ConfigGroup Group;
            @Nullable
            public final String Comment;
            public final String Path;
            public final boolean DefaultValue;
            public final boolean AdvantageousValue;
            public final String Translation;
            @Nullable
            private Property configInstance = null;

            Booleans(ConfigGroup group, @Nullable String comment, String path, boolean defaultValue) {
                this(group, comment, path, defaultValue, false);
            }

            Booleans(ConfigGroup group, @Nullable String comment, String path, boolean defaultValue, boolean advantageousValue) {
                this.Group = group;
                this.Comment = comment;
                this.Path = path;
                this.DefaultValue = defaultValue;
                this.AdvantageousValue = advantageousValue;
                this.Translation = "parcool.config.c." + path;
            }

            @Override public String getPath() { return Path; }
            @Override public ConfigGroup getGroup() { return Group; }
            @Override public void register(Configuration configuration) {
                configInstance = Forge14ConfigManager.defineBoolean(configuration, CATEGORY_PREFIX + Group.name(), Comment, Path, DefaultValue);
            }
            @Override public Boolean get() { return Forge14ConfigManager.getBoolean(configInstance, DefaultValue); }
            @Override public void set(Boolean value) { if (configInstance != null) configInstance.set(value); }
            @Override public Property getInternalInstance() { return configInstance; }
            @Override public void writeToBuffer(ByteBuffer buffer) { buffer.put((byte) (get() ? 1 : 0)); }
            @Override public Boolean readFromBuffer(ByteBuffer buffer) { return buffer.get() != 0; }
        }

        public enum Integers implements ConfigItem<Integer> {
            AcceptableAngleOfWallJump(
                    ParCoolConfig.ConfigGroup.Control, "Acceptable wall angle of Wall Jump : `0` means you exactly opposite to wall, `180` allows all angles", "acceptable_angle_wall_jump", 110, 0, 180
            ),
            HorizontalOffsetOfStaminaHUD(
                    ParCoolConfig.ConfigGroup.HUD, "Horizontal offset of the normal HUD", "offset_h_stamina_hud", 3, 0, 100
            ),
            VerticalOffsetOfStaminaHUD(
                    ParCoolConfig.ConfigGroup.HUD, "Vertical offset of the normal HUD", "offset_v_stamina_hud", 3, 0, 100
            ),
            HorizontalOffsetOfLightStaminaHUD(
                    ParCoolConfig.ConfigGroup.HUD, "Horizontal offset of the light HUD", "offset_h_light_hud", 0, -100, 100
            ),
            VerticalOffsetOfLightStaminaHUD(
                    ParCoolConfig.ConfigGroup.HUD, "Vertical offset of the light HUD", "offset_v_light_hud", 0, -100, 100
            ),
            WallRunContinuableTick(
                    ParCoolConfig.ConfigGroup.Modifier, "How long you can do Horizontal Wall Run", "wall-run_continuable_tick", 25, Server.Integers.MaxWallRunContinuableTick.Min, Server.Integers.MaxWallRunContinuableTick.Max
            ),
            SlidingContinuableTick(
                    ParCoolConfig.ConfigGroup.Modifier, "How long you can do Slide", "sliding_continuable_tick", 15, Server.Integers.MaxSlidingContinuableTick.Min, Server.Integers.MaxSlidingContinuableTick.Max
            ),
            SuccessiveDodgeCoolTime(
                    ParCoolConfig.ConfigGroup.Control, "How long the duration of dodge is treated as successive dodge", "successive_dodge_cool_time", 30, 0, 2147483647
            ),
            MaxSuccessiveDodgeCount(
                    ParCoolConfig.ConfigGroup.Control, "Max number of times of successive Dodge action", "successive_dodge_count", 3, 1, 2147483647
            ),
            JustTimeBreakfallTick(
                    ParCoolConfig.ConfigGroup.Control, "Window time of just-time breakfall", "justtime_breakfall_tick", Server.Integers.MaxJustTimeBreakfallTick.DefaultValue, Server.Integers.MaxJustTimeBreakfallTick.Min, Server.Integers.MaxJustTimeBreakfallTick.Max
            ),
            DodgeCoolTime(
                    ParCoolConfig.ConfigGroup.Control, "Cool time of Dodge action", "dodge_cool_time", 11, 11, 2147483647
            );

            public final ConfigGroup Group;
            @Nullable
            public final String Comment;
            public final String Path;
            public final int DefaultValue;
            public final String Translation;
            public final int Min;
            public final int Max;
            @Nullable
            private Property configInstance = null;

            Integers(ConfigGroup group, @Nullable String comment, String path, int defaultValue, int min, int max) {
                this.Group = group;
                this.Comment = comment;
                this.Path = path;
                this.DefaultValue = defaultValue;
                this.Min = min;
                this.Max = max;
                this.Translation = "parcool.config.c." + path;
            }

            @Override public String getPath() { return Path; }
            @Override public ConfigGroup getGroup() { return Group; }
            @Override public void register(Configuration configuration) {
                configInstance = Forge14ConfigManager.defineInt(configuration, CATEGORY_PREFIX + Group.name(), Comment, Path, DefaultValue, Min, Max);
            }
            @Override public Integer get() { return Forge14ConfigManager.getInt(configInstance, DefaultValue); }
            @Override public void set(Integer value) { if (configInstance != null) configInstance.set(value); }
            @Override public Property getInternalInstance() { return configInstance; }
            @Override public void writeToBuffer(ByteBuffer buffer) { buffer.putInt(get()); }
            @Override public Integer readFromBuffer(ByteBuffer buffer) { return buffer.getInt(); }
        }

        public enum Doubles implements ConfigItem<Double> {
            FastSwimSpeedModifier(
                    ParCoolConfig.ConfigGroup.Modifier, "Speed modifier of Fast Swim. The actual value is min(this, server\\'s MaxFastSwimSpeedModifier).", "fast_swim_speed_modifier", 1.0, 0.0, 10.0
            ),
            FastRunSpeedModifier(
                    ParCoolConfig.ConfigGroup.Modifier, "FastRun speed modifier. The actual value is min(this, server\\'s MaxFastRunSpeedModifier).", "fast-run_modifier", 2.0, Server.Doubles.MaxFastRunSpeedModifier.Min, Server.Doubles.MaxFastRunSpeedModifier.Max
            ),
            LowestFallDistanceForBreakfall(
                    ParCoolConfig.ConfigGroup.Control, "Lowest fall distance needed to trigger breakfall movements.", "lowest_fall_distance_for_breakfall", 2.0, 0.0, 10.0
            ),
            DodgeSpeedModifier(
                    ParCoolConfig.ConfigGroup.Modifier, "Dodge speed modifier. The actual value is min(this, server\\'s MaxDodgeSpeedModifier).", "dodge-speed_modifier", 1.0, Server.Doubles.MaxDodgeSpeedModifier.Min, Server.Doubles.MaxDodgeSpeedModifier.Max
            ),
            SkyDiveSpeedDecreaseRate(
                    ParCoolConfig.ConfigGroup.Modifier, "SkyDive speed decrease rate.", "sky_dive-speed-decreasement", 0.98, Server.Doubles.MinSkyDiveSpeedDecreaseRate.Min, Server.Doubles.MinSkyDiveSpeedDecreaseRate.Max
            ),
            DamageCompleteRemovableHeightBreakfall(
                    ParCoolConfig.ConfigGroup.Control, "How long distance breakfall can remove damage.", "max_breakfall_damage_remove_height", 6.0, 0.0, 1024.0
            ),
            DamageReductionRateBreakfall(
                    ParCoolConfig.ConfigGroup.Control, "Damage reduction rate of Breakfall.", "max_breakfall_damage_reduction_rate", 0.6, 0.0, 1.0
            );

            public final ConfigGroup Group;
            @Nullable
            public final String Comment;
            public final String Path;
            public final double DefaultValue;
            public final String Translation;
            public final double Min;
            public final double Max;
            @Nullable
            private Property configInstance = null;

            Doubles(ConfigGroup group, @Nullable String comment, String path, double defaultValue, double min, double max) {
                this.Group = group;
                this.Comment = comment;
                this.Path = path;
                this.DefaultValue = defaultValue;
                this.Min = min;
                this.Max = max;
                this.Translation = "parcool.config.c." + path;
            }

            @Override public String getPath() { return Path; }
            @Override public ConfigGroup getGroup() { return Group; }
            @Override public void register(Configuration configuration) {
                configInstance = Forge14ConfigManager.defineDouble(configuration, CATEGORY_PREFIX + Group.name(), Comment, Path, DefaultValue, Min, Max);
            }
            @Override public Double get() { return Forge14ConfigManager.getDouble(configInstance, DefaultValue); }
            @Override public void set(Double value) { if (configInstance != null) configInstance.set(value); }
            @Override public Property getInternalInstance() { return configInstance; }
            @Override public void writeToBuffer(ByteBuffer buffer) { buffer.putDouble(get()); }
            @Override public Double readFromBuffer(ByteBuffer buffer) { return buffer.getDouble(); }
        }
    }

    public static class Server {

        static final String CATEGORY_PREFIX = "Server.";

        /**
         * 「是否把这些限制施加给玩家」—— 对应上游 Server spec 根上的一个独立
         * {@code BooleanValue}（不在 {@code Server.Booleans} 里，因为上游那是个非枚举项）。
         *
         * <p>⚠️ <b>这不是一条普通配置，是一道总闸。</b>{@code false}（默认）时
         * {@link com.alrex.parcool.common.info.ServerLimitation} 根本不会 apply 全局限制 →
         * {@code [Server.*]} 段里的数值/开关<b>一条都不生效</b>，玩家拿到的是「最有利」的值。
         * {@code true} 时这些值才会作为 {@code GlobalLimitation} 施加给所有玩家。</p>
         *
         * <p>这是上游语义（见 {@code Limitation#readFromServerConfig()} 里
         * {@code enabled = LimitationEnabled.get()} 那一行），移植时原样保留 ——
         * 否则「管理员改配置」和「管理员用命令施加限制」两套机制会互相打架。</p>
         */
        private static Property limitationEnabledProperty;

        public static boolean isLimitationEnabled() {
            return Forge14ConfigManager.getBoolean(limitationEnabledProperty, false);
        }

        /** 非枚举的「额外项」注册入口（由 {@code Forge14ConfigManager.registerAll} 调用）。 */
        static void registerExtra(Configuration configuration) {
            limitationEnabledProperty = Forge14ConfigManager.defineBoolean(
                    configuration, CATEGORY_PREFIX + ConfigGroup.Other.name(),
                    "Whether these limitations will be imposed to players. "
                            + "OFF (default): every [Server.*] value below is ignored and players get the most advantageous values. "
                            + "ON: the values below are imposed on all players.",
                    "limitation_imposed", false);
        }

        public enum Booleans implements ConfigItem<Boolean> {
            AllowInfiniteStamina(
                    ParCoolConfig.ConfigGroup.Stamina, "Allow clients to enable infinite stamina. Even in single player this needs to be true.", "allow_infinite_stamina", true, true
            ),
            AllowDisableWallJumpCooldown(
                    ParCoolConfig.ConfigGroup.Control, "Allow disabling cooldown of wall jump.", "allow_disabling_wall_jump_cooldown", true, true
            ),
            DodgeProvideInvulnerableFrame(
                    ParCoolConfig.ConfigGroup.Control, "Enable invulnerable time by Dodge.", "enable_dodge_invulnerable_time", true, true
            );

            public final ConfigGroup Group;
            @Nullable
            public final String Comment;
            public final String Path;
            public final boolean DefaultValue;
            public final boolean AdvantageousValue;
            public final String Translation;
            @Nullable
            private Property configInstance = null;

            Booleans(ConfigGroup group, @Nullable String comment, String path, boolean defaultValue) {
                this(group, comment, path, defaultValue, false);
            }

            Booleans(ConfigGroup group, @Nullable String comment, String path, boolean defaultValue, boolean advantageousValue) {
                this.Group = group;
                this.Comment = comment;
                this.Path = path;
                this.DefaultValue = defaultValue;
                this.AdvantageousValue = advantageousValue;
                this.Translation = "parcool.config.c." + path;
            }

            @Override public String getPath() { return Path; }
            @Override public ConfigGroup getGroup() { return Group; }
            @Override public void register(Configuration configuration) {
                configInstance = Forge14ConfigManager.defineBoolean(configuration, CATEGORY_PREFIX + Group.name(), Comment, Path, DefaultValue);
            }
            @Override public Boolean get() { return Forge14ConfigManager.getBoolean(configInstance, DefaultValue); }
            @Override public void set(Boolean value) { if (configInstance != null) configInstance.set(value); }
            @Override public Property getInternalInstance() { return configInstance; }
            @Override public void writeToBuffer(ByteBuffer buffer) { buffer.put((byte) (get() ? 1 : 0)); }
            @Override public Boolean readFromBuffer(ByteBuffer buffer) { return buffer.get() != 0; }
        }

        public enum Integers implements ConfigItem<Integer> {
            MaxWallRunContinuableTick(
                    ParCoolConfig.ConfigGroup.Modifier, "Server-side upper bound of wall-run duration", "max_wall-run_continuable_tick", 100, 1, 1000
            ),
            MaxSlidingContinuableTick(
                    ParCoolConfig.ConfigGroup.Modifier, "Server-side upper bound of slide duration", "max_sliding_continuable_tick", 100, 1, 1000
            ),
            MaxJustTimeBreakfallTick(
                    ParCoolConfig.ConfigGroup.Control, "Server-side upper bound of just-time breakfall window", "max_justtime_breakfall_tick", 20, 1, 200
            ),
            MaxSuccessiveDodgeCount(
                    ParCoolConfig.ConfigGroup.Control, "Server-side upper bound of successive dodge count", "max_successive_dodge_count", 10, 1, 100
            ),
            DodgeCoolTime(
                    ParCoolConfig.ConfigGroup.Control, "Server-side lower bound of Dodge cool time", "least_dodge_cool_time", 11, 11, 2147483647, AdvantageousDirection.Lower
            ),
            SuccessiveDodgeCoolTime(
                    ParCoolConfig.ConfigGroup.Control, "Server-side lower bound of successive dodge window", "least_successive_dodge_cool_time", 0, 0, 2147483647, AdvantageousDirection.Lower
            ),
            MaxStaminaRecovery(
                    ParCoolConfig.ConfigGroup.Stamina, "Server-side upper bound of stamina recovered per tick", "max_stamina_recovery_limit", 10000, 1, 100000
            ),
            MaxStaminaLimit(
                    ParCoolConfig.ConfigGroup.Stamina, "Server-side upper bound of the player\\'s max stamina", "max_stamina_limit", 10000, 1, 1000000
            );

            public final ConfigGroup Group;
            @Nullable
            public final String Comment;
            public final String Path;
            public final int DefaultValue;
            public final String Translation;
            public final int Min;
            public final int Max;
            public final AdvantageousDirection Advantageous;
            @Nullable
            private Property configInstance = null;

            Integers(ConfigGroup group, @Nullable String comment, String path, int defaultValue, int min, int max) {
                this(group, comment, path, defaultValue, min, max, AdvantageousDirection.Higher);
            }

            Integers(ConfigGroup group, @Nullable String comment, String path, int defaultValue, int min, int max,
                     AdvantageousDirection advantageous) {
                this.Group = group;
                this.Comment = comment;
                this.Path = path;
                this.DefaultValue = defaultValue;
                this.Min = min;
                this.Max = max;
                this.Advantageous = advantageous;
                this.Translation = "parcool.config.c." + path;
            }

            @Override public String getPath() { return Path; }
            @Override public ConfigGroup getGroup() { return Group; }
            @Override public void register(Configuration configuration) {
                configInstance = Forge14ConfigManager.defineInt(configuration, CATEGORY_PREFIX + Group.name(), Comment, Path, DefaultValue, Min, Max);
            }
            @Override public Integer get() { return Forge14ConfigManager.getInt(configInstance, DefaultValue); }
            @Override public void set(Integer value) { if (configInstance != null) configInstance.set(value); }
            @Override public Property getInternalInstance() { return configInstance; }
            @Override public void writeToBuffer(ByteBuffer buffer) { buffer.putInt(get()); }
            @Override public Integer readFromBuffer(ByteBuffer buffer) { return buffer.getInt(); }
        }

        public enum Doubles implements ConfigItem<Double> {
            MaxFastSwimSpeedModifier(
                    ParCoolConfig.ConfigGroup.Modifier, "Server-side upper bound of Fast Swim speed modifier.", "max_fast_swim_speed_modifier", 1.0, 0.0, 10.0
            ),
            MaxFastRunSpeedModifier(
                    ParCoolConfig.ConfigGroup.Modifier, "Server-side upper bound of FastRun speed modifier.", "max_fast-run_modifier", 2.0, 0.001, 10.0, AdvantageousDirection.Higher
            ),
            MinLowestFallDistanceForBreakfall(
                    ParCoolConfig.ConfigGroup.Control, "Server-side lower bound of the fall distance needed to trigger breakfall.", "lowest_fall_distance_for_breakfall", 2.0, 0.0, 10.0, AdvantageousDirection.Lower
            ),
            MaxDodgeSpeedModifier(
                    ParCoolConfig.ConfigGroup.Modifier, "Server-side upper bound of Dodge speed modifier.", "max_dodge-speed_modifier", 1.0, 0.5, 3.0, AdvantageousDirection.Higher
            ),
            MinSkyDiveSpeedDecreaseRate(
                    ParCoolConfig.ConfigGroup.Modifier, "Server-side lower bound of SkyDive speed decrease rate.", "min_sky-dive_speed_decrease", 0.98, 0.001, 1.0, AdvantageousDirection.Lower
            ),
            MaxDamageCompleteRemovableHeightBreakfall(
                    ParCoolConfig.ConfigGroup.Control, "Server-side upper bound of the fall distance breakfall can completely negate.", "max_breakfall_damage_remove_height", 6.0, 0.0, 1024.0, AdvantageousDirection.Higher
            ),
            MaxDamageReductionRateBreakfall(
                    ParCoolConfig.ConfigGroup.Control, "Server-side upper bound of Breakfall damage reduction rate.", "max_breakfall_damage_reduction_rate", 0.6, 0.0, 1.0, AdvantageousDirection.Higher
            );

            public final ConfigGroup Group;
            @Nullable
            public final String Comment;
            public final String Path;
            public final double DefaultValue;
            public final String Translation;
            public final double Min;
            public final double Max;
            public final AdvantageousDirection Advantageous;
            @Nullable
            private Property configInstance = null;

            Doubles(ConfigGroup group, @Nullable String comment, String path, double defaultValue, double min, double max) {
                this(group, comment, path, defaultValue, min, max, AdvantageousDirection.Higher);
            }

            Doubles(ConfigGroup group, @Nullable String comment, String path, double defaultValue, double min, double max,
                    AdvantageousDirection advantageous) {
                this.Group = group;
                this.Comment = comment;
                this.Path = path;
                this.DefaultValue = defaultValue;
                this.Min = min;
                this.Max = max;
                this.Advantageous = advantageous;
                this.Translation = "parcool.config.c." + path;
            }

            @Override public String getPath() { return Path; }
            @Override public ConfigGroup getGroup() { return Group; }
            @Override public void register(Configuration configuration) {
                configInstance = Forge14ConfigManager.defineDouble(configuration, CATEGORY_PREFIX + Group.name(), Comment, Path, DefaultValue, Min, Max);
            }
            @Override public Double get() { return Forge14ConfigManager.getDouble(configInstance, DefaultValue); }
            @Override public void set(Double value) { if (configInstance != null) configInstance.set(value); }
            @Override public Property getInternalInstance() { return configInstance; }
            @Override public void writeToBuffer(ByteBuffer buffer) { buffer.putDouble(get()); }
            @Override public Double readFromBuffer(ByteBuffer buffer) { return buffer.getDouble(); }
        }
    }
}
