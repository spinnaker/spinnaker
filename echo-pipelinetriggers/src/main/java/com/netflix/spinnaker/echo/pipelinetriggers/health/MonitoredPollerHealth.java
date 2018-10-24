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
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
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
public class MonitoredPollerHealth extends AbstractHealthIndicator {

  private final MonitoredPoller poller;

  @Autowired
  public MonitoredPollerHealth(MonitoredPoller poller) {
    this.poller = poller;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    if (!poller.isRunning()) {
      builder.outOfService();
    } else {
      polledRecently(builder);
    }
  }

  private void polledRecently(Health.Builder builder) {
    Instant lastPollTimestamp = poller.getLastPollTimestamp();
    if (lastPollTimestamp == null) {
      builder.unknown();
    } else {
      val timeSinceLastPoll = Duration.between(lastPollTimestamp, now());
      builder.withDetail("last.polled", formatDurationWords(timeSinceLastPoll.toMillis(), true, true) + " ago");
      builder.withDetail("last.polled.at", ISO_LOCAL_DATE_TIME.format(lastPollTimestamp.atZone(ZoneId.systemDefault())));
      if (timeSinceLastPoll.compareTo(Duration.of(poller.getPollingIntervalSeconds() * 2, SECONDS)) <= 0) {
        builder.up();
      } else {
        builder.down();
      }
    }
  }
}
