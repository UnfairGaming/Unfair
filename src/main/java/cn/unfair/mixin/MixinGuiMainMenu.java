package cn.unfair.mixin;

import cn.unfair.management.altmanager.AltManagerGui;
import cn.unfair.ui.mainmenu.MainMenuButtonPostProcessor;
import cn.unfair.ui.mainmenu.MainMenuStyle;
import cn.unfair.ui.mainmenu.SilentMenuButton;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

@SideOnly(Side.CLIENT)
@Mixin({GuiMainMenu.class})
public abstract class MixinGuiMainMenu extends GuiScreen {
    private static final int ALT_MANAGER_BUTTON_ID = 9999;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 23;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_RADIUS = 7;
    private static final String[] SPLASHES = {
            "Math.random() bypass",
            "Math.abs() bypass",
            "System.out.println() bypass",
            "Fuck u",
            "Hi",
            "Unfair",
            "Minecraft 1.8.9",
            "Fan Dong Pai"
    };

    private final FontRenderer titleFont = Fonts.urbanist.get(38.0F);
    private final FontRenderer splashFont = Fonts.interRegular.get(19.0F);
    private final FontRenderer buttonFont = Fonts.interRegular.get(15.0F);
    private final float[] animatedX = new float[6];
    private final float[] animatedY = new float[6];
    private final float[] animatedW = new float[6];
    private final float[] animatedH = new float[6];
    private String splashText = "Unfair";

    @Inject(method = "initGui", at = @At("TAIL"))
    private void onInitGui(CallbackInfo ci) {
        updateSplashText();
        this.buttonList.clear();

        this.buttonList.add(new SilentMenuButton(1, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Singleplayer"));
        this.buttonList.add(new SilentMenuButton(2, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Multiplayer"));
        this.buttonList.add(new SilentMenuButton(ALT_MANAGER_BUTTON_ID, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Alt manager"));
        this.buttonList.add(new SilentMenuButton(0, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Options"));
        this.buttonList.add(new SilentMenuButton(4, 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, "Quit"));

        layoutButtons();
        resetButtonAnimation();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == ALT_MANAGER_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new AltManagerGui((GuiScreen) (Object) this));
            ci.cancel();
        }
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void onDrawScreenHead(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        removeExternalButtons();
        layoutButtons();

        MainMenuStyle.drawBackground(this.width, this.height, partialTicks);
        renderButtonPostProcessing();

        float titleY = this.height / 2.0F - (this.buttonList.size() * BUTTON_HEIGHT) / 2.0F;
        MainMenuStyle.drawCenteredString(titleFont, "Unfair", this.width / 2.0F, titleY, MainMenuStyle.WHITE_208);

        drawMenuButtons(mouseX, mouseY);

        float splashY = this.height / 2.0F
                + (BUTTON_HEIGHT + BUTTON_GAP) * (this.buttonList.size() + 1)
                - (this.buttonList.size() * BUTTON_HEIGHT) / 2.0F
                + titleFont.getHeight();
        MainMenuStyle.drawCenteredString(splashFont, splashText, this.width / 2.0F, splashY, MainMenuStyle.WHITE_208);

        String account = Minecraft.getMinecraft().getSession() == null ? "Unknown" : Minecraft.getMinecraft().getSession().getUsername();
        buttonFont.drawString(account, 10.0F, this.height - 18.0F, MainMenuStyle.WHITE_170);

        ci.cancel();
    }

    private void layoutButtons() {
        float count = 20.0F;
        float buttonWidth = BUTTON_WIDTH;
        float buttonHeight = BUTTON_HEIGHT;
        for (Object object : this.buttonList) {
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            button.xPosition = Math.round(this.width / 2.0F - buttonWidth / 2.0F);
            button.yPosition = Math.round(this.height / 2.0F + count - (this.buttonList.size() * buttonHeight) / 2.0F + titleFont.getHeight() + 2.0F);
            button.width = Math.round(buttonWidth);
            button.height = Math.round(buttonHeight);
            count += buttonHeight + BUTTON_GAP;
        }
    }

    private void removeExternalButtons() {
        this.buttonList.removeIf(button -> {
            if (!(button instanceof GuiButton)) {
                return false;
            }
            int id = ((GuiButton) button).id;
            return id != 1 && id != 2 && id != ALT_MANAGER_BUTTON_ID && id != 5 && id != 0 && id != 4;
        });
    }

    private void drawMenuButtons(int mouseX, int mouseY) {
        for (int i = 0; i < this.buttonList.size(); i++) {
            Object object = this.buttonList.get(i);
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            boolean hovered = button.enabled && inside(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height);
            updateButtonAnimation(i, button, hovered);

            MainMenuStyle.drawButton(animatedX[i], animatedY[i], animatedW[i], animatedH[i], BUTTON_RADIUS, hovered);
            MainMenuStyle.drawCenteredInBox(buttonFont, button.displayString, button.xPosition, button.yPosition, button.width, button.height, -1);
        }
    }

    private void updateButtonAnimation(int index, GuiButton button, boolean hovered) {
        if (animatedW[index] <= 0.0F || animatedH[index] <= 0.0F) {
            animatedX[index] = button.xPosition;
            animatedY[index] = button.yPosition;
            animatedW[index] = button.width;
            animatedH[index] = button.height;
        }

        float targetX = hovered ? button.xPosition + 1.5F : button.xPosition;
        float targetY = hovered ? button.yPosition + 1.5F : button.yPosition;
        float targetW = hovered ? button.width - 3.0F : button.width;
        float targetH = hovered ? button.height - 3.0F : button.height;
        animatedX[index] = interpolate(animatedX[index], targetX, 0.15F);
        animatedY[index] = interpolate(animatedY[index], targetY, 0.15F);
        animatedW[index] = interpolate(animatedW[index], targetW, 0.15F);
        animatedH[index] = interpolate(animatedH[index], targetH, 0.15F);
    }

    private void resetButtonAnimation() {
        for (int i = 0; i < this.buttonList.size() && i < animatedX.length; i++) {
            Object object = this.buttonList.get(i);
            if (object instanceof GuiButton) {
                GuiButton button = (GuiButton) object;
                animatedX[i] = button.xPosition;
                animatedY[i] = button.yPosition;
                animatedW[i] = button.width;
                animatedH[i] = button.height;
            }
        }
    }

    private void updateSplashText() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        int day = calendar.get(Calendar.DATE);
        int month = calendar.get(Calendar.MONTH) + 1;

        if (day == 27 && month == 6) {
            splashText = "Happy birthday Miko!";
        } else if (day == 8 && month == 3) {
            splashText = "Happy birthday Karuizawa!";
        } else {
            splashText = SPLASHES[new Random().nextInt(SPLASHES.length)];
        }
    }

    private float interpolate(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void renderButtonPostProcessing() {
        List<MainMenuButtonPostProcessor.ButtonBounds> bounds = new ArrayList<>();
        for (int i = 0; i < this.buttonList.size(); i++) {
            Object object = this.buttonList.get(i);
            if (!(object instanceof GuiButton)) {
                continue;
            }
            GuiButton button = (GuiButton) object;
            bounds.add(new MainMenuButtonPostProcessor.ButtonBounds(animatedX[i], animatedY[i], animatedW[i], animatedH[i], BUTTON_RADIUS));
        }
        MainMenuButtonPostProcessor.render(bounds, bound -> MainMenuStyle.drawButtonMask(bound.x, bound.y, bound.w, bound.h, bound.radius));
    }

}
