/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.health;

import com.netflix.spinnaker.echo.pipelinetriggers.MonitoredPoller;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static java.time.Instant.now;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDurationWords;

/**
 * A {@link HealthIndicator} implementation that monitors an instance of {@link MonitoredPoller}.
 * <p>
 * The health status is based on whether the poller is running and how long ago it last polled. If twice the polling
 * interval has passed since the last poll the poller is considered _down_.
 */
@Component
@Slf4j
public class MonitoredPollerHealth extends AbstractHealthIndicator {
  private final MonitoredPoller poller;
  private Status status;

  @Autowired
  public MonitoredPollerHealth(MonitoredPoller poller) {
    this.poller = poller;
    this.status = Status.DOWN;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    if (!poller.isRunning()) {
      log.warn("Poller {} is not currently running", poller);
    }

    // status is initially DOWN and can only flicked to UP (when the poller
    // has completed one cycle successfully), never back to DOWN
    // if poller staleness is a concern (i.e. poller.getLastPollTimestamp() is
    // significantly old), rely on monitoring and alerting instead
    if (status != Status.UP && poller.isInitialized()) {
      status = Status.UP;
    }

    Instant lastPollTimestamp = poller.getLastPollTimestamp();
    if (lastPollTimestamp != null) {
      val timeSinceLastPoll = Duration.between(lastPollTimestamp, now());
      builder.withDetail("last.polled", formatDurationWords(timeSinceLastPoll.toMillis(), true, true) + " ago")
        .withDetail("last.polled.at", ISO_LOCAL_DATE_TIME.format(lastPollTimestamp.atZone(ZoneId.systemDefault())));
    }

    builder.status(status);
  }
}
