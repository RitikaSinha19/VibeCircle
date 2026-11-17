package com.vibecircle.account;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** vibecircle.account.purge-after: how long a deleted account is kept (anonymised) before its data is erased. */
@ConfigurationProperties(prefix = "vibecircle.account")
public record AccountProperties(@DefaultValue("P30D") Duration purgeAfter) {
}
