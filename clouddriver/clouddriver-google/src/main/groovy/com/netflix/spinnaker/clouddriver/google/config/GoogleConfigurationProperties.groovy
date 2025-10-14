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

package com.netflix.spinnaker.clouddriver.google.config

import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.googlecommon.config.GoogleCommonManagedAccount
import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.clouddriver.security.AccessControlledAccountDefinition
import groovy.transform.Canonical
import groovy.transform.ToString
import org.springframework.boot.context.properties.NestedConfigurationProperty

class GoogleConfigurationProperties {
  public static final int ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT = 300
  public static final int ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS = 8

  /**
   * health check related config settings
   */
  @Canonical
  static class HealthConfig {
    /**
     * flag to toggle verifying account health check. by default, account health check is enabled.
     */
    boolean verifyAccountHealth = true
  }

  @ToString(includeNames = true)
  @JsonTypeName("google")
  static class ManagedAccount extends GoogleCommonManagedAccount implements AccessControlledAccountDefinition {
    boolean alphaListed
    List<String> imageProjects
    ConsulConfig consul
    String userDataFile
    // Takes a list of regions you want indexed, per-account. Will default to the value in
    // defaultRegions if left unspecified. An empty list will index no regions.
    List<String> regions
    boolean required
    String namingStrategy = "gceAnnotations"
  }

  List<ManagedAccount> accounts = []
  int asyncOperationTimeoutSecondsDefault = ASYNC_OPERATION_TIMEOUT_SECONDS_DEFAULT
  int asyncOperationMaxPollingIntervalSeconds = ASYNC_OPERATION_MAX_POLLING_INTERVAL_SECONDS
  List<String> baseImageProjects
  long maxMIGPageSize = 50
  // Takes a list of regions you want indexed. Will default to indexing all regions if left
  // unspecified. An empty list will index no regions.
  List<String> defaultRegions

  @NestedConfigurationProperty
  final HealthConfig health = new HealthConfig()
}
