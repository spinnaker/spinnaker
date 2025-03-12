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

package com.netflix.spinnaker.clouddriver.titus.credentials

import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials

import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.security.TitusCredentials
import com.netflix.spinnaker.fiat.model.resources.Permissions

class NetflixTitusCredentials extends AbstractAccountCredentials<TitusCredentials> {
  private static final String CLOUD_PROVIDER = TitusCloudProvider.ID

  final String name
  final String environment
  final String accountType
  final List<String> requiredGroupMembership = Collections.emptyList()
  final Permissions permissions
  final String bastionHost
  final String registry
  final String discovery
  final String awsAccount
  final String awsVpc
  final boolean discoveryEnabled
  final String stack
  final String eurekaName

  private final List<TitusRegion> regions

  NetflixTitusCredentials() {}

  NetflixTitusCredentials(String name,
                          String environment,
                          String accountType,
                          List<TitusRegion> regions,
                          String bastionHost,
                          String registry,
                          String awsAccount,
                          String awsVpc,
                          boolean discoveryEnabled,
                          String discovery,
                          String stack,
                          List<String> requiredGroupMembership,
                          Permissions permissions,
                          String eurekaName
  ) {
    this.name = name
    this.environment = environment
    this.accountType = accountType
    this.regions = regions?.asImmutable() ?: Collections.emptyList()
    this.bastionHost = bastionHost
    this.registry = registry
    this.awsAccount = awsAccount
    this.awsVpc = awsVpc
    this.discoveryEnabled = discoveryEnabled
    this.discovery = discovery
    this.stack = stack
    this.requiredGroupMembership = requiredGroupMembership
    this.permissions = permissions
    this.eurekaName = eurekaName
  }

  @Override
  TitusCredentials getCredentials() {
    new TitusCredentials() {}
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }

  List<TitusRegion> getRegions() {
    regions
  }

  String getDiscovery() {
    return discovery
  }

  String getRegistry() {
    return registry
  }

  String getAwsAccount() {
    return awsAccount
  }

  String getAwsVpc() {
    return awsVpc
  }

  boolean getDiscoveryEnabled() {
    return discoveryEnabled
  }

  String getStack() {
    return stack
  }

  List<String> getRequiredGroupMembership() {
    return requiredGroupMembership
  }

  Permissions getPermissions() {
    return permissions;
  }

  String getEurekaName() {
    return eurekaName
  }
}
