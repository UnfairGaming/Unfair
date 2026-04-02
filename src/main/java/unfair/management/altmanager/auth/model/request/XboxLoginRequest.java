package unfair.management.altmanager.auth.model.request;

public class XboxLoginRequest<T> {
    private final T Properties;
    private final String RelyingParty;
    private final String TokenType;

    public XboxLoginRequest(T Properties, String RelyingParty, String TokenType) {
        this.Properties = Properties;
        this.RelyingParty = RelyingParty;
        this.TokenType = TokenType;
    }

    public T getProperties() {
        return Properties;
    }

    public String getSiteName() {
        return RelyingParty;
    }

    public String getTokenType() {
        return TokenType;
    }
}
