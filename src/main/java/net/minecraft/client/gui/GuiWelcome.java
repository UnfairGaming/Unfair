package net.minecraft.client.gui;

import cn.unfair.ui.mainmenu.LottieAnimationRenderer;
import cn.unfair.ui.mainmenu.MainMenuStyle;
import cn.unfair.ui.mainmenu.WelcomeTransition;
import cn.unfair.util.font.CustomFontRenderer;
import cn.unfair.util.font.Fonts;

import java.io.IOException;

public class GuiWelcome extends GuiScreen {
    private final LottieAnimationRenderer animation = new LottieAnimationRenderer();
    private final CustomFontRenderer promptFont = Fonts.interRegular.get(16.0F);
    private static final String PROMPT = "Click anywhere to enter the Main Menu";
    private boolean enteringMenu;

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        MainMenuStyle.drawBackground(this.width, this.height, partialTicks);
        float animationSize = Math.min(this.width, this.height) * 0.68F;
        this.animation.draw(this.width * 0.5F, this.height * 0.47F, animationSize);
        float promptY = this.height - this.promptFont.getHeight() - 44.0F;
        float promptWidth = this.promptFont.getStringWidth(PROMPT);
        this.promptFont.drawString(PROMPT, this.width * 0.5F - promptWidth * 0.5F, promptY, MainMenuStyle.WHITE_208);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.enteringMenu) {
            return;
        }
        this.enteringMenu = true;
        WelcomeTransition.begin(this.animation);
        this.mc.displayGuiScreen(new GuiMainMenu());
    }
}
