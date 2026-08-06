package cn.unfair.util;

import cn.unfair.enums.ChatColors;
import cn.unfair.util.postprocessing.ShaderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

public class RenderUtil {
    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static final int SKEET_OUTER_COLOR = 0xE6121212;
    private static final int SKEET_MIDDLE_COLOR = 0xFF2A2A2A;
    private static final int SKEET_INNER_COLOR = 0xFF171717;
    private static final String ROUNDED_RECT_SRC =
            "#version 120\n" +
                    "uniform vec2 location, rectSize, screenSize;\n" +
                    "uniform vec4 color;\n" +
                    "uniform float radius;\n" +
                    "uniform bool blur;\n" +
                    "float roundSDF(vec2 p, vec2 b, float r) { return length(max(abs(p) - b, 0.0)) - r; }\n" +
                    "void main() {\n" +
                    "    vec2 screenPos = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y);\n" +
                    "    vec2 rectHalf = rectSize * 0.5;\n" +
                    "    vec2 pos = screenPos - location - rectHalf;\n" +
                    "    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, roundSDF(pos, rectHalf - radius - 0.25, radius));\n" +
                    "    gl_FragColor = vec4(color.rgb, color.a * smoothedAlpha);\n" +
                    "}";
    private static final String MULTI_RADIUS_SRC =
            "#version 120\n" +
                    "uniform vec2 location, rectSize, screenSize;\n" +
                    "uniform vec4 color;\n" +
                    "uniform float radiusTopLeft, radiusTopRight, radiusBottomLeft, radiusBottomRight;\n" +
                    "float roundedBoxSDF(vec2 p, vec2 b, float r) { return length(max(abs(p) - b, 0.0)) - r; }\n" +
                    "void main() {\n" +
                    "    vec2 screenPos = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y);\n" +
                    "    vec2 rectHalf = rectSize * 0.5;\n" +
                    "    vec2 pos = screenPos - location - rectHalf;\n" +
                    "    float r = (pos.x > 0.0) ? ((pos.y < 0.0) ? radiusTopRight : radiusBottomRight) : ((pos.y < 0.0) ? radiusTopLeft : radiusBottomLeft);\n" +
                    "    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, roundedBoxSDF(pos, rectHalf - r - 0.25, r));\n" +
                    "    gl_FragColor = vec4(color.rgb, color.a * smoothedAlpha);\n" +
                    "}";
    private static final String ROUNDED_GRADIENT_SRC =
            "#version 120\n" +
                    "uniform vec2 location, rectSize, screenSize;\n" +
                    "uniform vec4 color1, color2, color3, color4;\n" +
                    "uniform float radius;\n" +
                    "float roundSDF(vec2 p, vec2 b, float r) { return length(max(abs(p) - b, 0.0)) - r; }\n" +
                    "void main() {\n" +
                    "    vec2 screenPos = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y);\n" +
                    "    vec2 rectHalf = rectSize * 0.5;\n" +
                    "    vec2 pos = screenPos - location - rectHalf;\n" +
                    "    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, roundSDF(pos, rectHalf - radius - 0.25, radius));\n" +
                    "    vec2 uv = gl_TexCoord[0].st;\n" +
                    "    vec4 left = mix(color1, color2, uv.y);\n" +
                    "    vec4 right = mix(color3, color4, uv.y);\n" +
                    "    vec4 gradColor = mix(left, right, uv.x);\n" +
                    "    gl_FragColor = vec4(gradColor.rgb, gradColor.a * smoothedAlpha);\n" +
                    "}";
    private static final String ROUNDED_GRADIENT_OUTLINE_SRC =
            "#version 120\n" +
                    "uniform vec2 location, rectSize, screenSize;\n" +
                    "uniform vec4 color1, color2;\n" +
                    "uniform float radius, thickness;\n" +
                    "float roundSDF(vec2 p, vec2 b, float r) { return length(max(abs(p) - b, 0.0)) - r; }\n" +
                    "void main() {\n" +
                    "    vec2 screenPos = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y);\n" +
                    "    vec2 rectHalf = rectSize * 0.5;\n" +
                    "    vec2 pos = screenPos - location - rectHalf;\n" +
                    "    float outer = 1.0 - smoothstep(0.0, 1.0, roundSDF(pos, rectHalf - radius - 0.25, radius));\n" +
                    "    float innerRadius = max(radius - thickness, 0.0);\n" +
                    "    vec2 innerHalf = max(rectHalf - thickness, vec2(0.0));\n" +
                    "    float inner = 1.0 - smoothstep(0.0, 1.0, roundSDF(pos, innerHalf - innerRadius - 0.25, innerRadius));\n" +
                    "    float outlineAlpha = outer * (1.0 - inner);\n" +
                    "    vec4 gradColor = mix(color1, color2, gl_TexCoord[0].st.x);\n" +
                    "    gl_FragColor = vec4(gradColor.rgb, gradColor.a * outlineAlpha);\n" +
                    "}";
    private static final String ROUNDED_TEXTURE_SRC =
            "#version 120\n" +
                    "uniform sampler2D tex;\n" +
                    "uniform vec2 location, rectSize, screenSize;\n" +
                    "uniform vec4 color;\n" +
                    "uniform float radius;\n" +
                    "float roundSDF(vec2 p, vec2 b, float r) { return length(max(abs(p) - b, 0.0)) - r; }\n" +
                    "void main() {\n" +
                    "    vec2 screenPos = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y);\n" +
                    "    vec2 rectHalf = rectSize * 0.5;\n" +
                    "    vec2 pos = screenPos - location - rectHalf;\n" +
                    "    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, roundSDF(pos, rectHalf - radius - 0.25, radius));\n" +
                    "    vec4 texColor = texture2D(tex, gl_TexCoord[0].st) * color;\n" +
                    "    gl_FragColor = vec4(texColor.rgb, texColor.a * smoothedAlpha);\n" +
                    "}";
    private static final ShaderUtils roundedShader = new ShaderUtils(ROUNDED_RECT_SRC, true);
    private static final ShaderUtils multiRadiusShader = new ShaderUtils(MULTI_RADIUS_SRC, true);
    private static final ShaderUtils roundedGradientShader = new ShaderUtils(ROUNDED_GRADIENT_SRC, true);
    private static final ShaderUtils roundedGradientOutlineShader = new ShaderUtils(ROUNDED_GRADIENT_OUTLINE_SRC, true);
    private static final ShaderUtils roundedTextureShader = new ShaderUtils(ROUNDED_TEXTURE_SRC, true);
    private static Minecraft mc;
    private static Frustum cameraFrustum;
    private static IntBuffer viewportBuffer;
    private static FloatBuffer modelViewBuffer;
    private static FloatBuffer projectionBuffer;
    private static FloatBuffer vectorBuffer;
    private static int cachedScaleDisplayWidth = -1;
    private static int cachedScaleDisplayHeight = -1;
    private static int cachedScaleGuiScale = -1;
    private static boolean cachedScaleUnicode = false;
    private static int cachedScaleFactor = 1;
    private static int cachedScaledWidth = 0;
    private static int cachedScaledHeight = 0;
    private static Map<Integer, EnchantmentData> enchantmentMap;

