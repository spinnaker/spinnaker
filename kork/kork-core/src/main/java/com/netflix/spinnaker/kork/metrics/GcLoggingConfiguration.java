/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.kork.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Preserves the verbose GC-pause console logging previously provided by Spectator's GcLogger.
 * GC/memory/thread/classloader *metrics* need no configuration here; they are auto-registered by
 * Spring Boot Actuator's JvmMetricsAutoConfiguration onto the Micrometer MeterRegistry bean.
 */
@Configuration
@ConfigurationProperties("spectator.gc")
public class GcLoggingConfiguration {
  private boolean loggingEnabled = true;

  public boolean isLoggingEnabled() {
    return loggingEnabled;
  }

  public void setLoggingEnabled(boolean loggingEnabled) {
    this.loggingEnabled = loggingEnabled;
  }

  @Bean(destroyMethod = "stop")
  @ConditionalOnProperty(value = "spectator.gc.logging-enabled", matchIfMissing = true)
  GcPauseLogger gcPauseLogger() {
    GcPauseLogger gcPauseLogger = new GcPauseLogger();
    gcPauseLogger.start();
    return gcPauseLogger;
  }
}
