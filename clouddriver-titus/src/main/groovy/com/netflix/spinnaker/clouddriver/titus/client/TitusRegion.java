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

package com.netflix.spinnaker.clouddriver.titus.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TitusRegion {
  private final String name;
  private final String account;
  private final String endpoint;
  private final Boolean autoscalingEnabled;
  private final Boolean loadBalancingEnabled;
  private final List<TitusFaultDomain> faultDomains;
  private final String applicationName;
  private final String url;
  private final int port;
  private final List<String> featureFlags;

  private <T> T notNull(T val, String name) {
    if (val == null) {
      throw new NullPointerException(name);
    }
    return val;
  }

  public TitusRegion(
      String name,
      String account,
      String endpoint,
      Boolean autoscalingEnabled,
      Boolean loadBalancingEnabled,
      List<TitusFaultDomain> faultDomains,
      String applicationName,
      String url,
      Integer port,
      List<String> featureFlags) {
    this.name = notNull(name, "name");
    this.account = notNull(account, "account");
    this.endpoint = EndpointValidator.validateEndpoint(endpoint);
    this.autoscalingEnabled = autoscalingEnabled;
    this.loadBalancingEnabled = loadBalancingEnabled;
    this.faultDomains =
        faultDomains == null ? Collections.emptyList() : Collections.unmodifiableList(faultDomains);
    this.applicationName = applicationName;
    this.url = url;
    if (port != null) {
      this.port = port;
    } else {
      this.port = 7104;
    }
    if (featureFlags == null) {
      this.featureFlags = new ArrayList<>();
    } else {
      this.featureFlags = featureFlags;
    }
  }

  public TitusRegion(
      String name,
      String account,
      String endpoint,
      Boolean autoscalingEnabled,
      Boolean loadBalancingEnabled,
      String applicationName,
      String url,
      Integer port,
      List<String> featureFlags) {
    this(
        name,
        account,
        endpoint,
        autoscalingEnabled,
        loadBalancingEnabled,
        Collections.emptyList(),
        applicationName,
        url,
        port,
        featureFlags);
  }

  public String getAccount() {
    return account;
  }

  public String getName() {
    return name;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public Boolean isAutoscalingEnabled() {
    return autoscalingEnabled;
  }

  public Boolean isLoadBalancingEnabled() {
    return loadBalancingEnabled;
  }

  public List<TitusFaultDomain> getFaultDomains() {
    return faultDomains;
  }

  public String getApplicationName() {
    return applicationName;
  }

  public Integer getPort() {
    return port;
  }

  public String getUrl() {
    return url;
  }

  public List<String> getFeatureFlags() {
    return featureFlags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TitusRegion that = (TitusRegion) o;

    if (!name.equals(that.name)) return false;
    if (!account.equals(that.account)) return false;
    if (!endpoint.equals(that.endpoint)) return false;
    return faultDomains.equals(that.faultDomains);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + account.hashCode();
    result = 31 * result + endpoint.hashCode();
    result = 31 * result + faultDomains.hashCode();
    return result;
  }
}