    static {
        RenderUtil.mc = Minecraft.getMinecraft();
        RenderUtil.cameraFrustum = new Frustum();
        RenderUtil.viewportBuffer = GLAllocation.createDirectIntBuffer(16);
        RenderUtil.modelViewBuffer = GLAllocation.createDirectFloatBuffer(16);
        RenderUtil.projectionBuffer = GLAllocation.createDirectFloatBuffer(16);
        RenderUtil.vectorBuffer = GLAllocation.createDirectFloatBuffer(4);
        RenderUtil.enchantmentMap = new EnchantmentMap();
    }

    private static ChatColors getColorForLevel(int currentLevel, int maxLevel) {
        if (currentLevel > maxLevel) {
            return ChatColors.LIGHT_PURPLE;
        }
        if (currentLevel == maxLevel) {
            return ChatColors.RED;
        }
        switch (currentLevel) {
            case 1: {
                return ChatColors.AQUA;
            }
            case 2: {
                return ChatColors.GREEN;
            }
            case 3: {
                return ChatColors.YELLOW;
            }
            case 4: {
                return ChatColors.GOLD;
            }
        }
        return ChatColors.GRAY;
    }

    public static void setupOrientationMatrix(double x, double y, double z) {
        double renderPosX = mc.getRenderManager().getRenderPosX();
        double renderPosY = mc.getRenderManager().getRenderPosY();
        double renderPosZ = mc.getRenderManager().getRenderPosZ();
        GlStateManager.translate(x - renderPosX, y - renderPosY, z - renderPosZ);
    }

    public static void drawOutlinedString(String text, float x, float y) {
        String string2 = text.replaceAll("(?i)Â§[\\da-f]", "");
        RenderUtil.mc.fontRendererObj.drawString(string2, x + 1.0f, y, 0, false);
        RenderUtil.mc.fontRendererObj.drawString(string2, x - 1.0f, y, 0, false);
        RenderUtil.mc.fontRendererObj.drawString(string2, x, y + 1.0f, 0, false);
        RenderUtil.mc.fontRendererObj.drawString(string2, x, y - 1.0f, 0, false);
        RenderUtil.mc.fontRendererObj.drawString(text, x, y, -1, false);
    }

    public static void renderEnchantmentText(ItemStack itemStack, float x, float y, float scale) {
        NBTTagList nBTTagList;
        nBTTagList = itemStack.getItem() == Items.enchanted_book ? Items.enchanted_book.getEnchantments(itemStack) : itemStack.getEnchantmentTagList();
        if (nBTTagList != null) {
            for (int i = 0; i < nBTTagList.tagCount(); ++i) {
                EnchantmentData enchantmentData = enchantmentMap.get(nBTTagList.getCompoundTagAt(i).getInteger("id"));
                if (enchantmentData == null) {
                    continue;
                }
                short s = nBTTagList.getCompoundTagAt(i).getShort("lvl");
                ChatColors chatColors = RenderUtil.getColorForLevel(s, enchantmentData.maxLevel);
                RenderUtil.drawOutlinedString(ChatColors.formatColor(String.format("&r%s%s%d&r", enchantmentData.shortName, chatColors, (int) s)), x * (1.0f / scale), (y + (float) i * 4.0f) * (1.0f / scale));
            }
        }
    }

