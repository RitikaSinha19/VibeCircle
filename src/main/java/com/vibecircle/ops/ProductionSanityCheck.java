package com.vibecircle.ops;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** In production the application refuses to start with development defaults. */
@Component
@Profile("prod")
public class ProductionSanityCheck implements InitializingBean {

    private final String jwtSecret;
    private final String dbPassword;
    private final String corsOrigins;
    private final String wsOrigins;

    public ProductionSanityCheck(@Value("${vibecircle.auth.jwt-secret:}") String jwtSecret,
                                 @Value("${spring.datasource.password:}") String dbPassword,
                                 @Value("${vibecircle.cors.allowed-origins:}") String corsOrigins,
                                 @Value("${vibecircle.ws.allowed-origins:}") String wsOrigins) {
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
        this.corsOrigins = corsOrigins;
        this.wsOrigins = wsOrigins;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = ProductionSettingsValidator.problems(jwtSecret, dbPassword, corsOrigins, wsOrigins);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with unsafe production settings:\n - "
                    + String.join("\n - ", problems));
        }
    }
}
