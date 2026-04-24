package org.enoria.mockbrevo.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.enoria.mockbrevo.domain.Account;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class CurrentAccount {

    public static final String REQUEST_ATTR = "mock-brevo.currentAccount";

    private CurrentAccount() {}

    public static Account require() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("No request bound");
        }
        HttpServletRequest request = attrs.getRequest();
        Account account = (Account) request.getAttribute(REQUEST_ATTR);
        if (account == null) {
            throw new IllegalStateException("Account not resolved — is the interceptor registered?");
        }
        return account;
    }
}
