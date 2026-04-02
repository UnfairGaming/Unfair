package unfair.management.altmanager.auth.model.response;

public class XboxLoginResponse {
    private final String IssueInstant;
    private final String NotAfter;
    private final String Token;
    private final XboxLiveLoginResponseClaims DisplayClaims;

    public XboxLoginResponse(String IssueInstant, String NotAfter, String Token, XboxLiveLoginResponseClaims DisplayClaims) {
        this.IssueInstant = IssueInstant;
        this.NotAfter = NotAfter;
        this.Token = Token;
        this.DisplayClaims = DisplayClaims;
    }

    public String getIssueInstant() {
        return IssueInstant;
    }

    public String getNotAfter() {
        return NotAfter;
    }

    public String getToken() {
        return Token;
    }

    public XboxLiveLoginResponseClaims getDisplayClaims() {
        return DisplayClaims;
    }

    public static class XboxLiveLoginResponseClaims {
        private final XboxLiveUserInfo[] xui;

        public XboxLiveLoginResponseClaims(XboxLiveUserInfo[] xui) {
            this.xui = xui;
        }

        public XboxLiveUserInfo[] getUsers() {
            return xui;
        }
    }

    public static class XboxLiveUserInfo {
        private final String uhs;

        public XboxLiveUserInfo(String uhs) {
            this.uhs = uhs;
        }

        public String getUserHash() {
            return uhs;
        }
    }
}
