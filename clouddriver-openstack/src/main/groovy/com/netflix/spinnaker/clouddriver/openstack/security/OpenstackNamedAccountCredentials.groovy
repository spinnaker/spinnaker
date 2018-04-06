/*
 * Copyright 2016 Veritas Technologies LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.openstack.security

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.StackConfig
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.ToString

@ToString(includeNames = true, excludes = "password")
class OpenstackNamedAccountCredentials implements AccountCredentials<OpenstackCredentials> {
  static final String CLOUD_PROVIDER = "openstack"
  final String name
  final String environment
  final String accountType
  final String username
  @JsonIgnore
  final String password
  final String projectName
  final String domainName
  final String authUrl
  final List<String> requiredGroupMembership
  final OpenstackCredentials credentials
  List<String> regions
  final Boolean insecure
  final String heatTemplateLocation
  final LbaasConfig lbaasConfig
  final StackConfig stackConfig
  final ConsulConfig consulConfig
  final String userDataFile
  Map<String, List<String>> regionToZones



  OpenstackNamedAccountCredentials(String accountName,
                                   String environment,
                                   String accountType,
                                   String username,
                                   String password,
                                   String projectName,
                                   String domainName,
                                   String authUrl,
                                   List<String> regions,
                                   Boolean insecure,
                                   String heatTemplateLocation,
                                   LbaasConfig lbaasConfig,
                                   StackConfig stackConfig,
                                   ConsulConfig consulConfig,
                                   String userDataFile) {
    this(accountName, environment, accountType, username, password, null, projectName, domainName, authUrl, regions, insecure, heatTemplateLocation, lbaasConfig, stackConfig, consulConfig, userDataFile)
  }

  // Explicit getter so that we can mock
  LbaasConfig getLbaasConfig() {
    return lbaasConfig
  }

  // Explicit getter so that we can mock
  StackConfig getStackConfig() {
    return stackConfig
  }

  OpenstackNamedAccountCredentials(String accountName,
                                   String environment,
                                   String accountType,
                                   String username,
                                   String password,
                                   List<String> requiredGroupMembership,
                                   String projectName,
                                   String domainName,
                                   String authUrl,
                                   List<String> regions,
                                   Boolean insecure,
                                   String heatTemplateLocation,
                                   LbaasConfig lbaasConfig,
                                   StackConfig stackConfig,
                                   ConsulConfig consulConfig,
                                   String userDataFile) {
    this.name = accountName
    this.environment = environment
    this.accountType = accountType
    this.username = username
    this.password = password
    this.projectName = projectName
    this.domainName = domainName
    this.authUrl = authUrl
    this.requiredGroupMembership = requiredGroupMembership
    this.regions = regions
    this.insecure = insecure
    this.heatTemplateLocation = heatTemplateLocation
    this.lbaasConfig = lbaasConfig
    this.stackConfig = stackConfig
    this.consulConfig = consulConfig
    this.userDataFile = userDataFile
    if (this.consulConfig?.enabled) {
      this.consulConfig.applyDefaults()
    }
    this.credentials = buildCredentials()
  }

  private OpenstackCredentials buildCredentials() {
    new OpenstackCredentials(this)
  }

  static class Builder {
    String name
    String environment
    String accountType
    String username
    String password
    String projectName
    String domainName
    String authUrl
    List<String> requiredGroupMembership
    OpenstackCredentials credentials
    List<String> regions
    Boolean insecure
    String heatTemplateLocation
    LbaasConfig lbaasConfig
    StackConfig stackConfig
    ConsulConfig consulConfig
    String userDataFile

    Builder() {}

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder username(String username) {
      this.username = username
      return this
    }

    Builder password(String password) {
      this.password = password
      return this
    }

    Builder projectName(String projectName) {
      this.projectName = projectName
      return this
    }

    Builder domainName(String domainName) {
      this.domainName = domainName
      return this
    }

    Builder authUrl(String authUrl) {
      this.authUrl = authUrl
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder credentials(OpenstackCredentials credentials) {
      this.credentials = credentials
      return this
    }

    Builder regions(List<String> regions) {
      this.regions = regions
      return this
    }

    Builder insecure(Boolean insecure) {
      this.insecure = insecure
      return this
    }

    Builder heatTemplateLocation(String heatTemplateLocation) {
      this.heatTemplateLocation = heatTemplateLocation
      return this
    }

    Builder lbaasConfig(LbaasConfig lbaasConfig) {
      this.lbaasConfig = lbaasConfig
      return this
    }

    Builder stackConfig(StackConfig stackConfig) {
      this.stackConfig = stackConfig
      return this
    }

    Builder consulConfig(ConsulConfig consulConfig) {
      this.consulConfig = consulConfig
      return this
    }

    Builder userDataFile(String userDataFile) {
      this.userDataFile = userDataFile
      return this
    }

    public OpenstackNamedAccountCredentials build() {
      def account = new OpenstackNamedAccountCredentials(name,
        environment,
        accountType,
        username,
        password,
        projectName,
        domainName,
        authUrl,
        regions,
        insecure,
        heatTemplateLocation,
        lbaasConfig,
        stackConfig,
        consulConfig,
        userDataFile)
      def provider = account.credentials.provider
      def regionToZoneMap = regions.collectEntries { region ->
        [(region): provider.getZones(region).findAll { zone -> zone.zoneState.available }.collect { zone -> zone.zoneName}]
      }
      account.regionToZones = regionToZoneMap
      return account
    }
  }

  @Override
  String getCloudProvider() {
    CLOUD_PROVIDER
  }

  /**
   * Note: this is needed because there is an interface method of this name that should be called
   * in lieu of the synthetic getter for the credentials instance variable.
   * @return
   */
  @Override
  OpenstackCredentials getCredentials() {
    credentials
  }

}
