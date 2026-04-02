package unfair.management.altmanager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import unfair.management.altmanager.microsoft.MicrosoftOAuthTranslation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TokenLoginGui extends GuiScreen {
    private final AltManagerGui parent;
    private GuiTextField tokenField;

    public TokenLoginGui(AltManagerGui parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        int centerX = this.width / 2;
        int fieldWidth = 200;
        int fieldHeight = 20;
        int buttonWidth = 80;
        int buttonHeight = 20;
        int baseY = this.height / 2 - 30;

        this.buttonList.clear();
        this.tokenField = new GuiTextField(0, this.fontRendererObj, centerX - (fieldWidth / 2), baseY, fieldWidth, fieldHeight);
        this.tokenField.setMaxStringLength(32767);

        GuiButton loginButton = new GuiButton(0, centerX - buttonWidth - 5, baseY + fieldHeight + 15, buttonWidth, buttonHeight, "Login");
        GuiButton backButton = new GuiButton(1, centerX + 5, baseY + fieldHeight + 15, buttonWidth, buttonHeight, "Back");

        this.buttonList.add(loginButton);
        this.buttonList.add(backButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawCenteredString(this.fontRendererObj, "Token Login", this.width / 2, 30, 0xFFFFFF);
        this.fontRendererObj.drawStringWithShadow("Enter Access Token or Refresh Token:", this.tokenField.xPosition, this.tokenField.yPosition - 12, 0xAAAAAA);
        this.tokenField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            String token = tokenField.getText().trim();
            if (!token.isEmpty()) {
                loginWithToken(token);
            }
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(parent);
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        this.tokenField.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void loginWithToken(String token) {
        AltManagerGui.status = "§eAnalyzing Token...";
        final String cleanToken = token.trim();

        new Thread(() -> {
            try {
                // 如果是 JWT (eyJ开头)，尝试直接作为 Minecraft Access Token
                if (cleanToken.startsWith("eyJ") || cleanToken.length() > 500) {
                    AltManagerGui.status = "§eAttempting Direct Login...";
                    try {
                        String[] profile = getProfileInfo(cleanToken);
                        handleLoginSuccess(cleanToken, profile[0], profile[1]);
                        return;
                    } catch (IOException e) {
                        mc.addScheduledTask(() -> AltManagerGui.status = "§cInvalid Access Token (401)");
                        return;
                    }
                }

                // 否则尝试作为 Microsoft Refresh Token
                AltManagerGui.status = "§eRefreshing Microsoft Session...";
                MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.login(cleanToken);

                if (loginData.isGood()) {
                    handleLoginSuccess(loginData.mcToken, loginData.username, loginData.uuid);
                } else {
                    mc.addScheduledTask(() -> AltManagerGui.status = "§c" + (loginData.errorMessage != null ? loginData.errorMessage : "Login failed"));
                }

            } catch (Exception e) {
                e.printStackTrace();
                mc.addScheduledTask(() -> AltManagerGui.status = "§cLogin Failed: " + e.getMessage());
            }
        }).start();
    }

    private void handleLoginSuccess(String token, String username, String uuid) {
        SessionChanger.instance().setSessionWithData(new MicrosoftOAuthTranslation.LoginData(token, null, uuid, username));

        mc.addScheduledTask(() -> {
            AltManagerGui.status = "§aLogged in as " + username;

            // 更新或添加账户
            Alt existingAlt = null;
            for (Alt alt : AltManagerGui.getAlts()) {
                if (alt.getName() != null && alt.getName().equals(username)) {
                    existingAlt = alt;
                    break;
                }
            }

            if (existingAlt != null) {
                existingAlt.setUuid(uuid);
                existingAlt.setRefreshToken(token);
            } else {
                Alt alt = new Alt(username, "", username, false);
                alt.setUuid(uuid);
                alt.setRefreshToken(token);
                AltManagerGui.getAlts().add(alt);
            }

            AltManagerGui.saveAltsStatic();
            this.mc.displayGuiScreen(parent);
        });
    }

    private String[] getProfileInfo(String token) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("https://api.minecraftservices.com/minecraft/profile");
            request.setHeader("Authorization", "Bearer " + token);

            try (CloseableHttpResponse response = client.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String jsonString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                if (statusCode != 200) {
                    throw new IOException("API returned " + statusCode + ": " + jsonString);
                }

                JsonParser parser = new JsonParser();
                JsonObject json = parser.parse(jsonString).getAsJsonObject();

                if (!json.has("name") || !json.has("id")) {
                    throw new IOException("Invalid JSON response (Missing name/id): " + jsonString);
                }

                String username = json.get("name").getAsString();
                String uuid = json.get("id").getAsString();
                return new String[]{username, uuid};
            }
        }
    }
}
