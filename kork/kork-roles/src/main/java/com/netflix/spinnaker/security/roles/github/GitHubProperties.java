/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.roles.github;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Validated property map for the GitHub Teams role provider. */
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
