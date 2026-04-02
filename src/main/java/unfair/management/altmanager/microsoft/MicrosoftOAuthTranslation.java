package unfair.management.altmanager.microsoft;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MicrosoftOAuthTranslation {

    private static final String CLIENT_ID = "9fbc7315-7200-4b2b-a655-bb38c865da17";
    private static final String CLIENT_SECRET = "Bzn8Q~YryydJsydgnnxHgJq.NM3Oo4.AEEohLbBb";
    private static final int PORT = 8247;
    static ExecutorService executor = Executors.newCachedThreadPool();
    static Gson gson = new Gson();
    private static SimpleHttpServer server;
    private static Consumer<String> callback;

    static void browse(String url) {
        try {
            System.out.println("Opening browser to: " + url);
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException e) {
            System.err.println("Failed to open browser: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void getRefreshToken(Consumer<String> callback) {
        System.out.println("Starting OAuth flow...");
        MicrosoftOAuthTranslation.callback = callback;

        if (!startServer()) {
            callback.accept(null);
            return;
        }

        String authUrl = "https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID +
                "&response_type=code&redirect_uri=http://localhost:" + PORT +
                "&scope=XboxLive.signin%20offline_access" +
                "&prompt=select_account";
        browse(authUrl);
    }

    public static LoginData login(String refreshToken) {
        System.out.println("Logging in with refresh token...");

        if (refreshToken == null || refreshToken.isEmpty()) {
            System.err.println("No refresh token provided");
            LoginData result = new LoginData();
            result.errorMessage = "No refresh token provided";
            return result;
        }

        try {
            System.out.println("Refreshing access token...");
            String tokenRequest = "client_id=" + CLIENT_ID +
                    "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.name()) +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8.name()) +
                    "&grant_type=refresh_token" +
                    "&redirect_uri=http://localhost:" + PORT;

            String tokenResponse = postExternal("https://login.live.com/oauth20_token.srf", tokenRequest, false);

            if (tokenResponse == null) {
                System.err.println("Failed to refresh access token - null response");
                LoginData result = new LoginData();
                result.errorMessage = "Failed to refresh access token";
                return result;
            }

            System.out.println("Token response: " + tokenResponse);
            AuthTokenResponse res = gson.fromJson(tokenResponse, AuthTokenResponse.class);
            if (res == null || res.access_token == null) {
                if (res != null && res.error != null) {
                    System.err.println("Token error: " + res.error + " - " + res.error_description);
                    LoginData result = new LoginData();
                    result.errorMessage = "Microsoft auth error: " + res.error_description;
                    return result;
                }
                System.err.println("Invalid token response: " + tokenResponse);
                LoginData result = new LoginData();
                result.errorMessage = "Invalid token response from Microsoft";
                return result;
            }

            String accessToken = res.access_token;
            String newRefreshToken = res.refresh_token != null ? res.refresh_token : refreshToken;

            System.out.println("Got access token, proceeding to XBL...");

            String xblPayload = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + accessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
            String xblResponse = postExternal("https://user.auth.xboxlive.com/user/authenticate", xblPayload, true);

            if (xblResponse == null) {
                System.err.println("XBL authentication failed - null response");
                LoginData result = new LoginData();
                result.errorMessage = "XBL authentication failed";
                return result;
            }

            XblXstsResponse xblRes = gson.fromJson(xblResponse, XblXstsResponse.class);
            if (xblRes == null || xblRes.Token == null || xblRes.DisplayClaims == null ||
                    xblRes.DisplayClaims.xui == null || xblRes.DisplayClaims.xui.length == 0) {
                System.err.println("Invalid XBL response: " + xblResponse);
                LoginData result = new LoginData();
                result.errorMessage = "Invalid XBL response";
                return result;
            }

            System.out.println("XBL successful, proceeding to XSTS...");

            String xstsPayload = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblRes.Token + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
            String xstsResponse = postExternal("https://xsts.auth.xboxlive.com/xsts/authorize", xstsPayload, true);

            if (xstsResponse == null) {
                System.err.println("XSTS authentication failed - null response");
                LoginData result = new LoginData();
                result.errorMessage = "XSTS authentication failed";
                return result;
            }

            XblXstsResponse xstsRes = gson.fromJson(xstsResponse, XblXstsResponse.class);
            if (xstsRes == null || xstsRes.Token == null) {
                System.err.println("Invalid XSTS response: " + xstsResponse);
                if (xstsResponse.contains("XErr")) {
                    if (xstsResponse.contains("2148916233")) {
                        System.err.println("No Xbox account associated with Microsoft account");
                        LoginData result = new LoginData();
                        result.errorMessage = "No Xbox account found. Please create one at https://www.xbox.com";
                        return result;
                    } else if (xstsResponse.contains("2148916235")) {
                        System.err.println("Xbox Live is banned in country");
                        LoginData result = new LoginData();
                        result.errorMessage = "Xbox Live is not available in your country";
                        return result;
                    } else if (xstsResponse.contains("2148916238")) {
                        System.err.println("Adult verification required");
                        LoginData result = new LoginData();
                        result.errorMessage = "Adult verification required. Please complete at https://xbox.com";
                        return result;
                    }
                }
                LoginData result = new LoginData();
                result.errorMessage = "Invalid XSTS response";
                return result;
            }

            System.out.println("XSTS successful, proceeding to Minecraft...");

            String uhs = xblRes.DisplayClaims.xui[0].uhs;
            String mcPayload = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsRes.Token + "\"}";
            String mcResponse = postExternal("https://api.minecraftservices.com/authentication/login_with_xbox", mcPayload, true);

            if (mcResponse == null) {
                System.err.println("Minecraft authentication failed - null response");
                LoginData result = new LoginData();
                result.errorMessage = "Minecraft authentication failed";
                return result;
            }

            McResponse mcRes = gson.fromJson(mcResponse, McResponse.class);
            if (mcRes == null || mcRes.access_token == null) {
                System.err.println("Invalid Minecraft response: " + mcResponse);
                LoginData result = new LoginData();
                result.errorMessage = "Invalid Minecraft response";
                return result;
            }

            System.out.println("Minecraft auth successful, checking ownership...");

            String ownershipResponse = getBearerResponse("https://api.minecraftservices.com/entitlements/mcstore", mcRes.access_token);
            if (ownershipResponse == null) {
                System.err.println("Ownership check failed - null response");
                LoginData result = new LoginData();
                result.errorMessage = "Ownership check failed";
                return result;
            }

            GameOwnershipResponse gameOwnershipRes = gson.fromJson(ownershipResponse, GameOwnershipResponse.class);
            if (gameOwnershipRes == null || !gameOwnershipRes.hasGameOwnership()) {
                System.err.println("No game ownership found: " + ownershipResponse);
                LoginData result = new LoginData();
                result.errorMessage = "No Minecraft license found on this account";
                return result;
            }

            System.out.println("Ownership verified, getting profile...");

            String profileResponse = getBearerResponse("https://api.minecraftservices.com/minecraft/profile", mcRes.access_token);
            if (profileResponse == null) {
                System.err.println("Profile retrieval failed - null response");
                LoginData result = new LoginData();
                result.errorMessage = "Profile retrieval failed";
                return result;
            }

            ProfileResponse profileRes = gson.fromJson(profileResponse, ProfileResponse.class);
            if (profileRes == null || profileRes.id == null || profileRes.name == null) {
                System.err.println("Invalid profile response: " + profileResponse);
                LoginData result = new LoginData();
                result.errorMessage = "Invalid profile response";
                return result;
            }

            System.out.println("Login successful for: " + profileRes.name);
            return new LoginData(mcRes.access_token, newRefreshToken, profileRes.id, profileRes.name);

        } catch (Exception e) {
            System.err.println("Unexpected error during login: " + e.getMessage());
            e.printStackTrace();
            LoginData result = new LoginData();
            result.errorMessage = "Unexpected error: " + e.getMessage();
            return result;
        }
    }

    public static LoginData loginWithCredentials(String email, String password) {
        System.out.println("Logging in with credentials...");
        try {
            String tokenRequest = "client_id=" + CLIENT_ID +
                    "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.name()) +
                    "&grant_type=password" +
                    "&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8.name()) +
                    "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8.name()) +
                    "&scope=XboxLive.signin%20offline_access";

            String tokenResponse = postExternal("https://login.live.com/oauth20_token.srf", tokenRequest, false);

            if (tokenResponse == null) {
                LoginData result = new LoginData();
                result.errorMessage = "Failed to get token";
                return result;
            }

            AuthTokenResponse res = gson.fromJson(tokenResponse, AuthTokenResponse.class);
            if (res == null || res.refresh_token == null) {
                if (res != null && res.error != null) {
                    LoginData result = new LoginData();
                    result.errorMessage = "Auth error: " + res.error_description;
                    return result;
                }
                LoginData result = new LoginData();
                result.errorMessage = "Invalid credentials";
                return result;
            }

            return login(res.refresh_token);
        } catch (Exception e) {
            e.printStackTrace();
            LoginData result = new LoginData();
            result.errorMessage = "Login error: " + e.getMessage();
            return result;
        }
    }

    private static boolean startServer() {
        if (server != null) {
            server.stop();
        }

        try {
            server = new SimpleHttpServer(PORT);
            boolean started = server.start();
            if (started) {
                System.out.println("Local server started on port " + PORT);
                return true;
            } else {
                System.err.println("Failed to start local server on port " + PORT);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static void stopServer() {
        if (server == null) return;

        server.stop();
        server = null;
        System.out.println("Local server stopped");
    }

    private static void handleCode(String code) {
        System.out.println("Exchanging code for token...");

        try {
            String tokenRequest = "client_id=" + CLIENT_ID +
                    "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8.name()) +
                    "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8.name()) +
                    "&grant_type=authorization_code" +
                    "&redirect_uri=http://localhost:" + PORT;

            String response = postExternal("https://login.live.com/oauth20_token.srf", tokenRequest, false);

            if (response == null) {
                System.err.println("Failed to exchange code for token - null response");
                if (callback != null) callback.accept(null);
                return;
            }

            System.out.println("Token exchange response: " + response);
            AuthTokenResponse res = gson.fromJson(response, AuthTokenResponse.class);
            if (res == null || res.refresh_token == null) {
                if (res != null && res.error != null) {
                    System.err.println("Token exchange error: " + res.error + " - " + res.error_description);
                }
                System.err.println("Invalid token exchange response");
                if (callback != null) callback.accept(null);
            } else {
                System.out.println("Successfully obtained refresh token");
                if (callback != null) callback.accept(res.refresh_token);
            }
        } catch (Exception e) {
            System.err.println("Error in handleCode: " + e.getMessage());
            e.printStackTrace();
            if (callback != null) callback.accept(null);
        }
    }

    private static String postExternal(String url, String post, boolean json) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);

            connection.addRequestProperty("User-Agent", "Mozilla/5.0");
            connection.addRequestProperty("Content-Type", json ? "application/json" : "application/x-www-form-urlencoded");
            connection.addRequestProperty("Accept", "application/json");

            byte[] out = post.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(out.length);

            System.out.println("Sending POST to: " + url);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(out);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response code: " + responseCode);

            InputStream stream = responseCode < 400 ? connection.getInputStream() : connection.getErrorStream();

            if (stream == null) {
                System.err.println("No response stream");
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                String responseBody = response.toString();
                if (responseCode >= 400) {
                    System.err.println("HTTP Error " + responseCode + ": " + responseBody);
                }
                return responseBody;
            }
        } catch (Exception e) {
            System.err.println("HTTP POST error for " + url + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static String getBearerResponse(String url, String bearer) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.addRequestProperty("User-Agent", "Mozilla/5.0");
            connection.addRequestProperty("Authorization", "Bearer " + bearer);
            connection.addRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            System.out.println("Bearer request to " + url + " - Response: " + responseCode);

            InputStream stream = responseCode == 200 ? connection.getInputStream() : connection.getErrorStream();

            if (stream == null) {
                System.err.println("No response stream for bearer request");
                return null;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                String responseBody = response.toString();
                if (responseCode != 200) {
                    System.err.println("Bearer request failed: " + responseBody);
                }
                return responseBody;
            }
        } catch (Exception e) {
            System.err.println("Bearer request error for " + url + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public static class LoginData {
        public String mcToken;
        public String newRefreshToken;
        public String uuid, username;
        public String errorMessage;

        public LoginData() {
        }

        public LoginData(String mcToken, String newRefreshToken, String uuid, String username) {
            this.mcToken = mcToken;
            this.newRefreshToken = newRefreshToken;
            this.uuid = uuid;
            this.username = username;
        }

        public boolean isGood() {
            return mcToken != null && errorMessage == null;
        }
    }

    private static class SimpleHttpServer {
        private final int port;
        private ServerSocket serverSocket;
        private boolean running;

        public SimpleHttpServer(int port) {
            this.port = port;
        }

        public boolean start() throws IOException {
            try {
                serverSocket = new ServerSocket(port, 1, InetAddress.getByName("localhost"));
                running = true;
                executor.execute(this::run);
                System.out.println("Server socket created successfully");
                return true;
            } catch (BindException e) {
                System.err.println("Port " + port + " is already in use: " + e.getMessage());
                return false;
            } catch (IOException e) {
                System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
                return false;
            }
        }

        public void stop() {
            running = false;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    System.out.println("Server socket closed");
                }
            } catch (IOException e) {
                System.err.println("Error closing server: " + e.getMessage());
            }
        }

        private void run() {
            System.out.println("Server listening on port " + port);
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Received connection from: " + clientSocket.getInetAddress());
                    executor.execute(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Server error: " + e.getMessage());
                    }
                }
            }
        }

        private void handleClient(Socket clientSocket) {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                String requestLine = in.readLine();
                if (requestLine == null) {
                    System.out.println("Empty request");
                    return;
                }

                System.out.println("Request: " + requestLine);

                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                }

                String[] requestParts = requestLine.split(" ");
                if (requestParts.length < 2) {
                    System.out.println("Invalid request format");
                    return;
                }

                String method = requestParts[0];
                String path = requestParts[1];

                if ("GET".equals(method)) {
                    Map<String, String> params = new HashMap<>();
                    int queryStart = path.indexOf('?');
                    if (queryStart != -1) {
                        String query = path.substring(queryStart + 1);
                        System.out.println("Query string: " + query);
                        for (String param : query.split("&")) {
                            String[] pair = param.split("=");
                            if (pair.length == 2) {
                                params.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
                            } else if (pair.length == 1) {
                                params.put(pair[0], "");
                            }
                        }
                    }

                    String code = params.get("code");
                    String error = params.get("error");
                    String responseHtml;

                    if (code != null && !code.isEmpty()) {
                        System.out.println("Received auth code: " + code);
                        handleCode(code);
                        responseHtml = "<html><body style='font-family: Arial, sans-serif; text-align: center; padding: 50px;'><h1 style='color: green;'>✓ Authentication Successful!</h1><p>You may now close this page and return to the game.</p><script>setTimeout(function() { window.close(); }, 2000);</script></body></html>";
                    } else if (error != null) {
                        System.out.println("Received error: " + error + " - " + params.get("error_description"));
                        responseHtml = "<html><body style='font-family: Arial, sans-serif; text-align: center; padding: 50px;'><h1 style='color: red;'>✗ Authentication Failed</h1><p>Error: " + error + "</p><p>Please try again.</p></body></html>";
                        if (callback != null) callback.accept(null);
                    } else {
                        System.out.println("No auth code or error received");
                        responseHtml = "<html><body style='font-family: Arial, sans-serif; text-align: center; padding: 50px;'><h1 style='color: orange;'>⚠ Authentication Incomplete</h1><p>No authentication code received. Please try again.</p></body></html>";
                    }

                    String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: " + responseHtml.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                            "Connection: close\r\n\r\n" +
                            responseHtml;

                    out.write(response.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    System.out.println("Response sent to browser");

                    executor.execute(() -> {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        stopServer();
                    });
                }

            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }
    }

    private static class AuthTokenResponse {
        @Expose
        @SerializedName("access_token")
        public String access_token;
        @Expose
        @SerializedName("refresh_token")
        public String refresh_token;
        @Expose
        @SerializedName("error")
        public String error;
        @Expose
        @SerializedName("error_description")
        public String error_description;
    }

    private static class XblXstsResponse {
        @Expose
        @SerializedName("Token")
        public String Token;
        @Expose
        @SerializedName("DisplayClaims")
        public DisplayClaims DisplayClaims;

        private static class DisplayClaims {
            @Expose
            @SerializedName("xui")
            public Claim[] xui;

            private static class Claim {
                @Expose
                @SerializedName("uhs")
                public String uhs;
            }
        }
    }

    private static class McResponse {
        @Expose
        @SerializedName("access_token")
        public String access_token;
    }

    private static class GameOwnershipResponse {
        @Expose
        @SerializedName("items")
        public Item[] items;

        private boolean hasGameOwnership() {
            if (items == null) return false;
            for (Item item : items) {
                if (item.name.equals("product_minecraft") || item.name.equals("game_minecraft")) {
                    return true;
                }
            }
            return false;
        }

        private static class Item {
            @Expose
            @SerializedName("name")
            public String name;
        }
    }

    private static class ProfileResponse {
        @Expose
        @SerializedName("id")
        public String id;
        @Expose
        @SerializedName("name")
        public String name;
    }
}
