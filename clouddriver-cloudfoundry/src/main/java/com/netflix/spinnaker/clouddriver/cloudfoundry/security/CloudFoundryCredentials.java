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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.HttpCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

@Slf4j
@Getter
@JsonIgnoreProperties({"credentials", "client"})
public class CloudFoundryCredentials implements AccountCredentials<CloudFoundryClient> {

  private final String name;

  @Nullable
  private final String environment;

  private final String accountType = "cloudfoundry";

  private final String cloudProvider = "cloudfoundry";

  @Deprecated
  private final List<String> requiredGroupMembership = Collections.emptyList();

  private final CloudFoundryClient credentials;

  public CloudFoundryCredentials(String name, String appsManagerUri, String metricsUri, String apiHost, String userName, String password, String environment) {
    this.name = name;
    this.environment = Optional.ofNullable(environment).orElse("dev");
    this.credentials = new HttpCloudFoundryClient(name, appsManagerUri, metricsUri, apiHost, userName, password);
  }

  public CloudFoundryClient getClient() {
    return credentials;
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
}
