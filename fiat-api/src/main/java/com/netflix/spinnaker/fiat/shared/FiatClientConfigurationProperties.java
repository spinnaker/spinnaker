/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties("services.fiat")
public class FiatClientConfigurationProperties {

  private boolean enabled;

  private String baseUrl;

  private boolean legacyFallback = false;

  private boolean refreshable = true;

  @NestedConfigurationProperty
  private PermissionsCache cache = new PermissionsCache();

  @NestedConfigurationProperty
  private RetryConfiguration retry = new RetryConfiguration();

  @Data
  class PermissionsCache {
    private Integer maxEntries = 1000;

    private Integer expiresAfterWriteSeconds = 20;
  }

  @Data
  class RetryConfiguration {
    private long maxBackoffMillis = 10000;
    private long initialBackoffMillis = 500;
    private double retryMultiplier = 1.5;
  }
}
