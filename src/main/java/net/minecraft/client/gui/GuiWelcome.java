package net.minecraft.client.gui;

import cn.unfair.ui.mainmenu.LottieAnimationRenderer;
import cn.unfair.ui.mainmenu.GifAnimationLayer;
import cn.unfair.ui.mainmenu.MainMenuStyle;
import cn.unfair.util.font.CustomFontRenderer;
import cn.unfair.util.font.Fonts;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;

import java.io.IOException;

/** The first screen shown after the client finishes loading. */
public class GuiWelcome extends GuiScreen {
    private final LottieAnimationRenderer animation = new LottieAnimationRenderer();
    private final GifAnimationLayer gifs = new GifAnimationLayer();
    private final CustomFontRenderer promptFont = Fonts.interRegular.get(16.0F);
    private static final String PROMPT = "Click anywhere to enter the Main Menu";

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        MainMenuStyle.drawBackground(this.width, this.height, partialTicks);
        this.gifs.draw(this.width, this.height, Minecraft.getSystemTime());
        float animationSize = Math.min(this.width, this.height) * 0.68F;
        this.animation.draw(this.width * 0.5F, this.height * 0.42F, animationSize);
        float promptY = this.height - this.promptFont.getHeight() - 44.0F;
        float promptWidth = this.promptFont.getStringWidth(PROMPT);
        RenderUtil.drawRect(this.width * 0.5D - promptWidth * 0.5D - 10.0D, promptY - 6.0D,
                promptWidth + 20.0D, this.promptFont.getHeight() + 12.0D, 0x56000000);
        this.promptFont.drawString(PROMPT, this.width * 0.5F - promptWidth * 0.5F, promptY, MainMenuStyle.WHITE_208);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.gifs.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        this.mc.displayGuiScreen(new GuiMainMenu());
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        this.gifs.mouseClickMove(mouseX, mouseY, clickedMouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.gifs.mouseReleased(mouseX, mouseY, state)) {
            this.gifs.release();
            this.mc.displayGuiScreen(new GuiMainMenu());
        }
    }

    @Override
    public void onGuiClosed() {
        this.gifs.release();
    }
}
