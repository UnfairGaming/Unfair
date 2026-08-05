package net.minecraft.client.gui;

import cn.unfair.management.altmanager.AltManagerGui;
import cn.unfair.ui.mainmenu.MainMenuStyle;
import cn.unfair.ui.mainmenu.SilentMenuButton;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.optifine.CustomPanorama;
import net.optifine.CustomPanoramaProperties;
import org.apache.commons.io.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.glu.Project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.*;

public class GuiMainMenu extends GuiScreen implements GuiYesNoCallback {
    private static final Logger logger = LogManager.getLogger();
    private static final Random RANDOM = new Random();
    private static final int ALT_MANAGER_BUTTON_ID = 9999;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 23;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_RADIUS = 7;
    private static final String[] CUSTOM_SPLASHES = {
            "Math.random() bypass",
            "Math.abs() bypass",
            "System.out.println() bypass",
            "Fuck u",
            "Hi",
            "Unfair",
            "Minecraft 1.8.9",
            "Fan Dong Pai"
    };

    /**
     * Counts the number of screen updates.
     */
    private final float updateCounter;

    /**
     * The splash message.
     */
    private String splashText;

    public void setSplashText(String splashText) {
        this.splashText = splashText;
    }

    /**
     * Timer used to rotate the panorama, increases every tick.
     */
    private int panoramaTimer;

    /**
     * Texture allocated for the current viewport of the main menu's panorama background.
     */
    private DynamicTexture viewportTexture;
    private final boolean field_175375_v = true;

    /**
     * The Object object utilized as a thread lock when performing non thread-safe operations
     */
    private final Object threadLock = new Object();

    /**
     * OpenGL graphics card warning.
     */
    private String openGLWarning1;

    /**
     * OpenGL graphics card warning.
     */
    private String openGLWarning2;

    /**
     * Link to the Mojang Support about minimum requirements
     */
    private String openGLWarningLink;
    private static final ResourceLocation splashTexts = ResourceLocation.of("texts/splashes.txt");
    private static final ResourceLocation minecraftTitleTextures = ResourceLocation.of("textures/gui/title/minecraft.png");

    /**
     * An array of all the paths to the panorama pictures.
     */
    private static final ResourceLocation[] titlePanoramaPaths = new ResourceLocation[]{ResourceLocation.of("textures/gui/title/background/panorama_0.png"), ResourceLocation.of("textures/gui/title/background/panorama_1.png"), ResourceLocation.of("textures/gui/title/background/panorama_2.png"), ResourceLocation.of("textures/gui/title/background/panorama_3.png"), ResourceLocation.of("textures/gui/title/background/panorama_4.png"), ResourceLocation.of("textures/gui/title/background/panorama_5.png")};
    public static final String field_96138_a = "Please click " + EnumChatFormatting.UNDERLINE + "here" + EnumChatFormatting.RESET + " for more information.";
    private int field_92024_r;
    private int field_92023_s;
    private int field_92022_t;
    private int field_92021_u;
    private int field_92020_v;
    private int field_92019_w;
    private ResourceLocation backgroundTexture;
    private final FontRenderer titleFont = Fonts.urbanist.get(38.0F);
    private final FontRenderer splashFont = Fonts.interRegular.get(19.0F);
    private final FontRenderer buttonFont = Fonts.interRegular.get(16.0F);
    private final float[] animatedX = new float[6];
    private final float[] animatedY = new float[6];
    private final float[] animatedW = new float[6];
    private final float[] animatedH = new float[6];

    public GuiMainMenu() {
        this.openGLWarning2 = field_96138_a;
        this.splashText = "missingno";
        BufferedReader bufferedreader = null;

        try {
            List<String> list = Lists.newArrayList();
            bufferedreader = new BufferedReader(new InputStreamReader(Minecraft.getMinecraft().getResourceManager().getResource(splashTexts).getInputStream(), Charsets.UTF_8));
            String s;

            while ((s = bufferedreader.readLine()) != null) {
                s = s.trim();

                if (!s.isEmpty()) {
                    list.add(s);
                }
            }

            if (!list.isEmpty()) {
                while (true) {
                    this.splashText = list.get(RANDOM.nextInt(list.size()));

                    if (this.splashText.hashCode() != 125780783) {
                        break;
                    }
                }
            }
        } catch (IOException var12) {
        } finally {
            if (bufferedreader != null) {
                try {
                    bufferedreader.close();
                } catch (IOException var11) {
                }
            }
        }

        this.updateCounter = RANDOM.nextFloat();
        this.openGLWarning1 = "";

        if (!GLContext.getCapabilities().OpenGL20 && !OpenGlHelper.areShadersSupported()) {
            this.openGLWarning1 = I18n.format("title.oldgl1");
            this.openGLWarning2 = I18n.format("title.oldgl2");
            this.openGLWarningLink = "https://help.mojang.com/customer/portal/articles/325948?ref=game";
        }
    }

