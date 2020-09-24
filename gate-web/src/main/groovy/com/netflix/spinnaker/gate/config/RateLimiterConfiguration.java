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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("rate-limit")
public class RateLimiterConfiguration {

  /**
   * When learning mode is enabled, principals that go over their allocated rate limit will not
   * receive a 429, but gate will still log that they would have been rate limited.
   */
  private boolean learning = true;

  /** The number of requests allowed in the given rateSeconds window. */
  private int capacity = 60;

  /**
   * The number of seconds for each rate limit bucket. Capacity will be filled at the beginning of
   * each rate interval.
   */
  private int rateSeconds = 10;

  /**
   * The registration order for {@link com.netflix.spinnaker.gate.ratelimit.RateLimitingFilter}.
   *
   * <p>100 - Run after the spring security filter chain.
   *
   * <p>-100 - Run prior to the spring security filter chain.
   *
   * <p>When the filter is run prior to the spring security filter chain, it will only rate limit
   * x509 certificate-based requests.
   */
  private int filterOrder = 100;

  /**
   * A principal-specific capacity override map. This can be defined if you want to give a specific
   * principal more or less capacity per rateSeconds than the default.
   */
  private List<PrincipalOverride> capacityByPrincipal = new ArrayList<>();

  /**
   * A source app-specific capacity override map. This can be defined if you want to give a specific
   * source app more or less capacity per rateSeconds than the default.
   */
  private List<SourceAppOverride> capacityBySourceApp = new ArrayList<>();

  /** A principal-specific rate override map. */
  private List<PrincipalOverride> rateSecondsByPrincipal = new ArrayList<>();

  /**
   * A list of principals whose capacities are being enforced. This setting will only be used when
   * learning mode is ENABLED, allowing operators to incrementally enable rate limiting on a
   * per-principal basis.
   */
  private List<String> enforcing = new ArrayList<>();

  /**
   * A list of principals whose capacities are not being enforced. This setting will only be used
   * when learning mode is DISABLED, allowing operators to enable learning-mode on a per-principal
   * basis.
   */
  private List<String> ignoring = new ArrayList<>();

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

  public List<SourceAppOverride> getCapacityBySourceApp() {
    return capacityBySourceApp;
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

  public void setLearning(boolean learning) {
    this.learning = learning;
  }

  public void setCapacity(int capacity) {
    this.capacity = capacity;
  }

  public void setRateSeconds(int rateSeconds) {
    this.rateSeconds = rateSeconds;
  }

  public void setCapacityByPrincipal(List<PrincipalOverride> capacityByPrincipal) {
    this.capacityByPrincipal = capacityByPrincipal;
  }

  public void setCapacityBySourceApp(List<SourceAppOverride> capacityBySourceApp) {
    this.capacityBySourceApp = capacityBySourceApp;
  }

  public void setRateSecondsByPrincipal(List<PrincipalOverride> rateSecondsByPrincipal) {
    this.rateSecondsByPrincipal = rateSecondsByPrincipal;
  }

  public void setEnforcing(List<String> enforcing) {
    this.enforcing = enforcing;
  }

  public void setIgnoring(List<String> ignoring) {
    this.ignoring = ignoring;
  }

  public int getFilterOrder() {
    return filterOrder;
  }

  public void setFilterOrder(int filterOrder) {
    this.filterOrder = filterOrder;
  }

  // Spring doesn't enjoy principals that have dots in their name, so it can't be a map.
  public static class PrincipalOverride {
    private String principal;
    private Integer override;

    public String getPrincipal() {
      return principal;
    }

    public Integer getOverride() {
      return override;
    }

    public void setPrincipal(String principal) {
      this.principal = principal;
    }

    public void setOverride(Integer override) {
      this.override = override;
    }
  }

  public static class SourceAppOverride {
    private String sourceApp;
    private Integer override;

    public String getSourceApp() {
      return sourceApp;
    }

    public Integer getOverride() {
      return override;
    }

    public void setSourceApp(String sourceApp) {
      this.sourceApp = sourceApp;
    }

    public void setOverride(Integer override) {
      this.override = override;
    }
  }
}
