/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.igor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("locking")
public class LockManagerConfigProperties {
  private Long maximumLockDurationMillis;
  private Long leaseDurationMillis = 30000L;
  private Long heartbeatRateMillis = 5000L;
  private boolean enabled;

  public Long getMaximumLockDurationMillis() {
    return maximumLockDurationMillis;
  }

  public void setMaximumLockDurationMillis(Long maximumLockDurationMillis) {
    this.maximumLockDurationMillis = maximumLockDurationMillis;
  }

  public Long getHeartbeatRateMillis() {
    return heartbeatRateMillis;
  }

  public void setHeartbeatRateMillis(Long heartbeatRateMillis) {
    this.heartbeatRateMillis = heartbeatRateMillis;
  }

  public Long getLeaseDurationMillis() {
    return leaseDurationMillis;
  }

  public void setLeaseDurationMillis(Long leaseDurationMillis) {
    this.leaseDurationMillis = leaseDurationMillis;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
