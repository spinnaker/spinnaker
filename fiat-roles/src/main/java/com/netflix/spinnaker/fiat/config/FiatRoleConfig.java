/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.roles.Synchronizer;
import com.netflix.spinnaker.fiat.roles.UserRolesSyncStrategy;
import com.netflix.spinnaker.fiat.roles.UserRolesSyncStrategy.CachedSynchronizationStrategy;
import com.netflix.spinnaker.fiat.roles.UserRolesSyncStrategy.DefaultSynchronizationStrategy;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties("fiat.role")
public class FiatRoleConfig {

  private boolean orMode = false;

  @Bean
  @ConditionalOnProperty(
      name = "fiat.role.sync-cache.enabled",
      havingValue = "false",
      matchIfMissing = true)
  @ConditionalOnExpression("${fiat.write-mode.enabled:true}")
  UserRolesSyncStrategy defaultSyncStrategy(Synchronizer synchronizer) {
    return new DefaultSynchronizationStrategy(synchronizer);
  }

  @Bean
  @ConditionalOnProperty(name = "fiat.role.sync-cache.enabled", havingValue = "true")
  @ConditionalOnExpression("${fiat.write-mode.enabled:true}")
  UserRolesSyncStrategy cachedSyncStrategy(Synchronizer synchronizer) {
    return new CachedSynchronizationStrategy(synchronizer);
  }
}
