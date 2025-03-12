/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rollback")
public class RollbackConfigurationProperties {

  private static final Logger logger =
      LoggerFactory.getLogger(RollbackConfigurationProperties.class);

  @Value("${rollback.timeout.enabled:false}")
  private boolean dynamicRollbackEnabled;

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "rollback.dynamicRollback.enabled")
  public boolean getDynamicRollbackEnabled() {
    return dynamicRollbackEnabled;
  }

  @NestedConfigurationProperty private ExplicitRollback explicitRollback = new ExplicitRollback();

  @NestedConfigurationProperty private DynamicRollback dynamicRollback = new DynamicRollback();

  @Getter
  @Setter
  @NoArgsConstructor
  public static class ExplicitRollback {
    private int timeout = 5;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static class DynamicRollback {
    private boolean enabled = false;
  }

  public boolean isDynamicRollbackEnabled() {
    if (getDynamicRollbackEnabled()) {
      logger.warn(
          "The rollback.timeout.enabled property is deprecated and will be removed in a future release. Please use rollback.dynamicRollback.enabled instead.");
    }
    return dynamicRollback.isEnabled() || getDynamicRollbackEnabled();
  }
}
