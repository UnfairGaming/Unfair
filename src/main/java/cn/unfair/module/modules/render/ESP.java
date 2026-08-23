package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.enums.ChatColors;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.Priority;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.ResizeEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.AndroidUtil;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.shader.GlowESPBlurShader;
import cn.unfair.util.shader.ShaderUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int MODE_2D = 1;
    private static final int MODE_3D = 2;
    private static final int MODE_GLOW = 3;
    private static final int MODE_FAKE_CORNER = 4;
    private static final int MODE_FAKE_2D = 5;

    private final DecimalFormat dFormat = new DecimalFormat("0.0");

    public final ModeProperty mode = new ModeProperty("Mode", 2, new String[]{"None", "2D", "3D", "Glow", "Fakecorner", "Fake2D"});
    public final ModeProperty color = new ModeProperty("Color", 0, new String[]{"Default", "Teams", "Hud"});
    public final ModeProperty healthBar = new ModeProperty("Health Bar", 0, new String[]{"None", "2D", "Raven"}, () -> mode.getValue() != MODE_2D);
    public final ModeProperty health = new ModeProperty("Health", 0, new String[]{"Entity", "Tab"});

    private final BooleanProperty outline = new BooleanProperty("Outline", true, () -> mode.getValue() == MODE_2D);
    private final ModeProperty boxMode = new ModeProperty("Box Mode", 0, new String[]{"Box", "Corners"}, () -> mode.getValue() == MODE_2D);

    private final BooleanProperty healthBar2D = new BooleanProperty("Health-Bar", true, () -> mode.getValue() == MODE_2D);
    private final ModeProperty hpBarMode = new ModeProperty("HBar-Mode", 0, new String[]{"Dot", "Line"}, () -> mode.getValue() == MODE_2D && healthBar2D.getValue());
    private final BooleanProperty absorption = new BooleanProperty("Render-Absorption", true, () -> mode.getValue() == MODE_2D && healthBar2D.getValue() && hpBarMode.getValue() == 1);
    private final BooleanProperty healthNumber = new BooleanProperty("HealthNumber", true, () -> mode.getValue() == MODE_2D && healthBar2D.getValue());
    private final ModeProperty hpMode = new ModeProperty("HP-Mode", 0, new String[]{"Health", "Percent"}, () -> mode.getValue() == MODE_2D && healthBar2D.getValue() && healthNumber.getValue());

    private final BooleanProperty armorBar = new BooleanProperty("Armor-Bar", true, () -> mode.getValue() == MODE_2D);
    private final ModeProperty armorBarMode = new ModeProperty("ABar-Mode", 0, new String[]{"Total", "Items"}, () -> mode.getValue() == MODE_2D && armorBar.getValue());
    private final BooleanProperty armorNumber = new BooleanProperty("ItemArmorNumber", true, () -> mode.getValue() == MODE_2D && armorBar.getValue());
    private final BooleanProperty armorItems = new BooleanProperty("ArmorItems", true, () -> mode.getValue() == MODE_2D);
    private final BooleanProperty armorDur = new BooleanProperty("ArmorDurability", true, () -> mode.getValue() == MODE_2D && armorItems.getValue());

    private final BooleanProperty tagsValue = new BooleanProperty("Tags", true, () -> mode.getValue() == MODE_2D);
    private final BooleanProperty tagsBGValue = new BooleanProperty("Tags-Background", false, () -> mode.getValue() == MODE_2D && tagsValue.getValue());
    private final BooleanProperty itemTagsValue = new BooleanProperty("Item-Tags", true, () -> mode.getValue() == MODE_2D);
    private final FloatProperty fontScaleValue = new FloatProperty("Font-Scale", 0.5f, 0.1f, 1.0f, () -> mode.getValue() == MODE_2D);

    private final BooleanProperty droppedItems = new BooleanProperty("Dropped-Items", false, () -> mode.getValue() == MODE_2D);

    private final IntProperty colorRedValue = new IntProperty("Red", 255, 0, 255, () -> mode.getValue() == MODE_2D);
    private final IntProperty colorGreenValue = new IntProperty("Green", 255, 0, 255, () -> mode.getValue() == MODE_2D);
    private final IntProperty colorBlueValue = new IntProperty("Blue", 255, 0, 255, () -> mode.getValue() == MODE_2D);

    public final FloatProperty glowExposure = new FloatProperty("Glow Exposure", 2.0F, 0.5F, 3.5F, () -> this.mode.getValue() == MODE_GLOW);
    public final IntProperty glowRadius = new IntProperty("Glow Radius", 5, 2, 30, () -> this.mode.getValue() == MODE_GLOW);
    public final BooleanProperty players = new BooleanProperty("Players", true);
    public final BooleanProperty friends = new BooleanProperty("Friends", true);
    public final BooleanProperty enemies = new BooleanProperty("Enemies", true);
    public final BooleanProperty self = new BooleanProperty("Self", false);
    public final BooleanProperty bots = new BooleanProperty("Bots", false);

    private GlowESPBlurShader blurShader;
    private boolean glowAvailable;
    private Framebuffer framebuffer = null;
    private Framebuffer glowFrameBuffer = null;
    private List<EntityPlayer> glowEntities = new ArrayList<>();
    @Getter
    private boolean renderingGlowEntities = false;

    public ESP() {
        super("ESP", false, true);
        try {
            if (AndroidUtil.isAndroid()) {
                this.glowAvailable = false;
                return;
            }
            this.blurShader = new GlowESPBlurShader();
            this.glowAvailable = true;
        } catch (RuntimeException exception) {
            this.glowAvailable = false;
            System.err.println("ESP glow shader unavailable; falling back to 2D ESP.");
            exception.printStackTrace();
        }
    }

    private boolean shouldRenderPlayer(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (!entityPlayer.ignoreFrustumCheck && !RenderUtil.isInViewFrustum(entityPlayer.getEntityBoundingBox(), 0.1F)) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.shouldBlockRenderTarget(entityPlayer)) {
                return false;
            } else if (!TeamUtil.isAntiBotEnabled() && TeamUtil.isBot(entityPlayer)) {
                return this.bots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.friends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.enemies.getValue() : this.players.getValue();
            }
        } else {
            return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) {
            return Unfair.friendManager.getColor();
        } else if (TeamUtil.isTarget(entityPlayer)) {
            return Unfair.targetManager.getColor();
        } else {
            switch (this.color.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, 1.0F);
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor);
                case 2:
                    Unfair.moduleManager.modules.get(HUD.class);
                    int hudColor = HUD.getColor(System.currentTimeMillis()).getRGB();
                    return new Color(hudColor);
                default:
                    return new Color(-1);
            }
        }
    }


    private Color get2DColor(Entity entity) {
        if (entity instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entity)) {
                return Color.BLUE;
            }
            switch (this.color.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor((EntityPlayer) entity, 1.0F);
                case 1:
                    int teamColor = TeamUtil.isSameTeam((EntityPlayer) entity) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor);
                case 2:
                    int hudColor = HUD.getColor(System.currentTimeMillis()).getRGB();
                    return new Color(hudColor);
                default:
                    return Color.WHITE;
            }
        }
        return new Color(colorRedValue.getValue(), colorGreenValue.getValue(), colorBlueValue.getValue());
    }

    private float getTabHealth(EntityPlayer entityPlayer) {
        if (mc.theWorld == null) {
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
        Score score = scoreboard.getValueFromObjective(entityPlayer.getName(), objective);
        return score == null ? -1.0F : (float) score.getScorePoints();
    }

    private float getHealthPoints(EntityPlayer entityPlayer) {
        if (this.health.getValue() == 1) {
            float tabHealth = this.getTabHealth(entityPlayer);
            if (tabHealth >= 0.0F) {
                return tabHealth;
            }
        }
        return entityPlayer.getHealth();
    }

    private Color getGlowColor() {
        return HUD.getColor(System.currentTimeMillis());
    }

    @EventTarget
    public void onResize(ResizeEvent event) {
        this.deleteGlowFramebuffers();
    }

    private void createGlowFramebuffers() {
        if (this.framebuffer != null
                && this.glowFrameBuffer != null
                && this.framebuffer.framebufferWidth == mc.displayWidth
                && this.framebuffer.framebufferHeight == mc.displayHeight
                && this.glowFrameBuffer.framebufferWidth == mc.displayWidth
                && this.glowFrameBuffer.framebufferHeight == mc.displayHeight) {
            return;
        }
        this.framebuffer = RenderUtil.createFrameBuffer(this.framebuffer, true);
        this.glowFrameBuffer = RenderUtil.createFrameBuffer(this.glowFrameBuffer, true);
    }

    private void deleteGlowFramebuffers() {
        if (this.framebuffer != null) {
            this.framebuffer.deleteFramebuffer();
            this.framebuffer = null;
        }
        if (this.glowFrameBuffer != null) {
            this.glowFrameBuffer.deleteFramebuffer();
            this.glowFrameBuffer = null;
        }
    }

    private List<EntityPlayer> getRenderedPlayers() {
        return TeamUtil.getLoadedEntitiesSorted()
                .stream()
                .filter(entity -> entity instanceof EntityPlayer && this.shouldRenderPlayer((EntityPlayer) entity))
                .map(EntityPlayer.class::cast)
                .collect(Collectors.toList());
    }


    private List<Entity> get2DRenderedEntities() {
        return mc.theWorld.loadedEntityList.stream()
                .filter(this::isValid2DEntity)
                .collect(Collectors.toList());
    }


    private boolean isValid2DEntity(Entity entity) {
        if (entity == null) return false;
        if (entity.isInvisible()) return false;
        if (entity instanceof EntityItem) return droppedItems.getValue();
        if (entity instanceof EntityPlayer) {
            return this.shouldRenderPlayer((EntityPlayer) entity);
        }
        return entity instanceof EntityLivingBase && entity != mc.getRenderViewEntity();
    }

    private void renderGlowEntities(float partialTicks) {
        if (this.glowEntities.isEmpty()) {
            return;
        }

        boolean shadow = mc.gameSettings.entityShadows;
        mc.gameSettings.entityShadows = false;
        this.renderingGlowEntities = true;
        try {
            for (EntityPlayer player : this.glowEntities) {
                boolean invisible = player.isInvisible();
                try {
                    player.setInvisible(false);
                    RendererLivingEntity.setShaderBrightness(player.hurtTime > 0 ? Color.RED : this.getEntityColor(player));
                    mc.getRenderManager().renderEntityStaticNoShadow(player, partialTicks, false);
                } finally {
                    RendererLivingEntity.unsetShaderBrightness();
                    player.setInvisible(invisible);
                }
            }
        } finally {
            this.renderingGlowEntities = false;
            mc.gameSettings.entityShadows = shadow;
        }
    }

    private void renderGlowPass() {
        if (!this.glowAvailable
                || this.blurShader == null
                || this.framebuffer == null
                || this.glowFrameBuffer == null
                || this.glowEntities.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.enableBlend();
            OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

            float radius = this.glowRadius.getValue();
            Color glowColor = this.getGlowColor();
            this.glowFrameBuffer.framebufferClear();
            this.glowFrameBuffer.bindFramebuffer(true);
            this.blurShader.use();
            this.blurShader.setup(2.0F, 0.0F, radius, this.glowExposure.getValue(), glowColor);
            RenderUtil.bindTexture(this.framebuffer.framebufferTexture);
            ShaderUtil.drawQuads();
            this.blurShader.stop();
            this.glowFrameBuffer.unbindFramebuffer();

            mc.getFramebuffer().bindFramebuffer(true);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            this.blurShader.use();
            this.blurShader.setup(0.0F, 2.0F, radius, this.glowExposure.getValue(), glowColor, true);
            RenderUtil.bindTexture(this.glowFrameBuffer.framebufferTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE16);
            RenderUtil.bindTexture(this.framebuffer.framebufferTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            ShaderUtil.drawQuads();
            this.blurShader.stop();
        } finally {
            this.glowFrameBuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            this.blurShader.stop();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            RenderUtil.bindTexture(0);
            GL20.glUseProgram(0);
            GlStateManager.resetColor();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }


    private void drawScaledString(String text, double x, double y, float scale, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, scale);
        RenderUtil.drawOutlinedString(text, 0, 0);
        GlStateManager.popMatrix();
    }

    private void drawScaledCenteredString(String text, double x, double y, float scale, int color) {
        float width = mc.fontRendererObj.getStringWidth(text);
        drawScaledString(text, x - (width * scale) / 2.0, y, scale, color);
    }

    private void renderItemStack(ItemStack stack, double x, double y) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(0.5, 0.5, 0.5);
        RenderUtil.renderItemInGUI(stack, 0, 0);
        GlStateManager.popMatrix();
    }

    private void render2DESP(float partialTicks) {
        ScaledResolution sr = new ScaledResolution(mc);
        double scaleFactor = sr.getScaleFactor();

        RenderUtil.enableRenderState();

        List<Entity> collectedEntities = this.get2DRenderedEntities();

        for (Entity entity : collectedEntities) {
            if (!RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 0.1f)) continue;

            mc.entityRenderer.setupCameraTransform(partialTicks, 0);
            Vector4d pos = RenderUtil.projectToScreen(entity, scaleFactor);
            mc.entityRenderer.setupOverlayRendering();

            if (pos == null) continue;

            double posX = pos.x;
            double posY = pos.y;
            double endPosX = pos.z;
            double endPosY = pos.w;

            if (Math.abs(posX) > 16000 || Math.abs(posY) > 16000) continue;

            int color = this.get2DColor(entity).getRGB();
            int black = Color.BLACK.getRGB();
            int background = new Color(0, 0, 0, 120).getRGB();

            if (outline.getValue()) {
                if (boxMode.getValue() == 0) {
                    RenderUtil.drawRect(posX - 1.0, posY, posX + 0.5, endPosY + 0.5, black);
                    RenderUtil.drawRect(posX - 1.0, posY - 0.5, endPosX + 0.5, posY + 0.5 + 0.5, black);
                    RenderUtil.drawRect(endPosX - 0.5 - 0.5, posY, endPosX + 0.5, endPosY + 0.5, black);
                    RenderUtil.drawRect(posX - 1.0, endPosY - 0.5 - 0.5, endPosX + 0.5, endPosY + 0.5, black);
                    RenderUtil.drawRect(posX - 0.5, posY, posX + 0.5 - 0.5, endPosY, color);
                    RenderUtil.drawRect(posX, endPosY - 0.5, endPosX, endPosY, color);
                    RenderUtil.drawRect(posX - 0.5, posY, endPosX, posY + 0.5, color);
                    RenderUtil.drawRect(endPosX - 0.5, posY, endPosX, endPosY, color);
                } else {
                    double lineW = (endPosX - posX) / 3.0;
                    double lineH = (endPosY - posY) / 4.0;
                    RenderUtil.drawRect(posX + 0.5, posY, posX - 1.0, posY + lineH + 0.5, black);
                    RenderUtil.drawRect(posX - 1.0, endPosY, posX + 0.5, endPosY - lineH - 0.5, black);
                    RenderUtil.drawRect(posX - 1.0, posY - 0.5, posX + lineW + 0.5, posY + 1.0, black);
                    RenderUtil.drawRect(endPosX - lineW - 0.5, posY - 0.5, endPosX, posY + 1.0, black);
                    RenderUtil.drawRect(endPosX - 1.0, posY, endPosX + 0.5, posY + lineH + 0.5, black);
                    RenderUtil.drawRect(endPosX - 1.0, endPosY, endPosX + 0.5, endPosY - lineH - 0.5, black);
                    RenderUtil.drawRect(posX - 1.0, endPosY - 1.0, posX + lineW + 0.5, endPosY + 0.5, black);
                    RenderUtil.drawRect(endPosX - lineW - 0.5, endPosY - 1.0, endPosX + 0.5, endPosY + 0.5, black);
                    RenderUtil.drawRect(posX, posY, posX - 0.5, posY + lineH, color);
                    RenderUtil.drawRect(posX, endPosY, posX - 0.5, endPosY - lineH, color);
                    RenderUtil.drawRect(posX - 0.5, posY, posX + lineW, posY + 0.5, color);
                    RenderUtil.drawRect(endPosX - lineW, posY, endPosX, posY + 0.5, color);
                    RenderUtil.drawRect(endPosX - 0.5, posY, endPosX, posY + lineH, color);
                    RenderUtil.drawRect(endPosX - 0.5, endPosY, endPosX, endPosY - lineH, color);
                    RenderUtil.drawRect(posX, endPosY - 0.5, posX + lineW, endPosY, color);
                    RenderUtil.drawRect(endPosX - lineW, endPosY - 0.5, endPosX - 0.5, endPosY, color);
                }
            }

            if (entity instanceof EntityLivingBase) {
                EntityLivingBase living = (EntityLivingBase) entity;
                double height = endPosY - posY;

                float hp;
                float maxHp = living.getMaxHealth();
                if (entity instanceof EntityPlayer && this.health.getValue() == 1) {
                    float tabHealth = this.getTabHealth((EntityPlayer) entity);
                    if (tabHealth >= 0.0F) {
                        hp = tabHealth;
                        maxHp = 20.0F;
                    } else {
                        hp = ((EntityPlayer) entity).getHealth();
                    }
                } else if (entity instanceof EntityPlayer) {
                    hp = ((EntityPlayer) entity).getHealth();
                } else {
                    hp = living.getHealth();
                }

                if (hp > maxHp) hp = maxHp;
                double hpPercentage = hp / maxHp;
                double hpHeight = height * hpPercentage;

                if (healthBar2D.getValue()) {
                    RenderUtil.drawRect(posX - 3.5, posY - 0.5, posX - 1.5, endPosY + 0.5, background);
                    int healthColor = ColorUtil.getHealthBlend(hp / maxHp).getRGB();

                    if (hpBarMode.getValue() == 0 && height >= 60) {
                        for (int k = 0; k < 10; k++) {
                            double reratio = MathHelper.clamp_double(hp - k * (maxHp / 10.0), 0.0, maxHp / 10.0) / (maxHp / 10.0);
                            double hei = (height / 10.0 - 0.5) * reratio;
                            RenderUtil.drawRect(posX - 3.0, endPosY - (height + 0.5) / 10.0 * k, posX - 2.0, endPosY - (height + 0.5) / 10.0 * k - hei, healthColor);
                        }
                    } else {
                        RenderUtil.drawRect(posX - 3.0, endPosY, posX - 2.0, endPosY - hpHeight, healthColor);
                        float absAmount = living.getAbsorptionAmount();
                        if (absorption.getValue() && absAmount > 0) {
                            RenderUtil.drawRect(posX - 3.0, endPosY, posX - 2.0, endPosY - (height / 6.0) * (absAmount / 2.0), new Color(255, 215, 0, 100).getRGB());
                        }
                    }

                    if (healthNumber.getValue()) {
                        String hpText = hpMode.getValue() == 0 ? dFormat.format(hp) + " §c❤" : (int) (hpPercentage * 100) + "%";
                        drawScaledString(hpText, posX - 4.0 - mc.fontRendererObj.getStringWidth(hpText) * fontScaleValue.getValue(), endPosY - hpHeight - mc.fontRendererObj.FONT_HEIGHT / 2.0f * fontScaleValue.getValue(), fontScaleValue.getValue(), -1);
                    }
                }

                if (armorBar.getValue()) {
                    if (armorBarMode.getValue() == 1) {
                        double constHeight = height / 4.0;
                        for (int m = 4; m > 0; m--) {
                            ItemStack armorStack = living.getEquipmentInSlot(m);
                            if (armorStack != null && armorStack.getItem() != null) {
                                double durabilityFactor = 1.0 - ((double) armorStack.getItemDamage() / armorStack.getMaxDamage());
                                double theHeight = constHeight + 0.25;
                                RenderUtil.drawRect(endPosX + 1.5, endPosY + 0.5 - theHeight * m, endPosX + 3.5, endPosY + 0.5 - theHeight * (m - 1), background);
                                RenderUtil.drawRect(endPosX + 2.0, endPosY + 0.5 - theHeight * (m - 1) - 0.25, endPosX + 3.0, endPosY + 0.5 - theHeight * (m - 1) - 0.25 - (constHeight - 0.25) * durabilityFactor, new Color(0, 255, 255).getRGB());
                            }
                        }
                    } else {
                        float armorVal = living.getTotalArmorValue();
                        if (armorVal > 0) {
                            double armorHeight = height * (armorVal / 20.0);
                            RenderUtil.drawRect(endPosX + 1.5, posY - 0.5, endPosX + 3.5, endPosY + 0.5, background);
                            RenderUtil.drawRect(endPosX + 2.0, endPosY, endPosX + 3.0, endPosY - armorHeight, new Color(0, 255, 255).getRGB());
                        }
                    }
                }

                if (armorItems.getValue()) {
                    double yDist = height / 4.0;
                    for (int j = 4; j > 0; j--) {
                        ItemStack armorStack = living.getEquipmentInSlot(j);
                        if (armorStack != null && armorStack.getItem() != null) {
                            double itemY = posY + yDist * (4 - j) + yDist / 2.0 - 5.0;
                            double itemX = endPosX + (armorBar.getValue() ? 4.0 : 2.0);
                            renderItemStack(armorStack, itemX, itemY);
                            if (armorDur.getValue()) {
                                int dur = armorStack.getMaxDamage() - armorStack.getItemDamage();
                                drawScaledCenteredString(String.valueOf(dur), itemX + 4.5, itemY + 9.0, fontScaleValue.getValue(), -1);
                            }
                        }
                    }
                }

                if (tagsValue.getValue()) {
                    String entName = living.getDisplayName().getFormattedText();
                    double textX = posX + (endPosX - posX) / 2.0;
                    double textY = posY - 1.0 - (mc.fontRendererObj.FONT_HEIGHT * fontScaleValue.getValue());
                    if (tagsBGValue.getValue()) {
                        float textW = mc.fontRendererObj.getStringWidth(entName) * fontScaleValue.getValue();
                        RenderUtil.drawRect(textX - textW / 2f - 2f, textY - 2f, textX + textW / 2f + 2f, textY + mc.fontRendererObj.FONT_HEIGHT * fontScaleValue.getValue(), 0x80000000);
                    }
                    drawScaledCenteredString(entName, textX, textY, fontScaleValue.getValue(), -1);
                }

                if (itemTagsValue.getValue()) {
                    ItemStack held = living.getHeldItem();
                    if (held != null && held.getItem() != null) {
                        String itemName = held.getDisplayName();
                        double textX = posX + (endPosX - posX) / 2.0;
                        double textY = endPosY + 1.0;
                        if (tagsBGValue.getValue()) {
                            float textW = mc.fontRendererObj.getStringWidth(itemName) * fontScaleValue.getValue();
                            RenderUtil.drawRect(textX - textW / 2f - 2f, textY - 2f, textX + textW / 2f + 2f, textY + mc.fontRendererObj.FONT_HEIGHT * fontScaleValue.getValue(), 0x80000000);
                        }
                        drawScaledCenteredString(itemName, textX, textY, fontScaleValue.getValue(), -1);
                    }
                }
            }
            else if (entity instanceof EntityItem && droppedItems.getValue()) {
                EntityItem item = (EntityItem) entity;
                ItemStack stack = item.getEntityItem();
                if (armorBar.getValue() && stack.isItemStackDamageable()) {
                    double maxD = stack.getMaxDamage();
                    double curD = maxD - stack.getItemDamage();
                    double per = curD / maxD;
                    double h = endPosY - posY;
                    RenderUtil.drawRect(endPosX + 1.5, posY - 0.5, endPosX + 3.5, endPosY + 0.5, background);
                    RenderUtil.drawRect(endPosX + 2.0, endPosY, endPosX + 3.0, endPosY - (h * per), new Color(0, 255, 255).getRGB());
                    if (armorNumber.getValue()) {
                        drawScaledString(String.valueOf((int) curD), endPosX + 4.0, endPosY - (h * per) - (mc.fontRendererObj.FONT_HEIGHT / 2f * fontScaleValue.getValue()), fontScaleValue.getValue(), -1);
                    }
                }
                if (itemTagsValue.getValue()) {
                    String entName = stack.getDisplayName();
                    double textX = posX + (endPosX - posX) / 2.0;
                    double textY = endPosY + 1.0;
                    if (tagsBGValue.getValue()) {
                        float textW = mc.fontRendererObj.getStringWidth(entName) * fontScaleValue.getValue();
                        RenderUtil.drawRect(textX - textW / 2f - 2f, textY - 2f, textX + textW / 2f + 2f, textY + mc.fontRendererObj.FONT_HEIGHT * fontScaleValue.getValue(), 0x80000000);
                    }
                    drawScaledCenteredString(entName, textX, textY, fontScaleValue.getValue(), -1);
                }
            }
        }
        RenderUtil.disableRenderState();
    }


    @EventTarget(Priority.HIGH)
    public void onRender(Render2DEvent event) {
        boolean glowMode = this.mode.getValue() == MODE_GLOW;

        if (this.isEnabled() && (this.mode.getValue() == MODE_2D || (glowMode && !this.glowAvailable))) {
            this.render2DESP(event.partialTicks());
            return;
        }

        if (this.isEnabled() && glowMode && this.glowAvailable) {
            this.renderGlowPass();
        }

        if (this.isEnabled() && glowMode && this.glowAvailable && this.healthBar.getValue() == 1 && this.mode.getValue() != MODE_2D) {
            List<EntityPlayer> renderedEntities = this.getRenderedPlayers();
            if (!renderedEntities.isEmpty()) {
                RenderUtil.enableRenderState();
                double scaleFactor = new ScaledResolution(mc).getScaleFactor();
                double scale = scaleFactor / Math.pow(scaleFactor, 2.0);
                GlStateManager.pushMatrix();
                GlStateManager.scale(scale, scale, scale);
                for (EntityPlayer player : renderedEntities) {
                    mc.entityRenderer.setupCameraTransform(event.partialTicks(), 0);
                    Vector4d screenPosition = RenderUtil.projectToScreen(player, scaleFactor);
                    mc.entityRenderer.setupOverlayRendering();
                    if (screenPosition != null) {
                        float x = (float) screenPosition.x;
                        float y = (float) screenPosition.y;
                        float z = (float) screenPosition.z;
                        float w = (float) screenPosition.w;
                        float heal = this.getHealthPoints(player) + player.getAbsorptionAmount();
                        float percent = Math.clamp(heal / player.getMaxHealth(), 0.0F, 1.0F);
                        float box = (z - x) * 0.08F;
                        Color healthColor = ColorUtil.getHealthBlend(percent);
                        RenderUtil.drawLine(x - box, y, x - box, w, 3.0F, ColorUtil.darker(healthColor, 0.2F).getRGB());
                        RenderUtil.drawLine(x - box, w, x - box, w + (y - w) * percent, 1.5F, healthColor.getRGB());
                    }
                }
                GlStateManager.popMatrix();
                RenderUtil.disableRenderState();
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && this.mode.getValue() == MODE_GLOW && this.glowAvailable) {
            this.createGlowFramebuffers();
            this.glowEntities = this.getRenderedPlayers();
            this.framebuffer.framebufferClear();
            this.framebuffer.bindFramebuffer(true);
            this.renderGlowEntities(event.partialTicks());
            this.framebuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.disableLighting();
        }

        if (this.isEnabled() && (this.mode.getValue() == MODE_3D || this.mode.getValue() == MODE_FAKE_CORNER || this.mode.getValue() == MODE_FAKE_2D || this.healthBar.getValue() == 2) && this.mode.getValue() != MODE_2D) {
            RenderUtil.enableRenderState();
            for (EntityPlayer player : this.getRenderedPlayers()) {
                if (player.ignoreFrustumCheck || RenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.1F)) {
                    if (this.mode.getValue() == MODE_3D) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawEntityBoundingBox(player, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), 1.5F, 0.1F);
                        GlStateManager.resetColor();
                    }
                    if (this.mode.getValue() == MODE_FAKE_CORNER) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawCornerESP(player, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                    }
                    if (this.mode.getValue() == MODE_FAKE_2D) {
                        Color color = this.getEntityColor(player);
                        RenderUtil.drawFake2DESP(player, color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
                    }
                    if (this.healthBar.getValue() == 2 && this.mode.getValue() != MODE_2D) {
                        double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.partialTicks())
                                - mc.getRenderManager().getRenderPosX();
                        double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.partialTicks())
                                - mc.getRenderManager().getRenderPosY()
                                - 0.1F;
                        double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.partialTicks())
                                - mc.getRenderManager().getRenderPosZ();
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(x, y, z);
                        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                        float heal = this.getHealthPoints(player) + player.getAbsorptionAmount();
                        float percent = Math.clamp(heal / player.getMaxHealth(), 0.0F, 1.0F);
                        Color healthColor = ColorUtil.getHealthBlend(percent);
                        float height = player.height + 0.2F;
                        RenderUtil.drawRect3D(0.57250005F, -0.027500002F, 0.7275F, height + 0.027500002F, Color.black.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height, Color.darkGray.getRGB());
                        RenderUtil.drawRect3D(0.6F, 0.0F, 0.70000005F, height * percent, healthColor.getRGB());
                        GlStateManager.popMatrix();
                    }
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}