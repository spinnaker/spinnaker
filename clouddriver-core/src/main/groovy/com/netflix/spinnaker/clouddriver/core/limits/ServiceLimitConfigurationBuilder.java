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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Mutable structure for construction of ServiceLimitConfiguration. */
public class ServiceLimitConfigurationBuilder {

  private MutableLimits defaults = new MutableLimits();
  private Map<String, MutableLimits> cloudProviderOverrides = new HashMap<>();
  private Map<String, MutableLimits> accountOverrides = new HashMap<>();
  private Map<String, MutableImplementationLimits> implementationLimits = new HashMap<>();

  public MutableLimits getDefaults() {
    return defaults;
  }

  public void setDefaults(MutableLimits defaults) {
    this.defaults = defaults;
  }

  public ServiceLimitConfigurationBuilder withDefault(String limit, Double value) {
    if (defaults == null) {
      defaults = new MutableLimits();
    }
    defaults.setLimit(limit, value);
    return this;
  }

  public Map<String, MutableLimits> getCloudProviderOverrides() {
    return cloudProviderOverrides;
  }

  public void setCloudProviderOverrides(Map<String, MutableLimits> cloudProviderOverrides) {
    this.cloudProviderOverrides = cloudProviderOverrides;
  }

  public ServiceLimitConfigurationBuilder withCloudProviderOverride(
      String cloudProvider, String limit, Double value) {
    if (cloudProviderOverrides == null) {
      cloudProviderOverrides = new HashMap<>();
    }
    cloudProviderOverrides
        .computeIfAbsent(cloudProvider, k -> new MutableLimits())
        .setLimit(limit, value);
    return this;
  }

  public Map<String, MutableLimits> getAccountOverrides() {
    return accountOverrides;
  }

  public void setAccountOverrides(Map<String, MutableLimits> accountOverrides) {
    this.accountOverrides = accountOverrides;
  }

  public ServiceLimitConfigurationBuilder withAccountOverride(
      String account, String limit, Double value) {
    if (accountOverrides == null) {
      accountOverrides = new HashMap<>();
    }

    accountOverrides.computeIfAbsent(account, k -> new MutableLimits()).setLimit(limit, value);
    return this;
  }

  public Map<String, MutableImplementationLimits> getImplementationLimits() {
    return implementationLimits;
  }

  public void setImplementationLimits(
      Map<String, MutableImplementationLimits> implementationLimits) {
    this.implementationLimits = implementationLimits;
  }

  public ServiceLimitConfigurationBuilder withImplementationDefault(
      String implementation, String limit, Double value) {
    if (implementationLimits == null) {
      implementationLimits = new HashMap<>();
    }
    implementationLimits
        .computeIfAbsent(implementation, k -> new MutableImplementationLimits())
        .defaults
        .setLimit(limit, value);
    return this;
  }

  public ServiceLimitConfigurationBuilder withImplementationAccountOverride(
      String implementation, String account, String limit, Double value) {
    if (implementationLimits == null) {
      implementationLimits = new HashMap<>();
    }

    implementationLimits
        .computeIfAbsent(implementation, k -> new MutableImplementationLimits())
        .accountOverrides
        .computeIfAbsent(account, k -> new MutableLimits())
        .setLimit(limit, value);

    return this;
  }

  public ServiceLimitConfiguration build() {
    return new ServiceLimitConfiguration(
        new ServiceLimits(defaults),
        toServiceLimits(cloudProviderOverrides),
        toServiceLimits(accountOverrides),
        toImplementationLimits(implementationLimits));
  }

  public static class MutableLimits extends HashMap<String, Double> {
    public void setLimit(String limit, Double value) {
      put(limit, value);
    }

    public Double getLimit(String limit) {
      return get(limit);
    }
  }

  public static class MutableImplementationLimits {
    MutableLimits defaults = new MutableLimits();
    Map<String, MutableLimits> accountOverrides = new HashMap<>();

    public ImplementationLimits toImplementationLimits() {
      return new ImplementationLimits(
          new ServiceLimits(defaults), toServiceLimits(accountOverrides));
    }

    public MutableLimits getDefaults() {
      return defaults;
    }

    public void setDefaults(MutableLimits defaults) {
      this.defaults = defaults;
    }

    public Map<String, MutableLimits> getAccountOverrides() {
      return accountOverrides;
    }

    public void setAccountOverrides(Map<String, MutableLimits> accountOverrides) {
      this.accountOverrides = accountOverrides;
    }
  }

  private static <S, D> Map<String, D> toImmutable(
      Map<String, S> src, Function<Map.Entry<String, S>, D> converter) {
    return java.util.Optional.ofNullable(src)
        .map(Map::entrySet)
        .map(Set::stream)
        .map(s -> s.collect(Collectors.toMap(Map.Entry::getKey, converter)))
        .orElse(Collections.emptyMap());
  }

  private static Map<String, ServiceLimits> toServiceLimits(Map<String, MutableLimits> limits) {
    return toImmutable(limits, mapEntry -> new ServiceLimits(mapEntry.getValue()));
  }

  private static Map<String, ImplementationLimits> toImplementationLimits(
      Map<String, MutableImplementationLimits> implementationLimits) {
    return toImmutable(
        implementationLimits, mapEntry -> mapEntry.getValue().toImplementationLimits());
  }
}
