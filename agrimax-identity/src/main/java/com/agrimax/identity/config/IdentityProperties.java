package com.agrimax.identity.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "agrimax.identity")
@Getter @Setter
public class IdentityProperties {

    private Cookie cookie = new Cookie();
    private Lockout lockout = new Lockout();
    private Bootstrap bootstrap = new Bootstrap();
    private Email email = new Email();
    private TwoFactor twoFactor = new TwoFactor();

    @Getter @Setter
    public static class Cookie {
        private String refreshTokenName = "refresh_token";
        private boolean secure = false;
        private boolean httpOnly = true;
        private String domain = "";
        private String sameSite = "Lax";
    }

    @Getter @Setter
    public static class Lockout {
        private boolean enabled = true;
        private int maxFailedAttempts = 5;
        private int lockoutDurationMinutes = 15;
        private int resetCounterAfterMinutes = 30;
    }

    @Getter @Setter
    public static class Bootstrap {
        private Admin admin = new Admin();

        @Getter @Setter
        public static class Admin {
            private boolean enabled = false;
            private String email = "";
            private String password = "";
            private String name = "Administrator";
        }
    }

    @Getter @Setter
    public static class Email {
        private String from = "noreply@agrimax.local";
        private String fromName = "AgriMax";
        private String frontendBaseUrl = "http://localhost:3000";
    }

    @Getter @Setter
    public static class TwoFactor {
        private String issuer = "AgriMax";
        private int backupCodeCount = 10;
    }
}