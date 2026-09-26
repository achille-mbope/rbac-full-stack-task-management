package net.mbope.taskmanager.auth.internal.application;

import net.mbope.taskmanager.auth.internal.application.port.TokenIssuer;
import net.mbope.taskmanager.user.UserCredentials;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private final UserCredentials credentials;
    private final TokenIssuer tokens;

    public LoginService(UserCredentials credentials, TokenIssuer tokens) {
        this.credentials = credentials;
        this.tokens = tokens;
    }

    public TokenResponse login(String email, String password) {
        var identity = credentials.verify(email, password).orElseThrow(LoginFailedException::new);
        return tokens.issue(identity);
    }
}
