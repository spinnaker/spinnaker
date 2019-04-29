/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.health;

import static java.util.stream.Collectors.toList;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.igor.polling.PollingMonitor;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.joda.time.DateTimeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The health status is based on whether the poller is running and how long ago it last polled. If 5
 * times the polling interval has passed since the last poll the poller is considered _down_.
 */
@Component
public class PollingMonitorHealth implements HealthIndicator {
  private static Logger log = LoggerFactory.getLogger(PollingMonitorHealth.class);
  private final ApplicationContext applicationContext;
  private AtomicBoolean upOnce;
  private final AtomicLong downPollers;

  @Autowired
  public PollingMonitorHealth(Registry registry, ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
    this.upOnce = new AtomicBoolean(false);
    this.downPollers =
        PolledMeter.using(registry).withName("health.pollers.down").monitorValue(new AtomicLong(0));
  }

  @Override
  public Health health() {
    final Collection<PollingMonitor> pollers = getPollingMonitors();
    List<PollingMonitor> downPollingMonitors =
        pollers.stream()
            .filter(hasBeenFiveTimesPollIntervalSinceLastPollPredicate())
            .collect(toList());

    // There is a circular dependency between discovery status and poller's health
    // This makes sure we have been UP at least once before checking when the poller was last run
    if (upOnce.get() && !downPollingMonitors.isEmpty()) {
      final List<Health> healths =
          pollers.stream().map(this::getPollingMonitorHealth).collect(toList());

      Health.Builder healthBuilder = Health.up();
      if (healths.isEmpty()) {
        healthBuilder = Health.down().withDetail("status", "No polling agents running");
      } else if (healths.stream().anyMatch(i -> i.getStatus() == Status.DOWN)) {
        healthBuilder = Health.down();
      } else if (healths.stream().anyMatch(i -> i.getStatus() == Status.UNKNOWN)) {
        healthBuilder = Health.unknown();
      }

      // update health details
      for (Health h : healths) {
        for (Map.Entry<String, Object> details : h.getDetails().entrySet()) {
          healthBuilder.withDetail(details.getKey(), details.getValue());
        }
      }

      Health health = healthBuilder.build();
      upOnce.set(health.getStatus() == Status.UP);
      downPollers.set(health.getStatus() != Status.UP ? 1 : 0);
      log.info("PollingMonitor Health {}, details: {}", health.getStatus(), health.getDetails());
      return health;
    }

    return new Health.Builder().up().build();
  }

  private Predicate<PollingMonitor> hasBeenFiveTimesPollIntervalSinceLastPollPredicate() {
    return i ->
        i.getLastPoll() != null
            && (System.currentTimeMillis() - i.getLastPoll())
                > 5 * i.getPollInterval() * DateTimeConstants.MILLIS_PER_SECOND;
  }

  private Health getPollingMonitorHealth(PollingMonitor pollingMonitor) {
    final long elapsed = System.currentTimeMillis() - pollingMonitor.getLastPoll();
    // has it been 5 x pollInterval since last poll?
    if (elapsed > 5 * pollingMonitor.getPollInterval() * DateTimeConstants.MILLIS_PER_SECOND) {
      log.warn("{} {}msec since last poll, this poller is DOWN", pollingMonitor.getName(), elapsed);
      return Health.down()
          .withDetail(String.format("%s.status", pollingMonitor.getName()), "stopped")
          .withDetail(
              String.format("%s.lastPoll", pollingMonitor.getName()), pollingMonitor.getLastPoll())
          .build();
    }

    return Health.up()
        .withDetail(String.format("%s.status", pollingMonitor.getName()), "running")
        .withDetail(
            String.format("%s.lastPoll", pollingMonitor.getName()), pollingMonitor.getLastPoll())
        .build();
  }

  private Collection<PollingMonitor> getPollingMonitors() {
    try {
      return applicationContext.getBeansOfType(PollingMonitor.class).values();
    } catch (BeansException e) {
      log.error("Could not get polling monitors", e);
      return Collections.emptyList();
    }
  }
}
