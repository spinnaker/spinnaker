/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.limits;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class ServiceLimitConfiguration {
  public static final String POLL_INTERVAL_MILLIS = "agentPollIntervalMs";
  public static final String POLL_TIMEOUT_MILLIS = "agentPollTimeoutMs";
  public static final String API_RATE_LIMIT = "rateLimit";

  private final ServiceLimits defaults;
  private final Map<String, ServiceLimits> cloudProviderOverrides;
  private final Map<String, ServiceLimits> accountOverrides;
  private final Map<String, ImplementationLimits> implementationLimits;

  public ServiceLimitConfiguration(
      ServiceLimits defaults,
      Map<String, ServiceLimits> cloudProviderOverrides,
      Map<String, ServiceLimits> accountOverrides,
      Map<String, ImplementationLimits> implementationLimits) {
    this.defaults = defaults == null ? new ServiceLimits(null) : defaults;
    this.cloudProviderOverrides =
        cloudProviderOverrides == null
            ? Collections.emptyMap()
            : ImmutableMap.copyOf(cloudProviderOverrides);
    this.accountOverrides =
        accountOverrides == null ? Collections.emptyMap() : ImmutableMap.copyOf(accountOverrides);
    this.implementationLimits =
        implementationLimits == null
            ? Collections.emptyMap()
            : ImmutableMap.copyOf(implementationLimits);
  }

  public Double getLimit(
      String limit,
      String implementation,
      String account,
      String cloudProvider,
      Double defaultValue) {
    return Optional.ofNullable(getImplementationLimit(limit, implementation, account))
        .orElse(
            Optional.ofNullable(getAccountLimit(limit, account))
                .orElse(
                    Optional.ofNullable(getCloudProviderLimit(limit, cloudProvider))
                        .orElse(
                            Optional.ofNullable(defaults.getLimit(limit)).orElse(defaultValue))));
  }

  private Double getAccountLimit(String limit, String account) {
    return Optional.ofNullable(account)
        .map(accountOverrides::get)
        .map(sl -> sl.getLimit(limit))
        .orElse(null);
  }

  private Double getCloudProviderLimit(String limit, String cloudProvider) {
    return Optional.ofNullable(cloudProvider)
        .map(cloudProviderOverrides::get)
        .map(sl -> sl.getLimit(limit))
        .orElse(null);
  }

  private Double getImplementationLimit(String limit, String implementation, String account) {
    return Optional.ofNullable(implementation)
        .map(implementationLimits::get)
        .map(il -> il.getLimit(limit, account))
        .orElse(null);
  }
}
