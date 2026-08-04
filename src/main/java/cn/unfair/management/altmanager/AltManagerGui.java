package cn.unfair.management.altmanager;

import cn.unfair.management.altmanager.microsoft.MicrosoftOAuthTranslation;
import cn.unfair.ui.mainmenu.MainMenuButtonPostProcessor;
import cn.unfair.ui.mainmenu.MainMenuStyle;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AltManagerGui extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<Alt> alts = new ArrayList<>();
    public static String status = "§aIdle";
    private static File altFile;

    private static final int NAME_LIMIT = 16;
    private static final int DARK = new Color(1, 1, 1).getRGB();
    private static final int PANEL = new Color(34, 34, 34).getRGB();
    private static final int LIGHT = new Color(254, 254, 254).getRGB();
    private static final int MUTED = new Color(153, 153, 153).getRGB();
    private static final float BUTTON_RADIUS = 7.0F;

    private final List<Button> buttons = new ArrayList<>();
    private final FontRenderer font14 = Fonts.interRegular.get(14.0F);
    private final FontRenderer font18 = Fonts.interRegular.get(18.0F);
    private final FontRenderer font20 = Fonts.interRegular.get(20.0F);
    private final FontRenderer font22 = Fonts.interMedium.get(22.0F);
    private GuiTextField crackedField;
    private GuiTextField tokenField;
    private Alt selected;
    private String oauthStatus = "";
    private boolean oauthRunning;
    private Dialog dialog = Dialog.NONE;
    private float scroll;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int entryHeight;
    private int buttonBaseX;
    private int buttonBaseY;
    private int uiScale = 1;
    private double mouseX;
    private double mouseY;
    private final GuiScreen parentScreen;

    public AltManagerGui() {
        this(new GuiMainMenu());
    }

    public AltManagerGui(GuiScreen parentScreen) {
        this.parentScreen = parentScreen == null ? new GuiMainMenu() : parentScreen;
        loadAlts();
        List<Alt> visible = getVisibleAlts();
        if (!visible.isEmpty()) {
            selected = visible.get(0);
        }
    }

    private static void loadAlts() {
        if (altFile == null) {
            altFile = new File(mc.mcDataDir, "unfair_alts.txt");
        }
        alts.clear();
        if (!altFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(altFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    boolean cracked = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                    Alt alt = new Alt(parts[0], parts[1], parts.length > 3 ? parts[3] : "", cracked);
                    if (parts.length > 4) alt.setRefreshToken(parts[4]);
                    if (parts.length > 5) alt.setBanned(Boolean.parseBoolean(parts[5]));
                    alts.add(alt);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveAlts() {
        if (altFile == null) {
            altFile = new File(mc.mcDataDir, "unfair_alts.txt");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(altFile))) {
            for (Alt alt : alts) {
                writer.println(alt.getEmail() + ":" + alt.getPassword() + ":" + alt.isCracked() + ":" +
                        (alt.getName() != null ? alt.getName() : "") + ":" +
                        (alt.getRefreshToken() != null ? alt.getRefreshToken() : "") + ":" + alt.isBanned());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Alt> getAlts() {
        return alts;
    }

    public static void saveAltsStatic() {
        saveAlts();
    }

    @Override
    public void initGui() {
        ScaledResolution sr = new ScaledResolution(mc);
        uiScale = Math.max(1, sr.getScaleFactor());
        float width = sr.getScaledWidth() * uiScale;
        float height = sr.getScaledHeight() * uiScale;

        listX = u((width - (width - 200.0F)) * 0.5F);
        listY = u(69.0F);
        listW = u(width - 200.0F);
        listH = u(height - 169.0F);
        entryHeight = u(52.0F);

        buttonBaseX = u(width * 0.5F - 486.0F);
        buttonBaseY = u(height - 94.0F);

        buttons.clear();
        setupButtons();

        if (crackedField == null) {
            crackedField = new GuiTextField(1, mc.fontRendererObj, 0, 0, u(264.0F), u(34.0F));
            crackedField.setEnableBackgroundDrawing(false);
            crackedField.setMaxStringLength(NAME_LIMIT);
        }
        if (tokenField == null) {
            tokenField = new GuiTextField(2, mc.fontRendererObj, 0, 0, u(264.0F), u(34.0F));
            tokenField.setEnableBackgroundDrawing(false);
            tokenField.setMaxStringLength(4096);
        }

        clampScroll();
    }

    private void setupButtons() {
        int y = buttonBaseY;
        int wideW = u(180.0F);
        int shortW = u(146.0F);
        int buttonH = u(40.0F);
        buttons.add(new Button("Login", buttonBaseX, y, wideW, buttonH, this::loginSelected));
        buttons.add(new Button("Cracked Login", buttonBaseX + u(198.0F), y, wideW, buttonH, this::openCrackedDialog));
        buttons.add(new Button("Cookie Login", buttonBaseX + u(396.0F), y, wideW, buttonH, this::cookieLogin));
        buttons.add(new Button("Web Login", buttonBaseX + u(594.0F), y, wideW, buttonH, this::startWebLogin));
        buttons.add(new Button("Token Login", buttonBaseX + u(792.0F), y, wideW, buttonH, this::openTokenDialog));

        y += u(48.0F);
        buttons.add(new Button("Reload", buttonBaseX + u(162.0F), y, shortW, buttonH, this::reloadAlts));
        buttons.add(new Button("Random", buttonBaseX + u(324.0F), y, shortW, buttonH, this::randomOfflineLogin));
        buttons.add(new Button("Remove", buttonBaseX + u(486.0F), y, shortW, buttonH, this::removeSelected));
        buttons.add(new Button("Back", buttonBaseX + u(648.0F), y, shortW, buttonH, this::closeScreen));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        MainMenuStyle.drawBackground(this.width, this.height, partialTicks);
        drawBackgroundTint();
        drawHeader();
        drawList();
        renderToolbarPostProcessing();
        drawToolbar();
        drawDialog();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawBackgroundTint() {
        RenderUtil.drawRect(0.0D, 0.0D, this.width, this.height, withAlpha(DARK, 30));
    }

    private void drawHeader() {
        String user = mc.getSession() == null ? "Unknown" : mc.getSession().getUsername();
        font18.drawString(user, u(20.0F), u(20.0F), new Color(220, 220, 220).getRGB());

        List<Alt> visible = getVisibleAlts();
        String title = "Account Manager - " + visible.size() + " alts";
        font18.drawCenteredString(title, this.width * 0.5F, u(20.0F), LIGHT);
        font18.drawCenteredString(stripColor(status), this.width * 0.5F, u(40.0F), statusColor());
    }

    private void drawList() {
        drawFlatRect(listX, listY, listW, listH, withAlpha(DARK, 72));

        List<Alt> visible = getVisibleAlts();
        clampScroll(visible);
        if (visible.isEmpty()) {
            font14.drawString("No saved alts.", listX + u(10.0F), listY + u(14.0F), MUTED);
            return;
        }

        float maxScroll = Math.max(0.0F, visible.size() * entryHeight - listH);
        scroll = clamp(scroll, 0.0F, maxScroll);

        RenderUtil.scissor(listX, listY, listW, listH);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        try {
            for (int i = 0; i < visible.size(); i++) {
                Alt alt = visible.get(i);
                float rowY = listY + u(4.0F) + i * entryHeight - scroll;
                if (rowY + entryHeight < listY || rowY > listY + listH) {
                    continue;
                }

                boolean selectedRow = alt == selected;
                boolean hovered = inside(mouseX, mouseY, listX + u(4.0F), rowY, listW - u(8.0F), entryHeight);
                int fill = selectedRow || hovered ? withAlpha(LIGHT, selectedRow ? 56 : 36) : withAlpha(DARK, 46);
                drawFlatRect(listX + u(4.0F), rowY, listW - u(8.0F), entryHeight - u(4.0F), fill);

                String name = getDisplayName(alt);
                String second = (alt.isCracked() ? "Cracked" : "Microsoft") + (alt.isBanned() ? " / Banned" : alt.hasRefreshToken() ? " / Token saved" : "");
                String email = alt.getEmail() == null ? "" : alt.getEmail();

                font20.drawString(name, listX + u(16.0F), rowY + u(8.0F), LIGHT);
                font14.drawString(second + (email.isEmpty() || email.equals(name) ? "" : " / " + email), listX + u(16.0F), rowY + u(31.0F), MUTED);
            }
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void drawToolbar() {
        for (Button button : buttons) {
            boolean hovered = button.contains(mouseX, mouseY);
            MainMenuStyle.drawButton(button.x, button.y, button.w, button.h, BUTTON_RADIUS, hovered);
            MainMenuStyle.drawCenteredInBox(font18, button.text, button.x, button.y, button.w, button.h, LIGHT);
        }
    }

    private void renderToolbarPostProcessing() {
        List<MainMenuButtonPostProcessor.ButtonBounds> bounds = new ArrayList<>();
        for (Button button : buttons) {
            bounds.add(new MainMenuButtonPostProcessor.ButtonBounds(button.x, button.y, button.w, button.h, BUTTON_RADIUS));
        }
        MainMenuButtonPostProcessor.render(bounds, bound -> MainMenuStyle.drawButtonMask(bound.x, bound.y, bound.w, bound.h, bound.radius), false);
    }

    private void drawDialog() {
        if (dialog == Dialog.NONE) {
            return;
        }

        float dialogW = u(340.0F);
        float dialogH = u(210.0F);
        float x = this.width * 0.5F - dialogW * 0.5F;
        float y = this.height * 0.5F - dialogH * 0.5F;
        drawFlatRect(0.0F, 0.0F, this.width, this.height, withAlpha(DARK, 120));
        drawFlatRect(x + u(4.0F), y + u(4.0F), dialogW, dialogH, withAlpha(DARK, 90));
        drawFlatRect(x, y, dialogW, dialogH, withAlpha(PANEL, 245));
        drawBorder(x, y, dialogW, dialogH, withAlpha(LIGHT, 48));

        boolean cracked = dialog == Dialog.CRACKED_LOGIN;
        String title = cracked ? "Cracked Login" : "Token Login";
        font22.drawCenteredString(title, x + dialogW * 0.5F, y + u(18.0F), LIGHT);
        drawFlatRect(x + u(38.0F), y + u(68.0F), u(264.0F), u(34.0F), withAlpha(DARK, 72));
        drawBorder(x + u(38.0F), y + u(68.0F), u(264.0F), u(34.0F), withAlpha(LIGHT, 42));

        GuiTextField field = cracked ? crackedField : tokenField;
        field.xPosition = (int) (x + u(46.0F));
        field.yPosition = (int) (y + u(77.0F));
        field.width = u(248.0F);
        field.height = u(18.0F);
        field.drawTextBox();

        if (!oauthStatus.isEmpty()) {
            font14.drawCenteredString(oauthStatus, x + dialogW * 0.5F, y + u(113.0F),
                    oauthStatus.toLowerCase(Locale.ROOT).contains("fail") ? new Color(255, 85, 85).getRGB() : new Color(235, 245, 245).getRGB());
        }

        renderDialogButtonPostProcessing(x, y);
        drawDialogButton(x + u(38.0F), y + u(146.0F), u(122.0F), u(36.0F), "Login");
        drawDialogButton(x + u(180.0F), y + u(146.0F), u(122.0F), u(36.0F), "Cancel");
    }

    private void renderDialogButtonPostProcessing(float x, float y) {
        List<MainMenuButtonPostProcessor.ButtonBounds> bounds = new ArrayList<>();
        bounds.add(new MainMenuButtonPostProcessor.ButtonBounds(x + u(38.0F), y + u(146.0F), u(122.0F), u(36.0F), BUTTON_RADIUS));
        bounds.add(new MainMenuButtonPostProcessor.ButtonBounds(x + u(180.0F), y + u(146.0F), u(122.0F), u(36.0F), BUTTON_RADIUS));
        MainMenuButtonPostProcessor.render(bounds, bound -> MainMenuStyle.drawButtonMask(bound.x, bound.y, bound.w, bound.h, bound.radius), false);
    }

    private void drawDialogButton(float x, float y, float w, float h, String text) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        MainMenuStyle.drawButton(x, y, w, h, BUTTON_RADIUS, hovered);
        MainMenuStyle.drawCenteredInBox(font18, text, x, y, w, h, LIGHT);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (dialog != Dialog.NONE) {
            handleDialogClick(mouseX, mouseY);
            return;
        }

        if (handleToolbar(mouseX, mouseY)) {
            return;
        }

        List<Alt> visible = getVisibleAlts();
        for (int i = 0; i < visible.size(); i++) {
            float rowY = listY + u(4.0F) + i * entryHeight - scroll;
            if (inside(mouseX, mouseY, listX + u(4.0F), rowY, listW - u(8.0F), entryHeight - u(4.0F))) {
                selected = visible.get(i);
                return;
            }
        }
    }

    private void handleDialogClick(int mouseX, int mouseY) {
        float dialogW = u(340.0F);
        float dialogH = u(210.0F);
        float x = this.width * 0.5F - dialogW * 0.5F;
        float y = this.height * 0.5F - dialogH * 0.5F;
        GuiTextField field = dialog == Dialog.CRACKED_LOGIN ? crackedField : tokenField;
        boolean login = inside(mouseX, mouseY, x + u(38.0F), y + u(146.0F), u(122.0F), u(36.0F));
        boolean cancel = inside(mouseX, mouseY, x + u(180.0F), y + u(146.0F), u(122.0F), u(36.0F));
        boolean fieldClick = inside(mouseX, mouseY, x + u(38.0F), y + u(68.0F), u(264.0F), u(34.0F));
        field.setFocused(fieldClick);

        if (login) {
            submitDialog();
        } else if (cancel || !inside(mouseX, mouseY, x, y, dialogW, dialogH)) {
            dialog = Dialog.NONE;
            oauthStatus = "";
            crackedField.setFocused(false);
            tokenField.setFocused(false);
        }
    }

    private void submitDialog() {
        if (dialog == Dialog.CRACKED_LOGIN) {
            String name = crackedField.getText() == null ? "" : crackedField.getText().trim();
            if (!isValidOfflineName(name)) {
                status = "§cEnter a valid username";
                return;
            }
            addAltAndLogin(name);
            crackedField.setText("");
            crackedField.setFocused(false);
            dialog = Dialog.NONE;
            return;
        }

        String token = tokenField.getText() == null ? "" : tokenField.getText().trim();
        if (token.isEmpty()) {
            status = "§cToken is empty.";
            return;
        }
        startTokenLogin(token);
    }

    private void addAltAndLogin(String name) {
        Alt alt = new Alt(name, "", name, true);
        alts.add(0, alt);
        selected = alt;
        saveAlts();
        SessionChanger.instance().loginCracked(name);
        status = "§aLogged in as " + name;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (dialog != Dialog.NONE) {
                dialog = Dialog.NONE;
                crackedField.setFocused(false);
                tokenField.setFocused(false);
                oauthStatus = "";
            } else {
                closeScreen();
            }
            return;
        }

        if (dialog != Dialog.NONE) {
            if (keyCode == Keyboard.KEY_RETURN) {
                submitDialog();
                return;
            }
            GuiTextField field = dialog == Dialog.CRACKED_LOGIN ? crackedField : tokenField;
            if (field.textboxKeyTyped(typedChar, keyCode)) {
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        crackedField.updateCursorCounter();
        tokenField.updateCursorCounter();
    }

    @Override
    public void handleMouseInput() {
        try {
            super.handleMouseInput();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && dialog == Dialog.NONE && inside(Mouse.getEventX() * this.width / mc.displayWidth, this.height - Mouse.getEventY() * this.height / mc.displayHeight - 1, listX, listY, listW, listH)) {
            scroll += wheel > 0 ? -24.0F : 24.0F;
            clampScroll();
        }
    }

    private boolean handleToolbar(int mouseX, int mouseY) {
        for (Button button : buttons) {
            if (button.contains(mouseX, mouseY)) {
                button.action.run();
                return true;
            }
        }
        return false;
    }

    private void closeScreen() {
        mc.displayGuiScreen(parentScreen);
    }

    private void loginSelected() {
        if (selected == null) {
            status = "§cNo alt selected.";
            return;
        }

        if (selected.isCracked()) {
            SessionChanger.instance().loginCracked(getDisplayName(selected));
            status = "§aLogged in as " + getDisplayName(selected);
        } else if (selected.hasRefreshToken()) {
            SessionChanger.instance().loginWithRefreshToken(selected.getRefreshToken());
        } else {
            SessionChanger.instance().loginMicrosoft(selected.getEmail(), selected.getPassword());
        }
    }

    private void openCrackedDialog() {
        dialog = Dialog.CRACKED_LOGIN;
        oauthStatus = "";
        crackedField.setText("");
        crackedField.setFocused(true);
        tokenField.setFocused(false);
    }

    private void openTokenDialog() {
        dialog = Dialog.TOKEN_LOGIN;
        oauthStatus = "";
        tokenField.setText("");
        tokenField.setFocused(true);
        crackedField.setFocused(false);
    }

    private void cookieLogin() {
        status = "§cCookie login not available.";
    }

    private void startWebLogin() {
        if (oauthRunning) {
            return;
        }
        oauthRunning = true;
        oauthStatus = "";
        status = "§aOpening browser...";
        MicrosoftOAuthTranslation.getRefreshToken(token -> {
            if (token == null) {
                mc.addScheduledTask(() -> {
                    oauthRunning = false;
                    status = "§cOAuth failed";
                });
                return;
            }

            mc.addScheduledTask(() -> status = "§6Logging in...");
            new Thread(() -> {
                MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.login(token);
                mc.addScheduledTask(() -> {
                    oauthRunning = false;
                    if (loginData.isGood()) {
                        accountFromOAuth(loginData.username, loginData.uuid, loginData.mcToken);
                        status = "§aWeb login complete.";
                    } else {
                        status = "§cWeb login failed: " + (loginData.errorMessage != null ? loginData.errorMessage : "Unknown error");
                    }
                });
            }, "Unfair Web Login").start();
        });
    }

    private void startTokenLogin(String token) {
        if (oauthRunning) {
            return;
        }
        oauthRunning = true;
        oauthStatus = "Checking token...";
        status = "§aChecking token...";
        new Thread(() -> {
            try {
                MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.login(token);
                mc.addScheduledTask(() -> {
                    oauthRunning = false;
                    if (loginData.isGood()) {
                        accountFromOAuth(loginData.username, loginData.uuid, loginData.mcToken);
                        dialog = Dialog.NONE;
                        tokenField.setText("");
                        oauthStatus = "";
                        status = "§aToken login complete.";
                    } else {
                        oauthStatus = "Token login failed: " + (loginData.errorMessage != null ? loginData.errorMessage : "Unknown error");
                        status = "§c" + oauthStatus;
                    }
                });
            } catch (Exception e) {
                mc.addScheduledTask(() -> {
                    oauthRunning = false;
                    oauthStatus = "Token login failed: " + shortError(e);
                    status = "§c" + oauthStatus;
                });
            }
        }, "Unfair Token Login").start();
    }

    private void accountFromOAuth(String name, String uuid, String accessToken) {
        Alt existing = null;
        for (Alt alt : alts) {
            if (alt.getName() != null && alt.getName().equalsIgnoreCase(name)) {
                existing = alt;
                break;
            }
        }

        if (existing != null) {
            existing.setUuid(uuid);
            existing.setRefreshToken(accessToken);
            selected = existing;
        } else {
            Alt alt = new Alt(name, "", name, false);
            alt.setUuid(uuid);
            alt.setRefreshToken(accessToken);
            alts.add(0, alt);
            selected = alt;
        }
        saveAlts();
    }

    private void reloadAlts() {
        loadAlts();
        List<Alt> visible = getVisibleAlts();
        selected = visible.isEmpty() ? null : visible.get(0);
        scroll = 0.0F;
        status = "§aReloaded accounts.";
    }

    private void removeSelected() {
        if (selected == null) {
            status = "§cNo alt selected.";
            return;
        }
        alts.remove(selected);
        List<Alt> visible = getVisibleAlts();
        selected = visible.isEmpty() ? null : visible.get(0);
        saveAlts();
        clampScroll();
        status = "§aRemoved account.";
    }

    private void randomOfflineLogin() {
        String name = RandomOfflineNameGenerator.generate();
        selected = null;
        SessionChanger.instance().loginCracked(name);
        status = "§aRandom offline login. (" + name + ")";
    }

    private List<Alt> getVisibleAlts() {
        return new ArrayList<>(alts);
    }

    private void clampScroll() {
        clampScroll(getVisibleAlts());
    }

    private void clampScroll(List<Alt> visible) {
        float maxScroll = Math.max(0.0F, visible.size() * entryHeight - listH);
        scroll = clamp(scroll, 0.0F, maxScroll);
    }

    private boolean isValidOfflineName(String text) {
        if (text == null || text.length() < 1 || text.length() > NAME_LIMIT) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && c != '_') {
                return false;
            }
        }
        return true;
    }

    private String getDisplayName(Alt alt) {
        String displayName = alt.getName() != null && !alt.getName().isEmpty() ? alt.getName() : alt.getEmail();
        return displayName == null || displayName.isEmpty() ? "Unknown" : displayName;
    }

    private int statusColor() {
        if (status.startsWith("§a")) return new Color(85, 255, 85).getRGB();
        if (status.startsWith("§c")) return new Color(255, 85, 85).getRGB();
        if (status.startsWith("§6")) return new Color(255, 170, 0).getRGB();
        return new Color(235, 245, 245).getRGB();
    }

    private String stripColor(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    private String shortError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = cause.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ');
        return message.length() > 42 ? message.substring(0, 42) + "..." : message;
    }

    private int u(float pixels) {
        return Math.round(pixels / Math.max(1, uiScale));
    }

    private float centerTextY(float height, FontRenderer font) {
        return (height - font.getHeight()) * 0.5F + u(1.0F);
    }

    private void drawFlatRect(float x, float y, float w, float h, int color) {
        RenderUtil.drawRect(x, y, x + w, y + h, color);
    }

    private void drawBorder(float x, float y, float w, float h, int color) {
        RenderUtil.drawRect(x, y, x + w, y + 1.0F, color);
        RenderUtil.drawRect(x, y + h - 1.0F, x + w, y + h, color);
        RenderUtil.drawRect(x, y, x + 1.0F, y + h, color);
        RenderUtil.drawRect(x + w - 1.0F, y, x + w, y + h, color);
    }

    private int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private enum Dialog {
        NONE,
        CRACKED_LOGIN,
        TOKEN_LOGIN
    }

    private static class Button {
        private final String text;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final Runnable action;

        private Button(String text, int x, int y, int w, int h, Runnable action) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }
}
