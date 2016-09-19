package com.netflix.spinnaker.fiat.roles.github;

import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Helper class to map masters in properties file into a validated property map
 */
@Configuration
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
@ConfigurationProperties(prefix = "auth.groupMembership.github")
@Data
public class GitHubProperties {
  @NotEmpty
  private String baseUrl;
  @NotEmpty
  private String accessToken;
  @NotEmpty
  private String organization;
}
