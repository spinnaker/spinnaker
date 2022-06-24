package com.netflix.spinnaker.fiat.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configures Fiat aspects of the account management API. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("fiat.account.manager")
@Data
public class AccountManagerConfig {

  /** List of roles allowed to programmatically manage accounts. */
  private List<String> roles = List.of();
}
