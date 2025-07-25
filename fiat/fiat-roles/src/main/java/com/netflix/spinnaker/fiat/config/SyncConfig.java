/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for user role synchronization in Fiat.
 *
 * <p>This class contains configuration parameters that control the behavior of user role
 * synchronization, including retry intervals, synchronization delays, and distributed lock
 * settings.
 */
@Data
@Configuration
@ConditionalOnExpression("${fiat.write-mode.enabled:true}")
@ConfigurationProperties("fiat.write-mode")
public class SyncConfig {
  /**
   * The interval in milliseconds between retry attempts when a synchronization attempt fails.
   * Default: 10000 (10 seconds)
   */
  long retryIntervalMs = 10000;

  /**
   * The delay in milliseconds between successful synchronization attempts. This controls how often
   * the system will attempt to sync user roles. Default: 600000 (10 minutes)
   */
  long syncDelayMs = 600000;

  /**
   * The delay in milliseconds between synchronization attempts after a failure occurs. This is
   * typically longer than the normal sync delay to prevent excessive retries. Default: 600000 (10
   * minutes)
   */
  long syncFailureDelayMs = 600000;

  /**
   * The maximum time in milliseconds to wait for a synchronization operation to complete before
   * timing out. This helps prevent synchronization operations from hanging indefinitely. Default:
   * 30000 (30 seconds)
   */
  long syncDelayTimeoutMs = 30000;

  /**
   * The name of the distributed lock used to coordinate synchronization across multiple instances.
   * This lock ensures that only one instance performs the synchronization at a time. Default:
   * "fiat.userrolessyncer"
   */
  String lockName = "Fiat.UserRolesSyncer".toLowerCase();
}
