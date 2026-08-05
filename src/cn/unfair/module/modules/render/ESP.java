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
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.postprocessing.GlowESPBlurShader;
import cn.unfair.util.postprocessing.ShaderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import javax.vecmath.Vector4d;
import java.awt.*;
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
    public final ModeProperty mode = new ModeProperty("mode", 2, new String[]{"NONE", "2D", "3D", "GLOW", "FAKECORNER", "FAKE2D"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final ModeProperty healthBar = new ModeProperty("health-bar", 0, new String[]{"NONE", "2D", "RAVEN"});
    public final ModeProperty health = new ModeProperty("health", 0, new String[]{"ENTITY", "TAB"});
    public final FloatProperty glowExposure = new FloatProperty("glow-exposure", 2.0F, 0.5F, 3.5F, () -> this.mode.getValue() == MODE_GLOW);
    public final IntProperty glowRadius = new IntProperty("glow-radius", 5, 2, 30, () -> this.mode.getValue() == MODE_GLOW);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);
    private final GlowESPBlurShader blurShader = new GlowESPBlurShader();
    private Framebuffer framebuffer = null;
    private Framebuffer glowFrameBuffer = null;
    private List<EntityPlayer> glowEntities = new ArrayList<>();
    private boolean renderingGlowEntities = false;

    public ESP() {
        super("ESP", false, true);
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

    public boolean isRenderingGlowEntities() {
        return this.renderingGlowEntities;
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
        if (this.framebuffer == null || this.glowFrameBuffer == null || this.glowEntities.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
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
        ShaderUtils.drawQuads();
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
        ShaderUtils.drawQuads();
        this.blurShader.stop();
        RenderUtil.bindTexture(0);
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    @EventTarget(Priority.HIGH)
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && (this.mode.getValue() == MODE_2D || this.mode.getValue() == MODE_GLOW || this.healthBar.getValue() == 1)) {
            if (this.mode.getValue() == MODE_GLOW) {
                this.renderGlowPass();
            }
            if (this.mode.getValue() == MODE_GLOW && this.healthBar.getValue() != 1) {
                return;
            }
            List<EntityPlayer> renderedEntities = this.getRenderedPlayers();
            if (!renderedEntities.isEmpty()) {
                if (this.mode.getValue() == MODE_2D || this.healthBar.getValue() == 1) {
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
                            if (this.mode.getValue() == MODE_2D) {
                                int color = this.getEntityColor(player).getRGB();
                                // Draw outer glow (slightly darker/wider)
                                int glowAlpha = (color >> 24) & 0xFF;
                                int glowR = ((color >> 16) & 0xFF) * 2 / 3;
                                int glowG = ((color >> 8) & 0xFF) * 2 / 3;
                                int glowB = (color & 0xFF) * 2 / 3;
                                int glowColor = (Math.max(glowAlpha - 80, 0) << 24) | (glowR << 16) | (glowG << 8) | glowB;
                                RenderUtil.drawESPBox2D(x, y, z, w, 3.0F, glowColor);
                                // Draw inner outline
                                RenderUtil.drawESPBox2D(x, y, z, w, 1.5F, color);
                            }
                            if (this.healthBar.getValue() == 1) {
                                float heal = this.getHealthPoints(player) + player.getAbsorptionAmount();
                                float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
                                float box = (z - x) * 0.08F;
                                Color healthColor = ColorUtil.getHealthBlend(percent);
                                RenderUtil.drawLine(x - box, y, x - box, w, 3.0F, ColorUtil.darker(healthColor, 0.2F).getRGB());
                                RenderUtil.drawLine(x - box, w, x - box, w + (y - w) * percent, 1.5F, healthColor.getRGB());
                            }
                        }
                    }
                    GlStateManager.popMatrix();
                    RenderUtil.disableRenderState();
                }
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && this.mode.getValue() == MODE_GLOW) {
            this.createGlowFramebuffers();
            this.glowEntities = this.getRenderedPlayers();
            this.framebuffer.framebufferClear();
            this.framebuffer.bindFramebuffer(true);
            this.renderGlowEntities(event.partialTicks());
            this.framebuffer.unbindFramebuffer();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.disableLighting();
        }

        if (this.isEnabled() && (this.mode.getValue() == MODE_3D || this.mode.getValue() == MODE_FAKE_CORNER || this.mode.getValue() == MODE_FAKE_2D || this.healthBar.getValue() == 2)) {
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
                    if (this.healthBar.getValue() == 2) {
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
                        float percent = Math.min(Math.max(heal / player.getMaxHealth(), 0.0F), 1.0F);
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
