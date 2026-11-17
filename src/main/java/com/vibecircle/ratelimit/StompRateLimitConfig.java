package com.vibecircle.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** A second configurer, so the existing WebSocketConfig does not need to change. */
@Configuration
public class StompRateLimitConfig implements WebSocketMessageBrokerConfigurer {

    private final RateLimiter limiter;
    private final RateLimitProperties props;

    public StompRateLimitConfig(RateLimiter limiter, RateLimitProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompRateLimitInterceptor(limiter, props));
    }
}