    public static void drawImage(ResourceLocation image, float x, float y, float width, float height, int color) {
        mc.getTextureManager().bindTexture(image);
        setColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    public static void drawImage(ResourceLocation image, float x, float y, float x2, float y2, int color1, int color2, int color3, int color4) {
        mc.getTextureManager().bindTexture(image);
        GL11.glBegin(GL11.GL_QUADS);
        setColor(color1);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        setColor(color2);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y2);
        setColor(color3);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x2, y2);
        setColor(color4);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x2, y);
        GL11.glEnd();
        GlStateManager.resetColor();
    }

    public static void renderItemInGUI(ItemStack itemStack, int x, int y) {
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        RenderUtil.mc.getRenderItem().zLevel = -150.0f;
        mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, x, y);
        mc.getRenderItem().renderItemOverlays(RenderUtil.mc.fontRendererObj, itemStack, x, y);
        RenderUtil.mc.getRenderItem().zLevel = 0.0f;
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5f, 0.5f, 0.5f);
        GlStateManager.disableDepth();
        RenderUtil.renderEnchantmentText(itemStack, x, y, 0.5f);
        GlStateManager.enableDepth();
        GlStateManager.scale(2.0f, 2.0f, 2.0f);
        GlStateManager.popMatrix();
    }

    public static void renderItemAndEffectIntoGui3D(ItemStack stack, int xPos, int yPos) {
        if (stack == null) {
            return;
        }

        GlStateManager.pushMatrix();
        prepareGuiItemRenderState();
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        mc.getRenderItem().zLevel = -150.0f;
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, xPos, yPos);
        mc.getRenderItem().zLevel = 0.0f;
        GlStateManager.popMatrix();
        RenderHelper.disableStandardItemLighting();
        prepareGuiTextureRenderState();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static void drawDurabilityBar(int xPos, int yPos, float durabilityRatio) {
        if (durabilityRatio < 0) durabilityRatio = 0;
        if (durabilityRatio > 1) durabilityRatio = 1;

        int barWidth = (int) (durabilityRatio * 13);

        GlStateManager.disableTexture2D();
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(xPos + 2, yPos + 15, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
        wr.pos(xPos + 2, yPos + 16, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
        wr.pos(xPos + 15, yPos + 16, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
        wr.pos(xPos + 15, yPos + 15, 0).color(0.0F, 0.0F, 0.0F, 1.0F).endVertex();
        tess.draw();

        float r, g, b;
        if (durabilityRatio <= 0.3F) {
            r = 1;
            g = 0;
            b = 0;
        } else if (durabilityRatio <= 0.6F) {
            r = 1;
            g = 1;
            b = 0;
        } else {
            r = 0;
            g = 1;
            b = 0;
        }

        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(xPos + 2, yPos + 15, 0).color(r, g, b, 1.0F).endVertex();
        wr.pos(xPos + 2, yPos + 16, 0).color(r, g, b, 1.0F).endVertex();
        wr.pos(xPos + 2 + barWidth, yPos + 16, 0).color(r, g, b, 1.0F).endVertex();
        wr.pos(xPos + 2 + barWidth, yPos + 15, 0).color(r, g, b, 1.0F).endVertex();
        tess.draw();

        GlStateManager.enableTexture2D();
    }

    private static void prepareGuiItemRenderState() {
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void prepareGuiTextureRenderState() {
        GlStateManager.disableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    public static void renderPotionEffect(PotionEffect potionEffect, int x, int y) {
        int n3 = Potion.potionTypes[potionEffect.getPotionID()].getStatusIconIndex();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.clear(256);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0f, 1.0f, -0.01f);
        mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(x, y, n3 % 8 * 18, 198 + (double) n3 / 8 * 18, 18, 18, 256.0f, 256.0f);
        GlStateManager.popMatrix();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    public static void drawRect(double left, double top, double right, double bottom, int color) {
        float f3 = (color >> 24 & 255) / 255.0F;
        float f = (color >> 16 & 255) / 255.0F;
        float f1 = (color >> 8 & 255) / 255.0F;
        float f2 = (color & 255) / 255.0F;
        GlStateManager.pushMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(f, f1, f2, f3);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION);
        worldrenderer.pos(left, bottom, 0.0D).endVertex();
        worldrenderer.pos(right, bottom, 0.0D).endVertex();
        worldrenderer.pos(right, top, 0.0D).endVertex();
        worldrenderer.pos(left, top, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public static void drawRect3D(float x1, float y1, float x2, float y2, int color) {
        if (color == 0) {
            return;
        }
        RenderUtil.setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = 0; i < 2; ++i) {
            GL11.glVertex2f(x1, y1);
            GL11.glVertex2f(x1, y2);
            GL11.glVertex2f(x2, y2);
            GL11.glVertex2f(x2, y1);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawAxisAlignedBB(AxisAlignedBB axisAlignedBB, boolean filled, int color) {
        enableRenderState();
        setColor(color);
        if (filled) {
            drawFilledBoundingBox(axisAlignedBB);
        } else {
            drawBoundingBox(axisAlignedBB);
        }
        disableRenderState();
    }

    public static void drawFilledBoundingBox(AxisAlignedBB bb) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION);
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        tessellator.draw();
    }

    public static void drawBoundingBox(AxisAlignedBB bb) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(3, DefaultVertexFormats.POSITION);
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        tessellator.draw();
        worldRenderer.begin(3, DefaultVertexFormats.POSITION);
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        tessellator.draw();
        worldRenderer.begin(1, DefaultVertexFormats.POSITION);
        worldRenderer.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        worldRenderer.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        tessellator.draw();
    }

    public static void drawOutlineRect(float x1, float y1, float x2, float y2, float lineWidth, int backgroundColor, int lineColor) {
        RenderUtil.drawRect(0.0f, 0.0f, x2, 27.0f, backgroundColor);
        if (lineColor == 0) {
            return;
        }
        RenderUtil.setColor(lineColor);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawESPBox2D(float left, float top, float right, float bottom, float lineWidth, int color) {
        if (color == 0) {
            return;
        }
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);

        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, bottom);

        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);

        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);

        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine(float x1, float y1, float x2, float y2, float lineWidth, int color) {
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawLine3D(Vec3 start, double endX, double endY, double endZ, float red, float green, float blue, float alpha, float lineWidth) {
        GlStateManager.pushMatrix();
        GlStateManager.color(red, green, blue, alpha);
        boolean bl = RenderUtil.mc.gameSettings.viewBobbing;
        RenderUtil.mc.gameSettings.viewBobbing = false;
        RenderUtil.mc.entityRenderer.setupCameraTransform(RenderUtil.mc.timer.renderPartialTicks, 2);
        RenderUtil.mc.gameSettings.viewBobbing = bl;
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(start.xCoord, start.yCoord, start.zCoord);
        GL11.glVertex3d(endX - mc.getRenderManager().getRenderPosX(), endY - mc.getRenderManager().getRenderPosY(), endZ - mc.getRenderManager().getRenderPosZ());
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
        GlStateManager.popMatrix();
    }

    public static void drawArrow(float centerX, float centerY, float angle, float length, float lineWidth, int color) {
        float f6 = angle + (float) Math.toRadians(45.0);
        float f7 = angle - (float) Math.toRadians(45.0);
        RenderUtil.setColor(color);
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f6), centerY + length * (float) Math.sin(f6));
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f7), centerY + length * (float) Math.sin(f7));
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawTriangle(float centerX, float centerY, float angle, float length, int color) {
        float f5 = angle + (float) Math.toRadians(26.25);
        float f6 = angle - (float) Math.toRadians(26.25);
        RenderUtil.setColor(color);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(9);
        GL11.glVertex2f(centerX, centerY);
        GL11.glVertex2f(centerX + length * (float) Math.cos(f5), centerY + length * (float) Math.sin(f5));
        GL11.glVertex2f(centerX + length * (float) Math.cos(f6), centerY + length * (float) Math.sin(f6));
        GL11.glEnd();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        GlStateManager.resetColor();
    }

    public static void drawFramebuffer(Framebuffer framebuffer) {
        updateScaledResolutionCache();
        GlStateManager.bindTexture(framebuffer.framebufferTexture);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0.0, 1.0);
        GL11.glVertex2d(0.0, 0.0);
        GL11.glTexCoord2d(0.0, 0.0);
        GL11.glVertex2d(0.0, cachedScaledHeight);
        GL11.glTexCoord2d(1.0, 0.0);
        GL11.glVertex2d(cachedScaledWidth, cachedScaledHeight);
        GL11.glTexCoord2d(1.0, 1.0);
        GL11.glVertex2d(cachedScaledWidth, 0.0);
        GL11.glEnd();
    }

    public static void drawCircle(double centerX, double centerY, double centerZ, double radius, int segments, int color) {
        RenderUtil.setColor(color);
        GL11.glLineWidth(3.0f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i <= segments; ++i) {
            double d5 = (double) i * (Math.PI * 2 / (double) segments);
            GL11.glVertex3d(centerX + Math.cos(d5) * radius, centerY, centerZ + Math.sin(d5) * radius);
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
        GlStateManager.resetColor();
    }

    public static void drawEntityCircle(Entity entity, double radius, int segments, int color) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, RenderUtil.mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosX();
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, RenderUtil.mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosY();
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, RenderUtil.mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosZ();
        RenderUtil.drawCircle(d2, d3, d4, radius, segments, color);
    }

    public static void drawFilledBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, 63).endVertex();
        tessellator.draw();
    }

    public static void drawFilledBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue, int alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        worldRenderer.pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).color(red, green, blue, alpha).endVertex();
        tessellator.draw();
    }

    public static void drawBoundingBox(AxisAlignedBB axisAlignedBB, int red, int green, int blue, int alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        RenderGlobal.drawOutlinedBoundingBox(axisAlignedBB, red, green, blue, alpha);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0f);
    }

    public static void drawEntityBox(Entity entity, int red, int green, int blue) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, RenderUtil.mc.timer.renderPartialTicks);
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, RenderUtil.mc.timer.renderPartialTicks);
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, RenderUtil.mc.timer.renderPartialTicks);
        RenderUtil.drawFilledBox(entity.getEntityBoundingBox().expand(0.1f, 0.1f, 0.1f).offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ).offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ()), red, green, blue);
    }

    public static void drawEntityBoundingBox(Entity entity, int red, int green, int blue, int alpha, float lineWidth, double expand) {
        double d2 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, RenderUtil.mc.timer.renderPartialTicks);
        double d3 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, RenderUtil.mc.timer.renderPartialTicks);
        double d4 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, RenderUtil.mc.timer.renderPartialTicks);
        RenderUtil.drawBoundingBox(entity.getEntityBoundingBox().expand(expand, expand, expand).offset(d2 - entity.posX, d3 - entity.posY, d4 - entity.posZ).offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ()), red, green, blue, alpha, lineWidth);
    }

    public static void drawBlockBox(BlockPos blockPos, double height, int red, int green, int blue) {
        RenderUtil.drawFilledBox(new AxisAlignedBB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), (double) blockPos.getX() + 1.0, (double) blockPos.getY() + height, (double) blockPos.getZ() + 1.0).offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ()), red, green, blue);
    }

    public static void drawBlockBoundingBox(BlockPos blockPos, double height, int red, int green, int blue, int alpha, float lineWidth) {
        RenderUtil.drawBoundingBox(new AxisAlignedBB(blockPos.getX(), blockPos.getY(), blockPos.getZ(), (double) blockPos.getX() + 1.0, (double) blockPos.getY() + height, (double) blockPos.getZ() + 1.0).offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ()), red, green, blue, alpha, lineWidth);
    }

    public static void drawCornerESP(EntityPlayer entity, float red, float green, float blue) {
        float x = (float) (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosX());
        float y = (float) (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosY());
        float z = (float) (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosZ());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2.0F, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-0.098F, -0.098F, 0.098F);
        float width = (float) (26.6 * entity.width / 2.0);
        float height = 12.0F;
        GlStateManager.color(red, green, blue);
        draw3DRect(width, height - 1.0F, width - 4.0F, height);
        draw3DRect(-width, height - 1.0F, -width + 4.0F, height);
        draw3DRect(-width, height, -width + 1.0F, height - 4.0F);
        draw3DRect(width, height, width - 1.0F, height - 4.0F);
        draw3DRect(width, -height, width - 4.0F, -height + 1.0F);
        draw3DRect(-width, -height, -width + 4.0F, -height + 1.0F);
        draw3DRect(-width, -height + 1.0F, -width + 1.0F, -height + 4.0F);
        draw3DRect(width, -height + 1.0F, width - 1.0F, -height + 4.0F);
        GlStateManager.color(0.0F, 0.0F, 0.0F);
        draw3DRect(width, height, width - 4.0F, height + 0.2F);
        draw3DRect(-width, height, -width + 4.0F, height + 0.2F);
        draw3DRect(-width - 0.2F, height + 0.2F, -width, height - 4.0F);
        draw3DRect(width + 0.2F, height + 0.2F, width, height - 4.0F);
        draw3DRect(width + 0.2F, -height, width - 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width + 4.0F, -height - 0.2F);
        draw3DRect(-width - 0.2F, -height, -width, -height + 4.0F);
        draw3DRect(width + 0.2F, -height, width, -height + 4.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void drawFake2DESP(EntityPlayer entity, float red, float green, float blue) {
        float x = (float) (RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosX());
        float y = (float) (RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosY());
        float z = (float) (RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, mc.timer.renderPartialTicks) - mc.getRenderManager().getRenderPosZ());
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + entity.height / 2.0F, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(-0.1F, -0.1F, 0.1F);
        GlStateManager.color(red, green, blue);
        float width = (float) (23.3 * entity.width / 2.0);
        float height = 12.0F;
        draw3DRect(width, height, -width, height + 0.4F);
        draw3DRect(width, -height, -width, -height + 0.4F);
        draw3DRect(width, -height + 0.4F, width - 0.4F, height + 0.4F);
        draw3DRect(-width, -height + 0.4F, -width + 0.4F, height + 0.4F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    public static void draw3DRect(float x1, float y1, float x2, float y2) {
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x2, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x1, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }

    public static Vector4d projectToScreen(Entity entity, double screenScale) {
        double d3 = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, RenderUtil.mc.timer.renderPartialTicks);
        double d4 = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, RenderUtil.mc.timer.renderPartialTicks);
        double d5 = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, RenderUtil.mc.timer.renderPartialTicks);
        AxisAlignedBB bb = entity.getEntityBoundingBox().expand(0.1f, 0.1f, 0.1f).offset(d3 - entity.posX, d4 - entity.posY, d5 - entity.posZ);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);

        Vector4d bounds = null;
        double renderX = mc.getRenderManager().getRenderPosX();
        double renderY = mc.getRenderManager().getRenderPosY();
        double renderZ = mc.getRenderManager().getRenderPosZ();

        for (int i = 0; i < 8; i++) {
            double cornerX = (i & 2) == 0 ? bb.minX : bb.maxX;
            double cornerY = (i & 1) == 0 ? bb.minY : bb.maxY;
            double cornerZ = (i & 4) == 0 ? bb.minZ : bb.maxZ;
            if (!GLU.gluProject((float) (cornerX - renderX), (float) (cornerY - renderY), (float) (cornerZ - renderZ), modelViewBuffer, projectionBuffer, viewportBuffer, vectorBuffer)) {
                continue;
            }

            double projectedX = vectorBuffer.get(0) / screenScale;
            double projectedY = (Display.getHeight() - vectorBuffer.get(1)) / screenScale;
            double projectedZ = vectorBuffer.get(2);
            if (projectedZ < 0.0 || projectedZ >= 1.0) {
                continue;
            }

            if (bounds == null) {
                bounds = new Vector4d(projectedX, projectedY, projectedX, projectedY);
            } else {
                bounds.x = Math.min(projectedX, bounds.x);
                bounds.y = Math.min(projectedY, bounds.y);
                bounds.z = Math.max(projectedX, bounds.z);
                bounds.w = Math.max(projectedY, bounds.w);
            }
        }

        return bounds;
    }

    public static boolean isInViewFrustum(AxisAlignedBB axisAlignedBB, double expand) {
        cameraFrustum.setPosition(RenderUtil.mc.getRenderViewEntity().posX, RenderUtil.mc.getRenderViewEntity().posY, RenderUtil.mc.getRenderViewEntity().posZ);
        return cameraFrustum.isBoundingBoxInFrustum(axisAlignedBB.expand(expand, expand, expand));
    }

    public static void enableRenderState() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
    }

    public static void disableRenderState() {
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void setColor(int argb) {
        float f = (float) (argb >> 24 & 0xFF) / 255.0f;
        float f2 = (float) (argb >> 16 & 0xFF) / 255.0f;
        float f3 = (float) (argb >> 8 & 0xFF) / 255.0f;
        float f4 = (float) (argb & 0xFF) / 255.0f;
        GlStateManager.color(f2, f3, f4, f);
    }

    public static float lerpFloat(float current, float previous, float t) {
        return previous + (current - previous) * t;
    }

    public static double lerpDouble(double current, double previous, double t) {
        return previous + (current - previous) * t;
    }

    public static void scissor(double x, double y, double width, double height) {
        updateScaledResolutionCache();
        int scale = cachedScaleFactor;

        int scaledX = (int) (x * scale);
        int scaledY = (int) ((cachedScaledHeight - (y + height)) * scale);
        int scaledWidth = (int) (width * scale);
        int scaledHeight = (int) (height * scale);

        if (scaledWidth < 0 || scaledHeight < 0) {
            return;
        }

        GL11.glScissor(scaledX, scaledY, scaledWidth, scaledHeight);
    }

    public static void renderPlayerHead(EntityLivingBase entity, float x, float y, float size) {
        renderPlayerHead(entity, x, y, size, Color.WHITE);
    }

    public static void renderPlayerHead(EntityLivingBase entity, float x, float y, float size, Color color) {
        ResourceLocation skin = null;
        if (entity instanceof EntityPlayer player && mc.getNetHandler() != null) {
            if (mc.getNetHandler().getPlayerInfo(player.getName()) != null) {
                skin = mc.getNetHandler().getPlayerInfo(player.getName()).getLocationSkin();
            }
        }
        if (skin == null) {
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.color(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
        mc.getTextureManager().bindTexture(skin);
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8.0F, 8.0F, 8, 8, (int) size, (int) size, 64.0F, 64.0F);
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 40.0F, 8.0F, 8, 8, (int) size, (int) size, 64.0F, 64.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void renderRoundedPlayerHead(EntityLivingBase entity, float x, float y, float size, float radius, Color color) {
        ResourceLocation skin = null;
        if (entity instanceof EntityPlayer player && mc.getNetHandler() != null) {
            if (mc.getNetHandler().getPlayerInfo(player.getName()) != null) {
                skin = mc.getNetHandler().getPlayerInfo(player.getName()).getLocationSkin();
            }
        }
        if (skin == null) {
            return;
        }

        radius = Math.min(radius, size / 2.0F);

        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.0F);

        mc.getTextureManager().bindTexture(skin);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        roundedTextureShader.init();
        setupRoundedRectUniforms(roundedTextureShader, x, y, size, size);

        int sf = getScaleFactor();
        roundedTextureShader.setUniformi("tex", 0);
        roundedTextureShader.setUniformf("radius", radius * sf);
        roundedTextureShader.setUniformf(
                "color",
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                color.getAlpha() / 255.0F
        );

        drawTexturedQuads(x - 1.0F, y - 1.0F, size + 2.0F, size + 2.0F, 8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F);
        drawTexturedQuads(x - 1.0F, y - 1.0F, size + 2.0F, size + 2.0F, 40.0F / 64.0F, 8.0F / 64.0F, 48.0F / 64.0F, 16.0F / 64.0F);

        roundedTextureShader.unload();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    public static void renderEquipment(EntityPlayer player, float x, float y, float scale) {
        List<ItemStack> items = new ArrayList<>();
        if (player.getHeldItem() != null) {
            items.add(player.getHeldItem());
        }
        for (int i = 3; i >= 0; --i) {
            ItemStack armor = player.inventory.armorInventory[i];
            if (armor != null) {
                items.add(armor);
            }
        }
        if (items.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        RenderHelper.enableGUIStandardItemLighting();
        float drawX = x / scale;
        float drawY = y / scale;
        for (ItemStack item : items) {
            renderItemInGUI(item, (int) drawX, (int) drawY);
            drawX += 16.0F;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    public static void drawSkeetRect(float x, float y, float width, float height) {
        drawRect(x, y, x + width, y + height, SKEET_OUTER_COLOR);
        drawRect(x + 1.0F, y + 1.0F, x + width - 1.0F, y + height - 1.0F, SKEET_MIDDLE_COLOR);
        drawRect(x + 2.0F, y + 2.0F, x + width - 2.0F, y + height - 2.0F, SKEET_INNER_COLOR);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, boolean blur, Color color) {
        if (width <= 0.0F || height <= 0.0F || color.getAlpha() <= 0) {
            return;
        }
        drawRoundedRect(
                x,
                y,
                width,
                height,
                radius,
                blur,
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                color.getAlpha() / 255.0F
        );
    }

    private static void drawRoundedRect(float x, float y, float width, float height, float radius, boolean blur, int color) {
        if (width <= 0.0F || height <= 0.0F || ((color >> 24) & 0xFF) <= 0) {
            return;
        }
        drawRoundedRect(
                x,
                y,
                width,
                height,
                radius,
                blur,
                (color >> 16 & 255) / 255.0F,
                (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F,
                (color >> 24 & 255) / 255.0F
        );
    }

    private static void drawRoundedRect(float x, float y, float width, float height, float radius, boolean blur, float red, float green, float blue, float alpha) {
        radius = Math.min(radius, Math.min(width, height) / 2.0F);

        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.0F);

        roundedShader.init();
        setupRoundedRectUniforms(roundedShader, x, y, width, height);

        int sf = getScaleFactor();
        roundedShader.setUniformf("radius", radius * sf);
        roundedShader.setUniformi("blur", blur ? 1 : 0);
        roundedShader.setUniformf(
                "color",
                red,
                green,
                blue,
                alpha
        );

        drawQuads(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F);

        roundedShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomLeft, float radiusBottomRight, int color) {
        if (width <= 0.0F || height <= 0.0F || ((color >> 24) & 0xFF) <= 0) {
            return;
        }

        float maxRadius = Math.min(width, height) / 2.0F;
        radiusTopLeft = Math.min(radiusTopLeft, maxRadius);
        radiusTopRight = Math.min(radiusTopRight, maxRadius);
        radiusBottomLeft = Math.min(radiusBottomLeft, maxRadius);
        radiusBottomRight = Math.min(radiusBottomRight, maxRadius);

        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.0F);

        multiRadiusShader.init();
        setupRoundedRectUniforms(multiRadiusShader, x, y, width, height);

        int sf = getScaleFactor();
        multiRadiusShader.setUniformf("radiusTopLeft", radiusTopLeft * sf);
        multiRadiusShader.setUniformf("radiusTopRight", radiusTopRight * sf);
        multiRadiusShader.setUniformf("radiusBottomLeft", radiusBottomLeft * sf);
        multiRadiusShader.setUniformf("radiusBottomRight", radiusBottomRight * sf);

        float alpha = (color >> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        multiRadiusShader.setUniformf("color", red, green, blue, alpha);

        drawQuads(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F);

        multiRadiusShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawRoundedRectangle(float x, float y, float x2, float y2, float radius, final int color) {
        if (x2 <= x || y2 <= y) {
            return;
        }
        drawRoundedRect(x, y, x2 - x, y2 - y, radius, false, color);
    }

    public static void drawRoundedGradientRect(float x, float y, float x2, float y2, float radius, final int n6, final int n7, final int n8, final int n9) {
        if (x2 <= x || y2 <= y) {
            return;
        }

        float width = x2 - x;
        float height = y2 - y;
        radius = Math.min(radius, Math.min(width, height) / 2.0F);

        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.0F);

        roundedGradientShader.init();
        setupRoundedRectUniforms(roundedGradientShader, x, y, width, height);

        int sf = getScaleFactor();
        roundedGradientShader.setUniformf("radius", radius * sf);
        setShaderColor(roundedGradientShader, "color1", n6);
        setShaderColor(roundedGradientShader, "color2", n7);
        setShaderColor(roundedGradientShader, "color3", n9);
        setShaderColor(roundedGradientShader, "color4", n8);

        drawQuads(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F);

        roundedGradientShader.unload();
        GlStateManager.disableBlend();
    }

    public static void drawGradientRect(int left, int top, float right, int bottom, int startColor, int endColor) {
        float startAlpha = (startColor >> 24 & 255) / 255.0F;
        float startRed = (startColor >> 16 & 255) / 255.0F;
        float startGreen = (startColor >> 8 & 255) / 255.0F;
        float startBlue = (startColor & 255) / 255.0F;
        float endAlpha = (endColor >> 24 & 255) / 255.0F;
        float endRed = (endColor >> 16 & 255) / 255.0F;
        float endGreen = (endColor >> 8 & 255) / 255.0F;
        float endBlue = (endColor & 255) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos(right, top, 0.0D).color(startRed, startGreen, startBlue, startAlpha).endVertex();
        worldrenderer.pos(left, top, 0.0D).color(startRed, startGreen, startBlue, startAlpha).endVertex();
        worldrenderer.pos(left, bottom, 0.0D).color(endRed, endGreen, endBlue, endAlpha).endVertex();
        worldrenderer.pos(right, bottom, 0.0D).color(endRed, endGreen, endBlue, endAlpha).endVertex();
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void drawGradientSideways(double left, double top, double right, double bottom, int startColor, int endColor) {
        float startAlpha = (startColor >> 24 & 255) / 255.0F;
        float startRed = (startColor >> 16 & 255) / 255.0F;
        float startGreen = (startColor >> 8 & 255) / 255.0F;
        float startBlue = (startColor & 255) / 255.0F;
        float endAlpha = (endColor >> 24 & 255) / 255.0F;
        float endRed = (endColor >> 16 & 255) / 255.0F;
        float endGreen = (endColor >> 8 & 255) / 255.0F;
        float endBlue = (endColor & 255) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(left, top, 0.0D).color(startRed, startGreen, startBlue, startAlpha).endVertex();
        worldRenderer.pos(left, bottom, 0.0D).color(startRed, startGreen, startBlue, startAlpha).endVertex();
        worldRenderer.pos(right, bottom, 0.0D).color(endRed, endGreen, endBlue, endAlpha).endVertex();
        worldRenderer.pos(right, top, 0.0D).color(endRed, endGreen, endBlue, endAlpha).endVertex();
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void glColor(final int n) {
        GL11.glColor4f((float) (n >> 16 & 0xFF) / 255.0f, (float) (n >> 8 & 0xFF) / 255.0f, (float) (n & 0xFF) / 255.0f, (float) (n >> 24 & 0xFF) / 255.0f);
    }

    public static void drawRoundedGradientOutlinedRectangle(
            float startX,
            float startY,
            float endX,
            float endY,
            final float cornerRadius,
            final int fillColor,
            final int outlineColor1,
            final int outlineColor2) {
        if (endX <= startX || endY <= startY) {
            return;
        }

        float outlineWidth = 1.5F;
        if (((fillColor >> 24) & 0xFF) > 0) {
            drawRoundedRectangle(startX, startY, endX, endY, cornerRadius, fillColor);
        }

        drawRoundedGradientOutline(startX, startY, endX - startX, endY - startY, cornerRadius, outlineWidth, outlineColor1, outlineColor2);
    }

    private static void drawRoundedGradientOutline(float x, float y, float width, float height, float radius, float thickness, int color1, int color2) {
        if (width <= 0.0F || height <= 0.0F || thickness <= 0.0F) {
            return;
        }

        radius = Math.min(radius, Math.min(width, height) / 2.0F);
        thickness = Math.min(thickness, Math.min(width, height) / 2.0F);

        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(516, 0.0F);

        roundedGradientOutlineShader.init();
        setupRoundedRectUniforms(roundedGradientOutlineShader, x, y, width, height);

        int sf = getScaleFactor();
        roundedGradientOutlineShader.setUniformf("radius", radius * sf);
        roundedGradientOutlineShader.setUniformf("thickness", thickness * sf);
        setShaderColor(roundedGradientOutlineShader, "color1", color1);
        setShaderColor(roundedGradientOutlineShader, "color2", color2);

        drawQuads(x - 1.0F, y - 1.0F, width + 2.0F, height + 2.0F);

        roundedGradientOutlineShader.unload();
        GlStateManager.disableBlend();
    }

    public static int mergeAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | alpha << 24;
    }

    public static int darkenColor(int color, double percent) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        percent = (100 - percent) / 100;

        red = (int) (red * percent);
        green = (int) (green * percent);
        blue = (int) (blue * percent);

        red = clamp(red);
        green = clamp(green);
        blue = clamp(blue);

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static int clamp(int n) {
        if (n > 255) {
            return 255;
        }
        if (n < 0) {
            return 0;
        }
        return n;
    }

    public static void resetColor() {
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void drawRoundedRectWithCorners(double x, double y, double x1, double y1, int color, double radius,
                                                  boolean leftTop, boolean rightTop, boolean leftBot, boolean rightBot) {
        if (x1 <= x || y1 <= y) {
            return;
        }

        radius = Math.min(radius, Math.min(x1 - x, y1 - y) / 2.0);

        GL11.glPushMatrix();

        drawRect(x + radius, y, x1 - radius, y1, color);
        drawRect(x, y + (leftTop ? radius : 0.0), x + radius, y1 - (leftBot ? radius : 0.0), color);
        drawRect(x1 - radius, y + (rightTop ? radius : 0.0), x1, y1 - (rightBot ? radius : 0.0), color);

        if (leftTop) {
            drawCirclePart(x + radius, y + radius, radius, 270, 360, color);
        }
        if (rightTop) {
            drawCirclePart(x1 - radius, y + radius, radius, 0, 90, color);
        }
        if (leftBot) {
            drawCirclePart(x + radius, y1 - radius, radius, 180, 270, color);
        }
        if (rightBot) {
            drawCirclePart(x1 - radius, y1 - radius, radius, 90, 180, color);
        }

        GL11.glPopMatrix();
    }

    private static void drawCirclePart(double x, double y, double radius, double from, double to, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        setColor(color);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(x, y);
        for (double i = from; i <= to; i += 2.0) {
            GL11.glVertex2d(
                    x + Math.sin(i * Math.PI / 180.0) * radius,
                    y - Math.cos(i * Math.PI / 180.0) * radius
            );
        }
        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static int getScaleFactor() {
        updateScaledResolutionCache();
        return cachedScaleFactor;
    }

    private static void setupRoundedRectUniforms(ShaderUtils shader, float x, float y, float width, float height) {
        int sf = getScaleFactor();
        float locX = x * sf;
        float locY = y * sf;

        shader.setUniformf("location", locX, locY);
        shader.setUniformf("rectSize", width * sf, height * sf);
        shader.setUniformf("screenSize", mc.displayWidth, mc.displayHeight);
    }

    private static void updateScaledResolutionCache() {
        int displayWidth = mc.displayWidth;
        int displayHeight = mc.displayHeight;
        int guiScale = mc.gameSettings.guiScale;
        boolean unicode = mc.isUnicode();
        if (displayWidth == cachedScaleDisplayWidth
                && displayHeight == cachedScaleDisplayHeight
                && guiScale == cachedScaleGuiScale
                && unicode == cachedScaleUnicode) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        cachedScaleDisplayWidth = displayWidth;
        cachedScaleDisplayHeight = displayHeight;
        cachedScaleGuiScale = guiScale;
        cachedScaleUnicode = unicode;
        cachedScaleFactor = sr.getScaleFactor();
        cachedScaledWidth = sr.getScaledWidth();
        cachedScaledHeight = sr.getScaledHeight();
    }

    private static void setShaderColor(ShaderUtils shader, String uniform, int color) {
        shader.setUniformf(
                uniform,
                (color >> 16 & 255) / 255.0F,
                (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F,
                (color >> 24 & 255) / 255.0F
        );
    }

    private static void drawQuads(float x, float y, float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
    }

    private static void drawTexturedQuads(float x, float y, float width, float height, float u1, float v1, float u2, float v2) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u1, v2);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(u2, v2);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(u2, v1);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
    }

    public static void setAlphaLimit(float limit) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL_GREATER, (float) (limit * .01));
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
        return createFrameBuffer(framebuffer, false);
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer, boolean depth) {
        boolean created = false;
        if (needsNewFramebuffer(framebuffer)) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
            framebuffer.setFramebufferFilter(GL_LINEAR);
            created = true;
        }
        if (created) {
            glBindTexture(GL_TEXTURE_2D, framebuffer.framebufferTexture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        return framebuffer;
    }

    private static boolean needsNewFramebuffer(Framebuffer framebuffer) {
        return framebuffer == null || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight;
    }

    public static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static Color interpolateColorC(Color color1, Color color2, float amount) {
        amount = Math.min(1, Math.max(0, amount));
        return new Color(interpolateInt(color1.getRed(), color2.getRed(), amount),
                interpolateInt(color1.getGreen(), color2.getGreen(), amount),
                interpolateInt(color1.getBlue(), color2.getBlue(), amount),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    private static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
        return (int) (oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float[] project2D(float x, float y, float z, int scaleFactor) {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelViewBuffer);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projectionBuffer);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);

        if (GLU.gluProject(x, y, z, modelViewBuffer, projectionBuffer, viewportBuffer, vectorBuffer)) {
            updateScaledResolutionCache();
            return new float[]{
                    vectorBuffer.get(0) / scaleFactor,
                    (cachedScaledHeight - vectorBuffer.get(1) / scaleFactor),
                    vectorBuffer.get(2)
            };
        }
        return null;
    }

    public record EnchantmentData(String shortName, int maxLevel) {
    }

    static final class EnchantmentMap extends HashMap<Integer, EnchantmentData> {
        EnchantmentMap() {
            this.put(0, new EnchantmentData("Pr", 4));
            this.put(1, new EnchantmentData("Fp", 4));
            this.put(2, new EnchantmentData("Ff", 4));
            this.put(3, new EnchantmentData("Bp", 4));
            this.put(4, new EnchantmentData("Pp", 4));
            this.put(5, new EnchantmentData("Re", 3));
            this.put(6, new EnchantmentData("Aq", 1));
            this.put(7, new EnchantmentData("Th", 3));
            this.put(8, new EnchantmentData("Ds", 3));
            this.put(16, new EnchantmentData("Sh", 5));
            this.put(17, new EnchantmentData("Sm", 5));
            this.put(18, new EnchantmentData("BoA", 5));
            this.put(19, new EnchantmentData("Kb", 2));
            this.put(20, new EnchantmentData("Fa", 2));
            this.put(21, new EnchantmentData("Lo", 3));
            this.put(32, new EnchantmentData("Ef", 5));
            this.put(33, new EnchantmentData("St", 1));
            this.put(34, new EnchantmentData("Ub", 3));
            this.put(35, new EnchantmentData("Fo", 3));
            this.put(48, new EnchantmentData("Po", 5));
            this.put(49, new EnchantmentData("Pu", 2));
            this.put(50, new EnchantmentData("Fl", 1));
            this.put(51, new EnchantmentData("Inf", 1));
            this.put(61, new EnchantmentData("LoS", 3));
            this.put(62, new EnchantmentData("Lu", 3));
        }
    }
}
