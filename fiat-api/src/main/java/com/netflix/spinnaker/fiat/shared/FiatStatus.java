/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FiatStatus {
  private final Logger log = LoggerFactory.getLogger(FiatStatus.class);

  private final DynamicConfigService dynamicConfigService;
  private final FiatClientConfigurationProperties fiatClientConfigurationProperties;

  private final AtomicBoolean enabled;
  private final AtomicBoolean legacyFallbackEnabled;

  @Autowired
  public FiatStatus(Registry registry,
                    DynamicConfigService dynamicConfigService,
                    FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    this.dynamicConfigService = dynamicConfigService;
    this.fiatClientConfigurationProperties = fiatClientConfigurationProperties;

    this.enabled = new AtomicBoolean(fiatClientConfigurationProperties.isEnabled());
    this.legacyFallbackEnabled = new AtomicBoolean(fiatClientConfigurationProperties.isLegacyFallback());

    registry.gauge("fiat.enabled", enabled, value -> enabled.get() ? 1 : 0);
    registry.gauge("fiat.legacyFallback.enabled", legacyFallbackEnabled, value -> legacyFallbackEnabled.get() ? 1 : 0);
  }

  public boolean isEnabled() {
    return enabled.get();
  }

  public boolean isLegacyFallbackEnabled() {
    return legacyFallbackEnabled.get();
  }

  @Scheduled(fixedDelay = 30000L)
  void refreshStatus() {
    try {
      if (!fiatClientConfigurationProperties.isRefreshable()) {
        return;
      }

      enabled.set(dynamicConfigService.isEnabled("fiat", fiatClientConfigurationProperties.isEnabled()));
      legacyFallbackEnabled.set(
          dynamicConfigService.isEnabled("fiat.legacyFallback", fiatClientConfigurationProperties.isLegacyFallback())
      );
    } catch (Exception e) {
      log.warn("Unable to refresh fiat status, reason: {}", e.getMessage());
    }
  }
}