    /**
     * Called from the main game loop to update the screen.
     */
    public void updateScreen() {
        ++this.panoramaTimer;
    }

    /**
     * Returns true if this GUI should pause the game when it is displayed in single-player
     */
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Fired when a key is typed (except F11 which toggles full screen). This is the equivalent of
     * KeyListener.keyTyped(KeyEvent e). Args : character (character on the key), keyCode (lwjgl Keyboard key code)
     */
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
    }

    /**
     * Adds the buttons (and other controls) to the screen in question. Called when the GUI is displayed and when the
     * window resizes, the buttonList is cleared beforehand.
     */
    public void initGui() {
        this.updateCustomSplashText();
        this.buttonList.clear();
        this.buttonList.add(new SilentMenuButton(1, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Singleplayer"));
        this.buttonList.add(new SilentMenuButton(2, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Multiplayer"));
        this.buttonList.add(new SilentMenuButton(ALT_MANAGER_BUTTON_ID, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Alt manager"));
        this.buttonList.add(new SilentMenuButton(0, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Options"));
        this.buttonList.add(new SilentMenuButton(4, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Quit"));
        this.layoutButtons();
        this.resetButtonAnimation();

        synchronized (this.threadLock) {
            this.field_92023_s = (int) this.fontRendererObj.getStringWidth(this.openGLWarning1);
            this.field_92024_r = (int) this.fontRendererObj.getStringWidth(this.openGLWarning2);
            int k = Math.max(this.field_92023_s, this.field_92024_r);
            this.field_92022_t = (this.width - k) / 2;
            this.field_92021_u = this.buttonList.get(0).yPosition - 24;
            this.field_92020_v = this.field_92022_t + k;
            this.field_92019_w = this.field_92021_u + 24;
        }
    }

    /**
     * Called by the controls from the buttonList when activated. (Mouse pressed for buttons)
     */
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ALT_MANAGER_BUTTON_ID) {
            this.mc.displayGuiScreen(new AltManagerGui(this));
            return;
        }

        if (button.id == 0) {
            this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
        }

        if (button.id == 5) {
            this.mc.displayGuiScreen(new GuiLanguage(this, this.mc.gameSettings, this.mc.getLanguageManager()));
        }

        if (button.id == 1) {
            this.mc.displayGuiScreen(new GuiSelectWorld(this));
        }

        if (button.id == 2) {
            this.mc.displayGuiScreen(new GuiMultiplayer(this));
        }

        if (button.id == 4) {
            this.mc.shutdown();
        }
    }

    public void confirmClicked(boolean result, int id) {
        if (id == 13) {
            if (result) {
                try {
                    Class<?> oclass = Class.forName("java.awt.Desktop");
                    Object object = oclass.getMethod("getDesktop").invoke(null);
                    oclass.getMethod("browse", URI.class).invoke(object, new URI(this.openGLWarningLink));
                } catch (Throwable throwable) {
                    logger.error("Couldn't open link", throwable);
                }
            }

            this.mc.displayGuiScreen(this);
        }
    }

    /**
     * Draws the main menu panorama
     */
    private void drawPanorama(int p_73970_1_, int p_73970_2_, float p_73970_3_) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(120.0F, 1.0F, 0.05F, 10.0F);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(90.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        int i = 8;
        int j = 64;
        CustomPanoramaProperties custompanoramaproperties = CustomPanorama.getCustomPanoramaProperties();

        if (custompanoramaproperties != null) {
            j = custompanoramaproperties.getBlur1();
        }

        for (int k = 0; k < j; ++k) {
            GlStateManager.pushMatrix();
            float f = ((float) (k % i) / (float) i - 0.5F) / 64.0F;
            float f1 = ((float) (k / i) / (float) i - 0.5F) / 64.0F;
            float f2 = 0.0F;
            GlStateManager.translate(f, f1, f2);
            GlStateManager.rotate(MathHelper.sin(((float) this.panoramaTimer + p_73970_3_) / 400.0F) * 25.0F + 20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-((float) this.panoramaTimer + p_73970_3_) * 0.1F, 0.0F, 1.0F, 0.0F);

            for (int l = 0; l < 6; ++l) {
                GlStateManager.pushMatrix();

                if (l == 1) {
                    GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                }

                if (l == 2) {
                    GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                }

                if (l == 3) {
                    GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
                }

                if (l == 4) {
                    GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                }

                if (l == 5) {
                    GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
                }

                ResourceLocation[] aresourcelocation = titlePanoramaPaths;

                if (custompanoramaproperties != null) {
                    aresourcelocation = custompanoramaproperties.getPanoramaLocations();
                }

                this.mc.getTextureManager().bindTexture(aresourcelocation[l]);
                worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                int i1 = 255 / (k + 1);
                float f3 = 0.0F;
                worldrenderer.pos(-1.0D, -1.0D, 1.0D).tex(0.0D, 0.0D).color(255, 255, 255, i1).endVertex();
                worldrenderer.pos(1.0D, -1.0D, 1.0D).tex(1.0D, 0.0D).color(255, 255, 255, i1).endVertex();
                worldrenderer.pos(1.0D, 1.0D, 1.0D).tex(1.0D, 1.0D).color(255, 255, 255, i1).endVertex();
                worldrenderer.pos(-1.0D, 1.0D, 1.0D).tex(0.0D, 1.0D).color(255, 255, 255, i1).endVertex();
                tessellator.draw();
                GlStateManager.popMatrix();
            }

            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }

        worldrenderer.setTranslation(0.0D, 0.0D, 0.0D);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    /**
     * Rotate and blurs the skybox view in the main menu
     */
    private void rotateAndBlurSkybox(float p_73968_1_) {
        this.mc.getTextureManager().bindTexture(this.backgroundTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, 256, 256);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.colorMask(true, true, true, false);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        GlStateManager.disableAlpha();
        int i = 3;
        int j = 3;
        CustomPanoramaProperties custompanoramaproperties = CustomPanorama.getCustomPanoramaProperties();

        if (custompanoramaproperties != null) {
            j = custompanoramaproperties.getBlur2();
        }

        for (int k = 0; k < j; ++k) {
            float f = 1.0F / (float) (k + 1);
            int l = this.width;
            int i1 = this.height;
            float f1 = (float) (k - i / 2) / 256.0F;
            worldrenderer.pos(l, i1, this.zLevel).tex(0.0F + f1, 1.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
            worldrenderer.pos(l, 0.0D, this.zLevel).tex(1.0F + f1, 1.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
            worldrenderer.pos(0.0D, 0.0D, this.zLevel).tex(1.0F + f1, 0.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
            worldrenderer.pos(0.0D, i1, this.zLevel).tex(0.0F + f1, 0.0D).color(1.0F, 1.0F, 1.0F, f).endVertex();
        }

        tessellator.draw();
        GlStateManager.enableAlpha();
        GlStateManager.colorMask(true, true, true, true);
    }

    /**
     * Renders the skybox in the main menu
     */
    protected void renderSkybox(int p_73971_1_, int p_73971_2_, float p_73971_3_) {
        this.mc.getFramebuffer().unbindFramebuffer();
        GlStateManager.viewport(0, 0, 256, 256);
        this.drawPanorama(p_73971_1_, p_73971_2_, p_73971_3_);
        this.rotateAndBlurSkybox(p_73971_3_);
        int i = 3;
        CustomPanoramaProperties custompanoramaproperties = CustomPanorama.getCustomPanoramaProperties();

        if (custompanoramaproperties != null) {
            i = custompanoramaproperties.getBlur3();
        }

        for (int j = 0; j < i; ++j) {
            this.rotateAndBlurSkybox(p_73971_3_);
            this.rotateAndBlurSkybox(p_73971_3_);
        }

        this.mc.getFramebuffer().bindFramebuffer(true);
        GlStateManager.viewport(0, 0, this.mc.displayWidth, this.mc.displayHeight);
        float f2 = this.width > this.height ? 120.0F / (float) this.width : 120.0F / (float) this.height;
        float f = (float) this.height * f2 / 256.0F;
        float f1 = (float) this.width * f2 / 256.0F;
        int k = this.width;
        int l = this.height;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        worldrenderer.pos(0.0D, l, this.zLevel).tex(0.5F - f, 0.5F + f1).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        worldrenderer.pos(k, l, this.zLevel).tex(0.5F - f, 0.5F - f1).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        worldrenderer.pos(k, 0.0D, this.zLevel).tex(0.5F + f, 0.5F - f1).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        worldrenderer.pos(0.0D, 0.0D, this.zLevel).tex(0.5F + f, 0.5F + f1).color(1.0F, 1.0F, 1.0F, 1.0F).endVertex();
        tessellator.draw();
    }

    /**
     * Draws the screen and all the components in it. Args : mouseX, mouseY, renderPartialTicks
     */
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.removeExternalButtons();
        this.layoutButtons();
        MainMenuStyle.drawBackground(this.width, this.height, partialTicks);

        float titleY = this.height / 2.0F - (this.buttonList.size() * BUTTON_HEIGHT) / 2.0F;
        MainMenuStyle.drawCenteredString(this.titleFont, "Unfair", this.width / 2.0F, titleY, MainMenuStyle.WHITE_208);
        this.drawMenuButtons(mouseX, mouseY);

        float splashY = this.height / 2.0F
                + (BUTTON_HEIGHT + BUTTON_GAP) * (this.buttonList.size() + 1)
                - (this.buttonList.size() * BUTTON_HEIGHT) / 2.0F
                + this.titleFont.getHeight();
        MainMenuStyle.drawCenteredString(this.splashFont, this.splashText, this.width / 2.0F, splashY, MainMenuStyle.WHITE_208);

        String account = this.mc.getSession() == null ? "Unknown" : this.mc.getSession().getUsername();
        this.buttonFont.drawString(account, 10.0F, this.height - 18.0F, MainMenuStyle.WHITE_170);
    }

    /**
     * Called when the mouse is clicked. Args : mouseX, mouseY, clickedButton
     */
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        synchronized (this.threadLock) {
            if (!this.openGLWarning1.isEmpty() && mouseX >= this.field_92022_t && mouseX <= this.field_92020_v && mouseY >= this.field_92021_u && mouseY <= this.field_92019_w) {
                GuiConfirmOpenLink guiconfirmopenlink = new GuiConfirmOpenLink(this, this.openGLWarningLink, 13, true);
                guiconfirmopenlink.disableSecurityWarning();
                this.mc.displayGuiScreen(guiconfirmopenlink);
            }
        }
    }

    private void layoutButtons() {
        float count = 20.0F;
        float buttonWidth = BUTTON_WIDTH;
        float buttonHeight = BUTTON_HEIGHT;

        for (GuiButton button : this.buttonList) {
            button.xPosition = Math.round(this.width / 2.0F - buttonWidth / 2.0F);
            button.yPosition = Math.round(this.height / 2.0F + count - (this.buttonList.size() * buttonHeight) / 2.0F + this.titleFont.getHeight() + 2.0F);
            button.width = Math.round(buttonWidth);
            button.height = Math.round(buttonHeight);
            count += buttonHeight + BUTTON_GAP;
        }
    }

    private void removeExternalButtons() {
        this.buttonList.removeIf(button -> {
            int id = button.id;
            return id != 1 && id != 2 && id != ALT_MANAGER_BUTTON_ID && id != 5 && id != 0 && id != 4;
        });
    }

    private void drawMenuButtons(int mouseX, int mouseY) {
        for (int i = 0; i < this.buttonList.size() && i < this.animatedX.length; i++) {
            GuiButton button = this.buttonList.get(i);
            boolean hovered = button.enabled && this.inside(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height);
            this.updateButtonAnimation(i, button, hovered);

            MainMenuStyle.drawButton(this.animatedX[i], this.animatedY[i], this.animatedW[i], this.animatedH[i], BUTTON_RADIUS, hovered);
            MainMenuStyle.drawCenteredInBox(this.buttonFont, button.displayString, button.xPosition, button.yPosition, button.width, button.height, -1);
        }
    }

    private void updateButtonAnimation(int index, GuiButton button, boolean hovered) {
        if (this.animatedW[index] <= 0.0F || this.animatedH[index] <= 0.0F) {
            this.animatedX[index] = button.xPosition;
            this.animatedY[index] = button.yPosition;
            this.animatedW[index] = button.width;
            this.animatedH[index] = button.height;
        }

        float targetX = hovered ? button.xPosition + 1.5F : button.xPosition;
        float targetY = hovered ? button.yPosition + 1.5F : button.yPosition;
        float targetW = hovered ? button.width - 3.0F : button.width;
        float targetH = hovered ? button.height - 3.0F : button.height;
        this.animatedX[index] = this.interpolate(this.animatedX[index], targetX, 0.15F);
        this.animatedY[index] = this.interpolate(this.animatedY[index], targetY, 0.15F);
        this.animatedW[index] = this.interpolate(this.animatedW[index], targetW, 0.15F);
        this.animatedH[index] = this.interpolate(this.animatedH[index], targetH, 0.15F);
    }

    private void resetButtonAnimation() {
        for (int i = 0; i < this.buttonList.size() && i < this.animatedX.length; i++) {
            GuiButton button = this.buttonList.get(i);
            this.animatedX[i] = button.xPosition;
            this.animatedY[i] = button.yPosition;
            this.animatedW[i] = button.width;
            this.animatedH[i] = button.height;
        }
    }

    private void updateCustomSplashText() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        int day = calendar.get(Calendar.DATE);
        int month = calendar.get(Calendar.MONTH) + 1;

        if (day == 27 && month == 6) {
            this.splashText = "Happy birthday Miko!";
        } else if (day == 8 && month == 3) {
            this.splashText = "Happy birthday Karuizawa!";
        } else {
            this.splashText = CUSTOM_SPLASHES[RANDOM.nextInt(CUSTOM_SPLASHES.length)];
        }
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
