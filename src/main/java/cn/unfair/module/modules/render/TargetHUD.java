package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render2DEvent;
import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.module.modules.render.targethud.impl.*;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ProjectionUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.ResourceLocation;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class TargetHUD extends ModuleWithModuleSettings {
    public static final Minecraft mc = Minecraft.getMinecraft();
    public static final DecimalFormat HEALTH_FORMAT = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    public static final DecimalFormat DIFF_FORMAT = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    public static final String FORMAT = "§";
    private static final float FOLLOW_PLAYER_X_PADDING = 2.0F;

    public final TimerUtil lastAttackTimer = new TimerUtil();
    public final TimerUtil animTimer = new TimerUtil();
    public final ModeProperty health = new ModeProperty("health", 0, new String[]{"ENTITY", "TAB"});
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);
    public final BooleanProperty followPlayer = new BooleanProperty("follow-player", false);
    public EntityLivingBase lastTarget = null;
    public EntityLivingBase target = null;
    public ResourceLocation headTexture = null;
    public float oldHealth = 0.0F;
    public float newHealth = 0.0F;
    public float maxHealth = 0.0F;
    public float lastHealthBar = 0.0F;
    public TimerUtil fadeTimer = null;
    public boolean fadingIn = false;
    public EntityLivingBase fadingEntity = null;
    public TargetHUD() {
        super("TargetHUD", false, true, "mode",
                new TargetHUDMyauMode(),
                new TargetHUDRavenModernMode(),
                new TargetHUDRavenLegacyMode(),
                new TargetHUDUnfairMode(),
                new TargetHUDNovolineMode(),
                new TargetHUDExhibitionMode()
        );
    }

    public TargetHUDMode getCurrentMode() {
        return (TargetHUDMode) this.getCurrentSubModule();
    }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        } else if (!this.kaOnly.getValue()
                && !this.lastAttackTimer.hasTimeElapsed(1500L)
                && TeamUtil.isEntityLoaded(this.lastTarget)) {
            return this.lastTarget;
        } else {
            return this.chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
        }
    }

    private boolean isChatPreviewTarget(EntityLivingBase entity) {
        return entity == mc.thePlayer && this.chatPreview.getValue() && mc.currentScreen instanceof GuiChat;
    }

    private void startFadeIn() {
        this.fadeTimer = new TimerUtil();
        this.fadeTimer.reset();
        this.fadingIn = true;
        this.fadingEntity = null;
    }

    private void startFadeOut(EntityLivingBase entity) {
        this.fadeTimer = new TimerUtil();
        this.fadeTimer.reset();
        this.fadingIn = false;
        this.fadingEntity = entity;
    }

    private void clearFade() {
        this.fadeTimer = null;
        this.fadingIn = false;
        this.fadingEntity = null;
    }

    private void updateTargetState() {
        if (!this.isEnabled() || mc.thePlayer == null) {
            this.target = null;
            this.clearFade();
            return;
        }

        EntityLivingBase previousTarget = this.target;
        EntityLivingBase resolvedTarget = this.resolveTarget();
        this.target = resolvedTarget;

        if (this.target != null) {
            if (this.isChatPreviewTarget(this.target)) {
                this.clearFade();
                return;
            }

            this.fadingEntity = null;
            if ((previousTarget == null || this.fadeTimer != null && !this.fadingIn) && this.fadeTimer == null) {
                this.startFadeIn();
            } else if (this.fadeTimer != null && !this.fadingIn) {
                this.startFadeIn();
            } else if (this.fadingIn && this.fadeTimer != null && this.fadeTimer.getElapsedTime() >= 400L) {
                this.clearFade();
            }
            return;
        }

        if (previousTarget != null) {
            if (previousTarget == mc.thePlayer) {
                this.clearFade();
            } else if (this.fadeTimer == null || this.fadingIn) {
                this.startFadeOut(previousTarget);
            }
        }

        if (this.fadeTimer != null && (this.fadingIn || this.fadingEntity == null || this.fadeTimer.getElapsedTime() >= 400L)) {
            this.target = null;
            this.clearFade();
        }
    }

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            if (playerInfo != null) {
                return playerInfo.getLocationSkin();
            }
        }
        return null;
    }

    private float getTabHealth(EntityLivingBase entityLivingBase) {
        if (!(entityLivingBase instanceof EntityPlayer) || mc.theWorld == null) {
            return -1.0F;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return -1.0F;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
        if (objective == null) {
            return -1.0F;
        }
        Score score = scoreboard.getValueFromObjective(entityLivingBase.getName(), objective);
        return score == null ? -1.0F : (float) score.getScorePoints();
    }

    public static float finiteOrDefault(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    public static float finiteHealth(float value) {
        return Math.max(0.0F, finiteOrDefault(value, 0.0F));
    }

    public HealthInfo getHealthInfo(EntityLivingBase entityLivingBase) {
        float healthPoints = finiteHealth(entityLivingBase.getHealth());
        if (this.health.getValue() == 1) {
            float tabHealth = this.getTabHealth(entityLivingBase);
            if (Float.isFinite(tabHealth) && tabHealth >= 0.0F) {
                healthPoints = finiteHealth(tabHealth);
            }
        }

        float absorptionHearts = finiteHealth(entityLivingBase.getAbsorptionAmount()) / 2.0F;
        float healthHearts = healthPoints / 2.0F + absorptionHearts;
        float maxHearts = Math.max(finiteHealth(entityLivingBase.getMaxHealth()), healthPoints) / 2.0F;
        return new HealthInfo(healthHearts, absorptionHearts, Math.max(maxHearts, 1.0F));
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            this.target = null;
            this.clearFade();
            return;
        }

        this.updateTargetState();
        if (this.target == null && this.fadeTimer == null) {
            return;
        }

        EntityLivingBase entity = this.target != null ? this.target : this.fadingEntity;
        if (entity == null) {
            return;
        }

        HealthInfo targetHealthInfo = this.getHealthInfo(entity);
        float targetHealth = targetHealthInfo.health;
        if (entity != this.target) {
            this.headTexture = null;
            this.animTimer.setTime();
            this.oldHealth = targetHealth;
            this.newHealth = targetHealth;
        }
        TargetHUDMode currentMode = this.getCurrentMode();
        if (!currentMode.shouldAnimateHealth() || this.animTimer.hasTimeElapsed(150L)) {
            this.oldHealth = this.newHealth;
            this.newHealth = targetHealth;
            this.maxHealth = targetHealthInfo.maxHealth;
            if (this.oldHealth != this.newHealth) {
                this.animTimer.reset();
            }
        }

        ResourceLocation resourceLocation = this.getSkin(entity);
        if (resourceLocation != null) {
            this.headTexture = resourceLocation;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity packet) {
            if (packet.getAction() != Action.ATTACK) {
                return;
            }
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase) {
                if (entity instanceof EntityArmorStand) {
                    return;
                }
                this.lastAttackTimer.reset();
                this.lastTarget = (EntityLivingBase) entity;
            }
        }
    }

    public boolean shouldRenderWidget() {
        this.updateTargetState();
        return this.isEnabled()
                && mc.thePlayer != null
                && !mc.gameSettings.showDebugInfo
                && (this.target != null || this.fadeTimer != null && this.fadingEntity != null);
    }

    public boolean shouldRenderWidgetEffects() {
        return this.shouldRenderWidget() && this.getCurrentMode().shouldRenderEffects(this);
    }

    public float[] getWidgetSize() {
        return this.getCurrentMode().getSize(this, this.getRenderData());
    }

    public boolean shouldFollowPlayer() {
        EntityLivingBase entity = this.getRenderableEntity();
        return this.followPlayer.getValue() && entity != null && entity != mc.thePlayer;
    }

    public float[] getFollowPosition(float width, float height) {
        EntityLivingBase entity = this.getRenderableEntity();
        if (!this.followPlayer.getValue() || entity == null || entity == mc.thePlayer) {
            return null;
        }

        ProjectionUtil.Projection projection = ProjectionUtil.projectEntity(entity);
        if (projection == null) {
            return null;
        }

        return new float[]{
                projection.right() + FOLLOW_PLAYER_X_PADDING,
                projection.bottom() - (projection.bottom() - projection.top()) / 2.0F - height / 2.0F
        };
    }

    public void renderWidget(float partialTicks, float x, float y) {
        if (!this.shouldRenderWidget()) {
            return;
        }
        RenderData data = this.getRenderData();
        if (data == null) {
            return;
        }
        this.getCurrentMode().render(this, data, x, y);
    }

    public void renderWidgetMask(float partialTicks, float x, float y, int color) {
        if (!this.shouldRenderWidgetEffects()) {
            return;
        }
        RenderData data = this.getRenderData();
        if (data == null) {
            return;
        }
        this.getCurrentMode().renderMask(this, data, x, y, color);
    }

    public EntityLivingBase getRenderableEntity() {
        return this.target != null ? this.target : this.fadingEntity;
    }

    public RenderData getRenderData() {
        EntityLivingBase entity = this.getRenderableEntity();
        if (entity == null || mc.thePlayer == null) {
            return null;
        }
        HealthInfo playerHealthInfo = this.getHealthInfo(mc.thePlayer);
        HealthInfo targetHealthInfo = this.getHealthInfo(entity);
        return new RenderData(entity, playerHealthInfo.health, targetHealthInfo.absorption, targetHealthInfo.health, targetHealthInfo.maxHealth);
    }

    public int getFadeAlpha() {
        if (this.fadeTimer == null) {
            return 255;
        }

        long elapsed = this.fadeTimer.getElapsedTime();
        if (elapsed < 400L) {
            return this.fadingIn ? (int) (elapsed / 400.0F * 255.0F) : (int) (255.0F - elapsed / 400.0F * 255.0F);
        }

        if (!this.fadingIn) {
            this.target = null;
            this.fadeTimer = null;
            this.fadingEntity = null;
            return 0;
        }

        return 255;
    }

    public TargetHudBounds getModernBounds(String playerInfo, float widgetX, float widgetY) {
        int padding = 8;
        int targetStrWithPadding = mc.fontRendererObj.getStringWidth(playerInfo) + padding;
        int textX = Math.round(widgetX) + padding;
        int textY = Math.round(widgetY) + padding;
        int left = textX - padding;
        int top = textY - padding;
        int right = textX + targetStrWithPadding;
        int contentBottom = textY + (mc.fontRendererObj.FONT_HEIGHT + 5) - 6 + padding;
        return new TargetHudBounds(left, top, right, contentBottom, contentBottom + 13, textX, textY);
    }

    public String buildModernPlayerInfo(EntityLivingBase entity, float targetHealth, float playerHealth, boolean indicator) {
        targetHealth = finiteHealth(targetHealth);
        playerHealth = finiteHealth(playerHealth);
        String playerInfo = entity.getDisplayName().getFormattedText();
        playerInfo += " " + FORMAT + "c" + String.format("%.1f", targetHealth);

        if (indicator) {
            playerInfo += " " + (targetHealth <= playerHealth ? FORMAT + "aW" : FORMAT + "cL");
        }
        return playerInfo;
    }

    public int[] getRavenGradientColors() {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        return new int[]{
                HUD.getColor(System.currentTimeMillis()).getRGB(),
                HUD.getColor(System.currentTimeMillis() + 500L).getRGB()
        };
    }

    public float updateRavenHealthBar(float healthBar, int barLeft, int barRight) {
        healthBar = finiteOrDefault(healthBar, barLeft);
        this.lastHealthBar = finiteOrDefault(this.lastHealthBar, healthBar);
        if (this.lastHealthBar != healthBar && this.lastHealthBar - barLeft >= 3.0F) {
            float diff = this.lastHealthBar - healthBar;
            if (diff > 0.0F) {
                this.lastHealthBar -= diff * 0.1F;
            } else {
                this.lastHealthBar += -diff * 0.1F;
            }
        } else {
            this.lastHealthBar = healthBar;
        }

        if (this.lastHealthBar > barRight) {
            this.lastHealthBar = barRight;
        }
        return this.lastHealthBar;
    }

    public record TargetHudBounds(int left, int top, int right, int contentBottom, int bottom, int textX, int textY) {

        public float width() {
                return this.right - this.left;
            }

            public float height() {
                return this.bottom - this.top;
            }
        }

    public record HealthInfo(float health, float absorption, float maxHealth) {
    }

    public record RenderData(EntityLivingBase entity, float playerHealth, float absorption, float targetHealth,
                             float maxHealth) {
    }
}
