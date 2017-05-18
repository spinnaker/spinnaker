/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.gate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties("rateLimit")
public class RateLimiterConfiguration {

  /**
   * When learning mode is enabled, principals that go over their
   * allocated rate limit will not receive a 429, but gate will still
   * log that they would have been rate limited.
   */
  boolean learning = true;

  /**
   * The number of requests allowed in the given rateSeconds window.
   */
  int capacity = 60;

  /**
   * The number of seconds for each rate limit bucket. Capacity will be
   * filled at the beginning of each rate interval.
   */
  int rateSeconds = 10;

  /**
   * A principal-specific capacity override map. This can be defined if
   * you want to give a specific principal more or less capacity per
   * rateSeconds than the default.
   */
  List<PrincipalOverride> capacityByPrincipal = new ArrayList<>();

  /**
   * A principal-specific rate override map.
   */
  List<PrincipalOverride> rateSecondsByPrincipal = new ArrayList<>();

  /**
   * A list of principals whose capacities are being enforced. This
   * setting will only be used when learning mode is ENABLED, allowing
   * operators to incrementally enable rate limiting on a per-principal
   * basis.
   */
  List<String> enforcing = new ArrayList<>();

  /**
   * A list of principals whose capacities are not being enforced. This
   * setting will only be used when learning mode is DISABLED, allowing
   * operators to enable learning-mode on a per-principal basis.
   */
  List<String> ignoring = new ArrayList<>();

  public int getCapacity() {
    return capacity;
  }

  public int getRateSeconds() {
    return rateSeconds;
  }

  public List<PrincipalOverride> getCapacityByPrincipal() {
    return capacityByPrincipal;
  }

  public List<PrincipalOverride> getRateSecondsByPrincipal() {
    return rateSecondsByPrincipal;
  }

  public boolean isLearning() {
    return learning;
  }

  public List<String> getEnforcing() {
    return enforcing;
  }

  public List<String> getIgnoring() {
    return ignoring;
  }

  // Spring doesn't enjoy principals that have dots in their name, so it can't be a map.
  public static class PrincipalOverride {
    String principal;
    Integer override;

    public String getPrincipal() {
      return principal;
    }

    public Integer getOverride() {
      return override;
    }
  }
}
