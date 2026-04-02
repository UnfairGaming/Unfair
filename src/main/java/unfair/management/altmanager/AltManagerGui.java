package unfair.management.altmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import unfair.Unfair;
import unfair.management.altmanager.microsoft.MicrosoftOAuthTranslation;
import unfair.module.modules.render.HUD;
import unfair.ui.ClickGui;
import unfair.util.RenderUtil;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class AltManagerGui extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final List<Alt> alts = new ArrayList<>();
    public static String status = "§aIdle";
    private static File altFile;
    // Buttons
    private final List<Button> buttons = new ArrayList<>();
    private int selectedAlt = -1;
    private int scrollOffset = 0;
    private final int maxVisibleAlts = 8;
    // UI dimensions
    private int guiX, guiY, guiWidth, guiHeight;
    private int listX, listY, listWidth, listHeight;
    // Input field (single input for username/cracked)
    private String usernameInput = "";
    private boolean inputFocused = false;

    public AltManagerGui() {
        loadAlts();
    }

    private static void loadAlts() {
        if (altFile == null) {
            altFile = new File(mc.mcDataDir, "unfair_alts.txt");
        }
        alts.clear();
        if (!altFile.exists()) return;

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
                String line = alt.getEmail() + ":" + alt.getPassword() + ":" + alt.isCracked() + ":" +
                        (alt.getName() != null ? alt.getName() : "") + ":" +
                        (alt.getRefreshToken() != null ? alt.getRefreshToken() : "") + ":" + alt.isBanned();
                writer.println(line);
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
        guiWidth = 320;
        guiHeight = 240;
        guiX = (sr.getScaledWidth() - guiWidth) / 2;
        guiY = (sr.getScaledHeight() - guiHeight) / 2;

        listX = guiX + 10;
        listY = guiY + 30;
        listWidth = 150;
        listHeight = 160;

        buttons.clear();
        int btnX = guiX + 170;
        int btnY = guiY + 30;
        int btnW = 140;
        int btnH = 18;
        int spacing = 22;

        buttons.add(new Button("Login", btnX, btnY, btnW, btnH, () -> loginSelected()));
        buttons.add(new Button("Add Cracked", btnX, btnY + spacing, btnW, btnH, () -> addCracked()));
        buttons.add(new Button("Token Login", btnX, btnY + spacing * 2, btnW, btnH, () -> mc.displayGuiScreen(new TokenLoginGui(this))));
        buttons.add(new Button("Remove", btnX, btnY + spacing * 3, btnW, btnH, () -> removeSelected()));
        buttons.add(new Button("OAuth Login", btnX, btnY + spacing * 4, btnW, btnH, () -> startOAuth()));
        buttons.add(new Button("Back", guiX + guiWidth / 2 - 30, guiY + guiHeight - 25, 60, 18, () -> mc.displayGuiScreen(ClickGui.getInstance())));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Background
        drawDefaultBackground();

        // Main panel
        RenderUtil.drawRoundedRectangle(guiX, guiY, guiX + guiWidth, guiY + guiHeight, 8, new Color(30, 30, 30, 200).getRGB());

        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        int hudColor = hud.getColor(System.currentTimeMillis()).getRGB();

        // Title
        mc.fontRendererObj.drawStringWithShadow("Alt Manager", guiX + 10, guiY + 10, hudColor);

        // Status
        mc.fontRendererObj.drawStringWithShadow("Status: " + status, guiX + 120, guiY + 10, -1);

        // Alt list background
        RenderUtil.drawRoundedRectangle(listX, listY, listX + listWidth, listY + listHeight, 4, new Color(20, 20, 20, 180).getRGB());

        // Alt list
        int itemHeight = 18;
        int visibleStart = Math.max(0, Math.min(scrollOffset, alts.size() - maxVisibleAlts));
        int visibleEnd = Math.min(visibleStart + maxVisibleAlts, alts.size());

        for (int i = visibleStart; i < visibleEnd; i++) {
            Alt alt = alts.get(i);
            int itemY = listY + 2 + (i - visibleStart) * itemHeight;
            boolean isSelected = i == selectedAlt;
            boolean isHovered = mouseX >= listX && mouseX <= listX + listWidth && mouseY >= itemY && mouseY < itemY + itemHeight;

            int bgColor = isSelected ? hudColor : (isHovered ? new Color(60, 60, 60, 180).getRGB() : new Color(40, 40, 40, 150).getRGB());
            RenderUtil.drawRoundedRectangle(listX + 2, itemY, listX + listWidth - 2, itemY + itemHeight - 2, 2, bgColor);

            String typeStr = alt.isCracked() ? "Cracked" : "Microsoft";
            String displayName = alt.getName() != null && !alt.getName().isEmpty() ? alt.getName() : alt.getEmail();
            if (displayName == null || displayName.isEmpty()) displayName = "Unknown";

            String listText = typeStr + ": " + displayName;
            if (alt.isBanned()) listText = "§c[Banned] §r" + listText;
            else if (alt.isCracked()) listText = "§e" + listText;
            else listText = "§b" + listText;

            mc.fontRendererObj.drawString(listText, listX + 6, itemY + 4, -1);
        }

        // Single input field for username
        int inputX = guiX + 170;
        int inputY = guiY + 180;
        int inputW = 140;
        int inputH = 14;

        int inputBorder = inputFocused ? hudColor : new Color(80, 80, 80).getRGB();
        RenderUtil.drawRoundedRectangle(inputX - 1, inputY - 1, inputX + inputW + 1, inputY + inputH + 1, 2, inputBorder);
        RenderUtil.drawRoundedRectangle(inputX, inputY, inputX + inputW, inputY + inputH, 2, new Color(20, 20, 20).getRGB());
        mc.fontRendererObj.drawString("Username/Token:", inputX, inputY - 10, new Color(200, 200, 200).getRGB());

        String inputDisplay = usernameInput.isEmpty() ? (inputFocused ? "_" : "") : usernameInput;
        if (inputFocused && !usernameInput.isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0) {
            inputDisplay += "_";
        }
        mc.fontRendererObj.drawString(inputDisplay, inputX + 4, inputY + 3, usernameInput.isEmpty() ? new Color(100, 100, 100).getRGB() : -1);

        // Buttons
        for (Button btn : buttons) {
            boolean hovered = mouseX >= btn.x && mouseX <= btn.x + btn.w && mouseY >= btn.y && mouseY <= btn.y + btn.h;
            int btnColor = hovered ? new Color(hudColor).brighter().getRGB() : hudColor;
            RenderUtil.drawRoundedRectangle(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, 4, btnColor);
            int textWidth = mc.fontRendererObj.getStringWidth(btn.text);
            mc.fontRendererObj.drawStringWithShadow(btn.text, btn.x + (btn.w - textWidth) / 2, btn.y + 5, -1);
        }

        // Scroll info
        if (alts.size() > maxVisibleAlts) {
            mc.fontRendererObj.drawString("(" + alts.size() + " alts, scroll)", listX, listY + listHeight + 2, new Color(150, 150, 150).getRGB());
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Check alt list clicks
        int itemHeight = 18;
        for (int i = 0; i < alts.size(); i++) {
            int itemY = listY + 2 + i * itemHeight - scrollOffset * itemHeight;
            if (itemY >= listY && itemY < listY + listHeight && mouseX >= listX && mouseX <= listX + listWidth && mouseY >= itemY && mouseY < itemY + itemHeight) {
                selectedAlt = i;
                if (mouseButton == 0 && i < alts.size()) {
                    Alt alt = alts.get(i);
                    usernameInput = alt.getName() != null ? alt.getName() : alt.getEmail();
                }
                return;
            }
        }

        // Input field click
        int inputX = guiX + 170;
        int inputY = guiY + 180;
        int inputW = 140;
        int inputH = 14;

        if (mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= inputY && mouseY <= inputY + inputH) {
            inputFocused = true;
            return;
        }

        inputFocused = false;

        // Button clicks
        for (Button btn : buttons) {
            if (mouseX >= btn.x && mouseX <= btn.x + btn.w && mouseY >= btn.y && mouseY <= btn.y + btn.h) {
                btn.action.run();
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(ClickGui.getInstance());
            return;
        }

        if (keyCode == Keyboard.KEY_TAB) {
            inputFocused = !inputFocused;
            return;
        }

        if (inputFocused) {
            if (keyCode == Keyboard.KEY_BACK) {
                if (!usernameInput.isEmpty()) usernameInput = usernameInput.substring(0, usernameInput.length() - 1);
            } else if (isValidChar(typedChar) && usernameInput.length() < 64) {
                usernameInput += typedChar;
            }
        }
    }

    private boolean isValidChar(char c) {
        return c >= 32 && c < 127;
    }

    @Override
    public void handleMouseInput() {
        try {
            super.handleMouseInput();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int scroll = org.lwjgl.input.Mouse.getEventDWheel();
        if (scroll != 0) {
            scrollOffset += scroll > 0 ? -1 : 1;
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, alts.size() - maxVisibleAlts)));
        }
    }

    private void loginSelected() {
        if (selectedAlt < 0 || selectedAlt >= alts.size()) {
            status = "§cNo alt selected";
            return;
        }
        Alt alt = alts.get(selectedAlt);
        if (alt.isCracked()) {
            SessionChanger.instance().loginCracked(alt.getEmail());
            status = "§aLogged in as " + alt.getEmail();
        } else {
            if (alt.hasRefreshToken()) {
                SessionChanger.instance().loginWithRefreshToken(alt.getRefreshToken());
            } else {
                SessionChanger.instance().loginMicrosoft(alt.getEmail(), alt.getPassword());
            }
        }
    }

    private void addCracked() {
        if (usernameInput.isEmpty()) {
            status = "§cEnter a username";
            return;
        }
        Alt alt = new Alt(usernameInput, "", usernameInput, true);
        alts.add(alt);
        saveAlts();
        usernameInput = "";
        status = "§aAdded cracked alt";
    }

    private void removeSelected() {
        if (selectedAlt < 0 || selectedAlt >= alts.size()) {
            status = "§cNo alt selected";
            return;
        }
        alts.remove(selectedAlt);
        if (selectedAlt >= alts.size()) selectedAlt = alts.size() - 1;
        saveAlts();
        status = "§aRemoved alt";
    }

    private void startOAuth() {
        status = "§6Waiting for browser...";
        MicrosoftOAuthTranslation.getRefreshToken(token -> {
            if (token != null) {
                mc.addScheduledTask(() -> status = "§6Logging in...");

                // Login to get username first
                new Thread(() -> {
                    MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.login(token);
                    mc.addScheduledTask(() -> {
                        if (loginData.isGood()) {
                            // Create alt with real username
                            Alt alt = new Alt(loginData.username, "", loginData.username, false);
                            alt.setRefreshToken(token);
                            alt.setUuid(loginData.uuid);
                            alts.add(alt);
                            saveAlts();

                            // Set session
                            SessionChanger.instance().setSessionWithData(loginData);
                            status = "§aLogged in as " + loginData.username;
                        } else {
                            status = "§cOAuth login failed: " + (loginData.errorMessage != null ? loginData.errorMessage : "Unknown error");
                        }
                    });
                }).start();
            } else {
                mc.addScheduledTask(() -> status = "§cOAuth failed");
            }
        });
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private static class Button {
        String text;
        int x, y, w, h;
        Runnable action;

        Button(String text, int x, int y, int w, int h, Runnable action) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }
}
