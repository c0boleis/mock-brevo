package org.enoria.mockbrevo.config;

import org.enoria.mockbrevo.auth.ApiKeyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyInterceptor apiKeyInterceptor;

    public WebConfig(ApiKeyInterceptor apiKeyInterceptor) {
        this.apiKeyInterceptor = apiKeyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/v3/**");
    }

    /**
     * Brevo-style deep-link URLs. When a client (e.g. Enoria's ApiBrevoService) links
     * to "app.brevo.com/marketing-campaign/edit/42", we rewrite the base URL to this
     * mock's UI — so the FULL Brevo path is preserved. The frontend JS inspects
     * window.location.pathname to render the matching detail view.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/marketing-campaign/edit/{id:[0-9]+}")
                .setViewName("forward:/index.html");
        registry.addViewController("/contact/list/id/{id:[0-9]+}")
                .setViewName("forward:/index.html");
    }
}
