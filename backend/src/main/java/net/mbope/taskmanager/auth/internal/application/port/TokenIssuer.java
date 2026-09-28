package net.mbope.taskmanager.auth.internal.application.port;

import net.mbope.taskmanager.auth.internal.application.TokenResponse;
import net.mbope.taskmanager.user.AccountIdentity;

public interface TokenIssuer {
    TokenResponse issue(AccountIdentity identity);
}
