/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.front50.config;

public class S3EventingProperties {
  boolean enabled = false;

  String snsTopicName;

  long refreshIntervalMs = 120000;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getSnsTopicName() {
    return snsTopicName;
  }

  public void setSnsTopicName(String snsTopicName) {
    this.snsTopicName = snsTopicName;
  }

  public long getRefreshIntervalMs() {
    return refreshIntervalMs;
  }

  public void setRefreshIntervalMs(long refreshIntervalMs) {
    this.refreshIntervalMs = refreshIntervalMs;
  }
}
