package com.netflix.spinnaker.fiat.roles.github;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Helper class to map masters in properties file into a validated property map */
@Configuration
@ConditionalOnProperty(value = "auth.group-membership.service", havingValue = "github")
@ConfigurationProperties(prefix = "auth.group-membership.github")
@Data
public class GitHubProperties {
  @NotEmpty private String baseUrl;
  @NotEmpty private String accessToken;
  @NotEmpty private String organization;

  @NotNull
  @Max(100L)
  @Min(1L)
  Integer paginationValue = 100;

  @NotNull Integer membershipCacheTTLSeconds = 60 * 10; // 10 min time to refresh
  @NotNull Integer membershipCacheTeamsSize = 1000; // 1000 github teams
}
