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
package com.netflix.spinnaker.clouddriver.core;

import com.netflix.spectator.api.Registry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public abstract class AlwaysUpHealthIndicator implements HealthIndicator {

  private static final Logger log = LoggerFactory.getLogger(AlwaysUpHealthIndicator.class);

  protected final AtomicReference<Exception> lastException = new AtomicReference<>(null);
  protected final AtomicReference<Boolean> hasInitialized = new AtomicReference<>(null);

  private final AtomicLong errors;

  public AlwaysUpHealthIndicator(Registry registry, String name) {
    this.errors = registry.gauge("health." + name + ".errors", new AtomicLong(0));
  }

  @Override
  public Health health() {
    if (hasInitialized.get() == Boolean.TRUE) {
      // avoid being marked unhealthy once connectivity to all accounts has been verified at least
      // once
      return new Health.Builder().up().build();
    }

    Exception ex = lastException.get();
    if (ex != null) {
      throw new HealthIndicatorWrappedException(ex);
    }

    return new Health.Builder().unknown().build();
  }

  protected void updateHealth(Runnable healthCheck) {
    try {
      healthCheck.run();
      hasInitialized.set(Boolean.TRUE);
      lastException.set(null);
      errors.set(0);
    } catch (Exception ex) {
      log.error("Unhealthy", ex);
      lastException.set(ex);
      errors.set(1);
    }
  }

  private static class HealthIndicatorWrappedException extends RuntimeException {
    public HealthIndicatorWrappedException(Throwable cause) {
      super(cause);
    }
  }
}
