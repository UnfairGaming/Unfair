package unfair.management.altmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import unfair.management.altmanager.microsoft.MicrosoftOAuthTranslation;

public class SessionChanger {
    public static String username = null;
    private static SessionChanger instance;
    private final Minecraft mc = Minecraft.getMinecraft();
    public long timeSinceFail;

    public static SessionChanger instance() {
        if (instance == null) {
            instance = new SessionChanger();
        }
        return instance;
    }

    public void loginCracked(String n) {
        SessionUtil.setSession(mc, new Session(n, n, "0", "legacy"));
        username = n;
    }

    public void loginMicrosoft(String email, String password) {
        new Thread(() -> {
            AltManagerGui.status = "§6Logging in...";
            try {
                MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.loginWithCredentials(email, password);
                if (loginData.isGood()) {
                    setSessionWithData(loginData);
                    AltManagerGui.status = "§aLogged in as " + loginData.username;
                } else {
                    System.out.println("Failed login: " + loginData.errorMessage);
                    timeSinceFail = System.currentTimeMillis();
                    AltManagerGui.status = "§c" + (loginData.errorMessage != null ? loginData.errorMessage : "Login failed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                timeSinceFail = System.currentTimeMillis();
                AltManagerGui.status = "§cLogin error: " + e.getMessage();
            }
        }).start();
    }

    public void loginWithRefreshToken(String refreshToken) {
        new Thread(() -> {
            AltManagerGui.status = "§6Logging in with OAuth...";
            MicrosoftOAuthTranslation.LoginData loginData = MicrosoftOAuthTranslation.login(refreshToken);

            if (loginData.isGood()) {
                setSessionWithData(loginData);
                AltManagerGui.status = "§aLogged in as " + loginData.username;
            } else {
                System.out.println("OAuth login failed: " + loginData.errorMessage);
                timeSinceFail = System.currentTimeMillis();
                AltManagerGui.status = "§c" + (loginData.errorMessage != null ? loginData.errorMessage : "OAuth login failed");
            }
        }).start();
    }

    public void setSessionWithData(MicrosoftOAuthTranslation.LoginData loginData) {
        SessionUtil.setSession(mc, new Session(loginData.username, loginData.uuid, loginData.mcToken, "mojang"));
        username = loginData.username;
        System.out.println("Login successful: " + loginData.username);
    }
}
