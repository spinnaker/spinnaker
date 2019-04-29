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

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.*;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
@ConditionalOnBean(EchoService.class)
public class EchoServiceHealthIndicator implements HealthIndicator {
  private Logger log = LoggerFactory.getLogger(EchoServiceHealthIndicator.class);
  private final AtomicReference<Exception> lastException = new AtomicReference<>(null);
  private final AtomicBoolean upOnce;
  private final Optional<EchoService> echoService;
  private final AtomicLong errors;

  static final GenericBuildEvent event = buildGenericEvent();

  @Autowired
  EchoServiceHealthIndicator(Registry registry, Optional<EchoService> echoService) {
    this.echoService = echoService;
    this.upOnce = !echoService.isPresent() ? new AtomicBoolean(true) : new AtomicBoolean(false);
    this.errors =
        PolledMeter.using(registry).withName("health.echo.errors").monitorValue(new AtomicLong(0));
  }

  @Override
  public Health health() {
    if (upOnce.get() == Boolean.TRUE) {
      return new Health.Builder().up().build();
    }

    Optional.ofNullable(lastException.get())
        .ifPresent(
            le -> {
              throw new EchoUnreachableException(le);
            });

    return new Health.Builder().unknown().build();
  }

  @Scheduled(fixedDelay = 120000L)
  void checkHealth() {
    echoService.ifPresent(
        s -> {
          try {
            s.postEvent(event);
            upOnce.set(true);
            errors.set(0);
            lastException.set(null);
          } catch (Exception e) {
            errors.set(1);
            lastException.set(e);
            log.error("Unable to connect to Echo", e);
          }
        });
  }

  private static GenericBuildEvent buildGenericEvent() {
    final GenericBuildEvent event = new GenericBuildEvent();
    final GenericBuildContent buildContent = new GenericBuildContent();
    final GenericProject project = new GenericProject("spinnaker", new GenericBuild());
    buildContent.setMaster("IgorHealthCheck");
    buildContent.setProject(project);
    event.setContent(buildContent);
    return event;
  }

  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Could not reach Echo.")
  static class EchoUnreachableException extends RuntimeException {
    public EchoUnreachableException(Throwable cause) {
      super(cause);
    }
  }
}
