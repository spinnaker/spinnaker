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

package com.netflix.spinnaker.kork.test.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAmount;

public class MutableClock extends Clock {

  private Instant instant;
  private final ZoneId zone;

  public MutableClock(Instant instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public MutableClock(Instant instant) {
    this(instant, ZoneId.systemDefault());
  }

  public MutableClock(ZoneId zone) {
    this(Instant.now(), zone);
  }

  public MutableClock() {
    this(Instant.now(), ZoneId.systemDefault());
  }

  @Override
  public MutableClock withZone(ZoneId zone) {
    return new MutableClock(instant, zone);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Instant instant() {
    return instant;
  }

  public void incrementBy(TemporalAmount amount) {
    instant = instant.plus(amount);
  }

  public void instant(Instant newInstant) {
    instant = newInstant;
  }
}
