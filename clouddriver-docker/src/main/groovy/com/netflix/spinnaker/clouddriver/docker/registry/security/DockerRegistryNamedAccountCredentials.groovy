/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.security

import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client.DockerRegistryClient;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;

import java.util.*;

public class DockerRegistryNamedAccountCredentials implements AccountCredentials<DockerRegistryCredentials> {
  public DockerRegistryNamedAccountCredentials(String accountName, String environment, String accountType,
                                               String address, String username, String password, String email,
                                               List<String> repositories) {
    this(accountName, environment, accountType, address, username, password, email, repositories, null);
  }

  public DockerRegistryNamedAccountCredentials(String accountName, String environment, String accountType,
                                               String address, String username, String password, String email,
                                               List<String> repositories, List<String> requiredGroupMembership) {
    this.accountName = accountName;
    this.environment = environment;
    this.accountType = accountType;
    this.address = address;
    this.username = username;
    this.password = password;
    this.email = email;
    this.repositories = (repositories == null) ? [] : repositories
    this.requiredGroupMembership = requiredGroupMembership == null ? Collections.emptyList() : Collections.unmodifiableList(requiredGroupMembership);
    this.credentials = buildCredentials();
  }

  @Override
  public String getName() {
    return accountName;
  }

  @Override
  public String getEnvironment() {
    return environment;
  }

  @Override
  public String getAccountType() {
    return accountType;
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  public DockerRegistryCredentials getCredentials() {
    return credentials;
  }

  private DockerRegistryCredentials buildCredentials() {
    DockerRegistryClient client = new DockerRegistryClient(this.address, this.email, this.username, this.password);
    return new DockerRegistryCredentials(client, this.repositories);
  }

  private static String getLocalName(String fullUrl) {
    return fullUrl.substring(fullUrl.lastIndexOf('/') + 1);
  }

  @Override
  public String getProvider() {
    return getCloudProvider();
  }

  public String getAccountName() {
    return accountName;
  }

  public List<String> getRequiredGroupMembership() {
    return requiredGroupMembership;
  }

  private static final String CLOUD_PROVIDER = "dockerRegistry";
  private final String accountName;
  private final String environment;
  private final String accountType;
  private final String address;
  private final String username;
  private final String password;
  private final String email;
  private final List<String> repositories;
  private final DockerRegistryCredentials credentials;
  private final List<String> requiredGroupMembership;
}
