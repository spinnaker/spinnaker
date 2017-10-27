/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.index.config;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

public class IndexConfigurationProperties {

  @Getter
  @Setter
  private long heartbeatIntervalMS = Duration.ofSeconds(5).toMillis();

  @Getter
  @Setter
  // The initial delay is to give an instance some time to first record its heartbeat.
  private long indexingInitialDelayMS = Duration.ofSeconds(5).toMillis();

  @Getter
  @Setter
  private long indexingIntervalMS = Duration.ofMinutes(15).toMillis();

  @Getter
  @Setter
  private int indexingLockTTLSec = (int)Duration.ofMinutes(15).getSeconds();

  @Getter
  @Setter
  private long pendingUpdateStaleEntryThresholdMS = Duration.ofHours(1).toMillis();
}
