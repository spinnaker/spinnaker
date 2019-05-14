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
package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("locking")
public class LockingConfigurationProperties {
  private boolean learningMode = true;
  private boolean enabled = false;
  private int ttlSeconds = 120;
  private int backoffBufferSeconds = 10;
  private final DynamicConfigService dynamicConfigService;

  @Autowired
  public LockingConfigurationProperties(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  public boolean isLearningMode() {
    return dynamicConfigService.getConfig(Boolean.class, "locking.learningMode", learningMode);
  }

  public void setLearningMode(boolean learningMode) {
    this.learningMode = learningMode;
  }

  public boolean isEnabled() {
    return dynamicConfigService.isEnabled("locking", enabled);
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getTtlSeconds() {
    return dynamicConfigService.getConfig(Integer.class, "locking.ttlSeconds", ttlSeconds);
  }

  public void setTtlSeconds(int ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  public int getBackoffBufferSeconds() {
    return dynamicConfigService.getConfig(
        Integer.class, "locking.backoffBufferSeconds", backoffBufferSeconds);
  }

  public void setBackoffBufferSeconds(int backoffBufferSeconds) {
    this.backoffBufferSeconds = backoffBufferSeconds;
  }
}
