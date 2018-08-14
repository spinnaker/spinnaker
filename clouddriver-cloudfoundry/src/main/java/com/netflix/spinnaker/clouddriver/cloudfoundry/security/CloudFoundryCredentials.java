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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class CloudFoundryCredentials implements AccountCredentials<CloudFoundryClient> {
  private final String name;
  private final String environment;
  private final String accountType = "cloudfoundry";
  private final String cloudProvider = "cloudfoundry";

  @Deprecated
  private final List<String> requiredGroupMembership = Collections.emptyList();

  @JsonIgnore
  private final CloudFoundryClient credentials;

  public CloudFoundryCredentials(String name, String apiHost, String userName, String password, String environment) {
    this.name = name;
    this.environment = environment;
    this.credentials = new CloudFoundryClient(name, apiHost, userName, password);
  }
}
