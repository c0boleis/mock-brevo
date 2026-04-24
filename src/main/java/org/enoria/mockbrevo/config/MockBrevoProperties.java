package org.enoria.mockbrevo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock-brevo")
public class MockBrevoProperties {

    private boolean revealKeys = true;
    private String defaultWebhookUrl = "";
    private boolean autoFireDelivered = false;
    private Smtp smtp = new Smtp();

    public boolean isRevealKeys() { return revealKeys; }
    public void setRevealKeys(boolean revealKeys) { this.revealKeys = revealKeys; }

    public String getDefaultWebhookUrl() { return defaultWebhookUrl; }
    public void setDefaultWebhookUrl(String defaultWebhookUrl) { this.defaultWebhookUrl = defaultWebhookUrl; }

    public boolean isAutoFireDelivered() { return autoFireDelivered; }
    public void setAutoFireDelivered(boolean autoFireDelivered) { this.autoFireDelivered = autoFireDelivered; }

    public Smtp getSmtp() { return smtp; }
    public void setSmtp(Smtp smtp) { this.smtp = smtp; }

    public static class Smtp {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 1025;
        private String username = "";
        private String password = "";
        private boolean starttls = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isStarttls() { return starttls; }
        public void setStarttls(boolean starttls) { this.starttls = starttls; }
    }
}
