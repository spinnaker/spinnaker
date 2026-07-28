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

package com.netflix.spinnaker.security.roles.config;

import com.netflix.spinnaker.security.roles.UserRolesProvider;
import com.netflix.spinnaker.security.roles.UserRolesResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@link UserRolesResolver} entrypoint (role-provider resolution + external-group
 * merge) for consumers such as Gate login and the SA/run-as token minter. The merge is toggleable
 * via {@code authz.roles.merge-external-roles}.
 */
@Configuration
@EnableConfigurationProperties(RoleResolutionProperties.class)
public class RoleResolutionConfiguration {

  @Bean
  @ConditionalOnBean(UserRolesProvider.class)
  @ConditionalOnMissingBean(UserRolesResolver.class)
  public UserRolesResolver userRolesResolver(
      UserRolesProvider userRolesProvider, RoleResolutionProperties properties) {
    return new UserRolesResolver(userRolesProvider, properties.isMergeExternalRoles());
  }
}
