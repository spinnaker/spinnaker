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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties("services.fiat")
public class FiatClientConfigurationProperties {
  @Autowired
  private DynamicConfigService dynamicConfigService;

  private boolean enabled;

  private String baseUrl;

  private boolean legacyFallback = false;

  private boolean refreshable = true;

  private Integer connectTimeoutMs;

  private Integer readTimeoutMs;

  @NestedConfigurationProperty
  private PermissionsCache cache = new PermissionsCache();

  @NestedConfigurationProperty
  private RetryConfiguration retry = new RetryConfiguration();

  public RetryConfiguration getRetry() {
    retry.setDynamicConfigService(dynamicConfigService);
    return retry;
  }

  @Data
  public static class PermissionsCache {
    private Integer maxEntries = 1000;

    private Integer expiresAfterWriteSeconds = 20;
  }

  @Data
  public static class RetryConfiguration {
    private DynamicConfigService dynamicConfigService;
    private long maxBackoffMillis = 10000;
    private long initialBackoffMillis = 500;
    private double retryMultiplier = 1.5;

    public void setDynamicConfigService(DynamicConfigService dynamicConfigService) {
      this.dynamicConfigService = dynamicConfigService;
    }

    public long getMaxBackoffMillis() {
      if (dynamicConfigService == null) {
        return maxBackoffMillis;
      }

      return dynamicConfigService.getConfig(Long.class, "fiat.retry.maxBackoffMillis", maxBackoffMillis);
    }

    public long getInitialBackoffMillis() {
      if (dynamicConfigService == null) {
        return initialBackoffMillis;
      }

      return dynamicConfigService.getConfig(Long.class, "fiat.retry.initialBackoffMillis", initialBackoffMillis);
    }

    public double getRetryMultiplier() {
      if (dynamicConfigService == null) {
        return retryMultiplier;
      }

      return dynamicConfigService.getConfig(Double.class, "fiat.retry.retryMultiplier", retryMultiplier);
    }
  }
}
