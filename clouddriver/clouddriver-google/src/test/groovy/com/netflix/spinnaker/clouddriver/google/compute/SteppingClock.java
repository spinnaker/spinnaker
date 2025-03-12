/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.compute;

import com.netflix.spectator.api.Clock;
import java.time.Duration;

class SteppingClock implements Clock {

  private long currentTimeMs = 0;
  private final int msAdjustmentBetweenCalls;

  public SteppingClock(int msAdjustmentBetweenCalls) {
    this.msAdjustmentBetweenCalls = msAdjustmentBetweenCalls;
  }

  @Override
  public long wallTime() {
    currentTimeMs += msAdjustmentBetweenCalls;
    return currentTimeMs;
  }

  @Override
  public long monotonicTime() {
    currentTimeMs += msAdjustmentBetweenCalls;
    return Duration.ofMillis(currentTimeMs).toNanos();
  }
}
