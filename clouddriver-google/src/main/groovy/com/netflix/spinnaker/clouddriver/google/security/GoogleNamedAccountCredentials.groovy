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

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Region
import com.google.api.services.compute.model.RegionList
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.TupleConstructor

@TupleConstructor
class GoogleNamedAccountCredentials implements AccountCredentials<GoogleCredentials> {

  final String name // aka accountName
  final String environment
  boolean httpLoadBalancingEnabled // TODO(jacobkiefer): Feature flag for L7 development.
  final String accountType
  final String cloudProvider = GoogleCloudProvider.GCE // duh.
  final List<String> requiredGroupMembership
  final GoogleCredentials credentials

  final String project
  final String applicationName
  final List<String> imageProjects
  final ComputeVersion computeVersion
  final Map<String, List<String>> regionToZonesMap
  final ConsulConfig consulConfig
  final Compute compute

  static class Builder {
    String name
    String environment
    boolean httpLoadBalancingEnabled // TODO(jacobkiefer): Feature flag for L7 development.
    String accountType
    List<String> requiredGroupMembership = []
    String project
    String applicationName
    List<String> imageProjects = []
    ComputeVersion computeVersion = ComputeVersion.V1
    Map<String, List<String>> regionToZonesMap = [:]
    String jsonKey
    GoogleCredentials credentials
    Compute compute
    ConsulConfig consulConfig

    /**
     * If true, overwrites any value in regionToZoneMap with values from the platform.
     */
    boolean regionLookupEnabled = true

    Builder name(String name) {
      this.name = name
      return this
    }

    Builder environment(String environment) {
      this.environment = environment
      return this
    }

    // TODO(jacobkiefer): Feature flag for L7 development.
    Builder httpLoadBalancingEnabled(boolean httpLoadBalancingEnabled) {
      this.httpLoadBalancingEnabled = httpLoadBalancingEnabled
      return this
    }

    Builder accountType(String accountType) {
      this.accountType = accountType
      return this
    }

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder project(String project) {
      this.project = project
      return this
    }

    Builder applicationName(String applicationName) {
      this.applicationName = applicationName
      return this
    }

    Builder imageProjects(List<String> imageProjects) {
      this.imageProjects = imageProjects
      return this
    }

    Builder computeVersion(ComputeVersion version) {
      this.computeVersion = version
      return this
    }

    Builder jsonKey(String jsonKey) {
      this.jsonKey = jsonKey
      return this
    }

    Builder regionToZonesMap(Map<String, List<String>> regionToZonesMap) {
      this.regionToZonesMap = regionToZonesMap
      return this
    }

    Builder regionLookupEnabled(boolean enabled) {
      this.regionLookupEnabled = enabled
      return this
    }

    Builder consulConfig(ConsulConfig consulConfig) {
      if (consulConfig?.enabled) {
        consulConfig.applyDefaults()
        this.consulConfig = consulConfig
      }
      return this
    }

    @VisibleForTesting
    Builder credentials(GoogleCredentials credentials) {
      this.credentials = credentials
      this.regionLookupEnabled = false
      return this
    }

    @VisibleForTesting
    Builder compute(Compute compute) {
      this.compute = compute
      this.regionLookupEnabled = false
      return this
    }


    GoogleNamedAccountCredentials build() {
      GoogleCredentials credentials = this.credentials
      if (credentials == null) {
        credentials = jsonKey ?
          new GoogleJsonCredentials(project, computeVersion, jsonKey) :
          new GoogleCredentials(project, computeVersion)
      }
      Compute compute = this.compute
      if (compute == null) {
        compute = credentials.getCompute(applicationName)
      }

      if (regionLookupEnabled) {
        regionToZonesMap = queryRegions(compute, project)
      }

      new GoogleNamedAccountCredentials(name,
                                        environment,
                                        httpLoadBalancingEnabled,
                                        accountType,
                                        GoogleCloudProvider.GCE,
                                        requiredGroupMembership,
                                        credentials,
                                        project,
                                        applicationName,
                                        imageProjects,
                                        computeVersion,
                                        regionToZonesMap,
                                        consulConfig,
                                        compute)

    }
  }

  public List<Map> getRegions() {
    List<Map> regionList = []

    regionToZonesMap.each { String region, List<String> zones ->
      Map regionMap = new HashMap()
      regionMap.put("name", region)
      regionMap.put("zones", zones)
      regionList.add(regionMap)
    }
    return regionList
  }

  public String regionFromZone(String zone) {
    if (zone == null || regionToZonesMap == null) {
      return null
    }
    for (String region : regionToZonesMap.keySet()) {
      if (regionToZonesMap.get(region).contains(zone)) {
        return region
      }
    }
    return null
  }

  public List<String> getZonesFromRegion(String region) {
    return region != null && regionToZonesMap != null ? regionToZonesMap.get(region) : null
  }


  private static Map<String, List<String>> queryRegions(Compute compute, String project) {
    RegionList regionList = fetchRegions(compute, project)
    return convertToMap(regionList)
  }

  @VisibleForTesting
  static Map<String, List<String>> convertToMap(RegionList regionList) {
    regionList.items.collectEntries { Region region ->
      [(region.name): region.zones.collect { String zone -> GCEUtil.getLocalName(zone) }]
    }
  }

  private static RegionList fetchRegions(Compute compute, String project) {
    try {
      return compute.regions().list(project).execute()
    } catch (IOException ioe) {
      throw new RuntimeException("Failed loading regions for " + project, ioe)
    }
  }
}
