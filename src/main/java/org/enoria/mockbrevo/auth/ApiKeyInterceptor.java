package org.enoria.mockbrevo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.enoria.mockbrevo.domain.Account;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final String HEADER = "api-key";

    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public ApiKeyInterceptor(AccountService accountService, ObjectMapper objectMapper) {
        this.accountService = accountService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeUnauthorized(response);
            return false;
        }
        Account account = accountService.resolveOrProvision(key.trim());
        request.setAttribute(CurrentAccount.REQUEST_ATTR, account);
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                Map.of("code", "unauthorized", "message", "Key not found"));
    }
}
