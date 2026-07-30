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

package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;

public interface DriftMetric {
  MeterRegistry getRegistry();

  NetflixAmazonCredentials getAccount();

  String getRegion();

  Logger getLog();

  String getAgentType();

  default AtomicReference<Double> getDriftMetricGauge() {
    return getRegistry()
        .gauge(
            "cache.drift",
            Tags.of(
                "agent",
                getClass().getSimpleName(),
                "account",
                getAccount().getName(),
                "region",
                getRegion()),
            new AtomicReference<>(0.0),
            AtomicReference::get);
  }

  default void recordDrift(Long start) {
    if (start != null && start != 0L) {
      Long drift = getRegistry().config().clock().wallTime() - start;
      getLog().info("{}/drift - {} milliseconds", getAgentType(), drift);
      getDriftMetricGauge().set(drift.doubleValue());
    }
  }
}
