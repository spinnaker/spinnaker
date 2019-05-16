/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.security;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.HttpCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@JsonIgnoreProperties({"credentials", "client"})
public class CloudFoundryCredentials implements AccountCredentials<CloudFoundryClient> {

  private final String name;
  private final String appsManagerUri;
  private final String metricsUri;
  private final String apiHost;
  private final String userName;
  private final String password;

  @Nullable private final String environment;

  private final String accountType = "cloudfoundry";

  private final String cloudProvider = "cloudfoundry";

  @Deprecated private final List<String> requiredGroupMembership = Collections.emptyList();

  private CloudFoundryClient credentials;

  public CloudFoundryCredentials(
      String name,
      String appsManagerUri,
      String metricsUri,
      String apiHost,
      String userName,
      String password,
      String environment) {
    this.name = name;
    this.appsManagerUri = appsManagerUri;
    this.metricsUri = metricsUri;
    this.apiHost = apiHost;
    this.userName = userName;
    this.password = password;
    this.environment = Optional.ofNullable(environment).orElse("dev");
  }

  public CloudFoundryClient getCredentials() {
    if (this.credentials == null) {
      this.credentials =
          new HttpCloudFoundryClient(name, appsManagerUri, metricsUri, apiHost, userName, password);
    }
    return credentials;
  }

  public CloudFoundryClient getClient() {
    return getCredentials();
  }

  public Collection<Map<String, String>> getRegions() {
    try {
      return credentials.getSpaces().all().stream()
          .map(space -> singletonMap("name", space.getRegion()))
          .collect(toList());
    } catch (CloudFoundryApiException e) {
      log.warn("Unable to determine regions for Cloud Foundry account " + name, e);
      return emptyList();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CloudFoundryCredentials)) {
      return false;
    }
    CloudFoundryCredentials that = (CloudFoundryCredentials) o;
    return name.equals(that.name)
        && Objects.equals(appsManagerUri, that.appsManagerUri)
        && Objects.equals(metricsUri, that.metricsUri)
        && Objects.equals(userName, that.userName)
        && Objects.equals(password, that.password)
        && Objects.equals(environment, that.environment);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, appsManagerUri, metricsUri, userName, password, environment);
  }
}
