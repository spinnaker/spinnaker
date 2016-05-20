/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

import java.util.List;

public class OpenstackNamedAccountCredentials implements AccountCredentials<OpenstackCredentials> {
  private static final String CLOUD_PROVIDER = "openstack";
  private final String accountName;
  private final String environment;
  private final String accountType;
  private final String master;
  private final String username;
  @JsonIgnore
  private final String password;
  private final String tenantId;
  private final String endpoint;
  private final List<String> requiredGroupMembership;
  private final OpenstackCredentials credentials;

  public OpenstackNamedAccountCredentials(String accountName,
                                          String environment,
                                          String accountType,
                                          String master,
                                          String username,
                                          String password,
                                          String tenantId,
                                          String endpoint) {
    this(accountName, environment, accountType, master, username, password, null, tenantId, endpoint);
  }

  public OpenstackNamedAccountCredentials(String accountName,
                                          String environment,
                                          String accountType,
                                          String master,
                                          String username,
                                          String password,
                                          List<String> requiredGroupMembership,
                                          String tenantId,
                                          String endpoint) {
    this.accountName = accountName;
    this.environment = environment;
    this.accountType = accountType;
    this.master = master;
    this.username = username;
    this.password = password;
    this.tenantId = tenantId;
    this.endpoint = endpoint;
    this.requiredGroupMembership = requiredGroupMembership;
    this.credentials = buildCredentials();
  }

  private OpenstackCredentials buildCredentials() {
    return new OpenstackCredentials(this.username, this.password, this.tenantId, this.endpoint);
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  @Override
  public String getName() {
    return accountName;
  }

  public String getEnvironment() {
    return environment;
  }

  public String getAccountType() {
    return accountType;
  }

  public String getMaster() {
    return master;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getEndpoint() {
    return endpoint;
  }

  @Override
  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  public OpenstackCredentials getCredentials() {
    return credentials;
  }
}
