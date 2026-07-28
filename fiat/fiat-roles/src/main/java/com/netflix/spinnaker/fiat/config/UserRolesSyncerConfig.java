/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConditionalOnExpression("${fiat.write-mode.enabled:true}")
@ConfigurationProperties("fiat.write-mode")
public class UserRolesSyncerConfig {
  long retryIntervalMs = 10000;
  long syncDelayMs = 600000;
  long syncFailureDelayMs = 600000;
  long syncDelayTimeoutMs = 30000;

  @NestedConfigurationProperty
  private SynchronizationConfig synchronizationConfig = new SynchronizationConfig();

  @Data
  public static class SynchronizationConfig {
    private boolean enabled;
    private String prefix = "spinnaker:fiat";
    private long syncDelayMs = 1000;
    private long syncFailureDelayMs = 1000;
    private long maxLockDurationMs = 600000;
  }
}
