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
import com.google.api.services.compute.model.MachineTypeAggregatedList
import com.google.api.services.compute.model.Region
import com.google.api.services.compute.model.RegionList
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.clouddriver.consul.config.ConsulConfig
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstanceTypeDisk
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.fiat.model.resources.Permissions
import groovy.transform.TupleConstructor

@TupleConstructor
class GoogleNamedAccountCredentials implements AccountCredentials<GoogleCredentials> {

  final String name // aka accountName
  final String environment
  final String accountType
  final String cloudProvider = GoogleCloudProvider.ID // duh.
  final List<String> requiredGroupMembership
  final Permissions permissions
  final GoogleCredentials credentials

  final String project
  final String applicationName
  final List<String> imageProjects
  final ComputeVersion computeVersion
  final Map<String, List<String>> regionToZonesMap
  final Map<String, Map> locationToInstanceTypesMap
  final List<GoogleInstanceTypeDisk> instanceTypeDisks
  final ConsulConfig consulConfig
  final Compute compute
  final String userDataFile

  static class Builder {
    String name
    String environment
    String accountType
    List<String> requiredGroupMembership = []
    Permissions permissions = Permissions.EMPTY
    String project
    String applicationName
    List<String> imageProjects = []
    ComputeVersion computeVersion = ComputeVersion.DEFAULT
    Map<String, List<String>> regionToZonesMap = [:]
    Map<String, Map> locationToInstanceTypesMap = [:]
    List<GoogleInstanceTypeDisk> instanceTypeDisks = []
    String jsonKey
    GoogleCredentials credentials
    Compute compute
    ConsulConfig consulConfig
    String userDataFile

    /**
     * If true, overwrites any value in regionToZoneMap and locationToInstanceTypesMap with values from the platform.
     */
    boolean liveLookupsEnabled = true

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

    Builder requiredGroupMembership(List<String> requiredGroupMembership) {
      this.requiredGroupMembership = requiredGroupMembership
      return this
    }

    Builder permissions(Permissions permissions) {
      if (permissions.isRestricted()) {
        this.requiredGroupMembership = []
        this.permissions = permissions
      }
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

    @VisibleForTesting
    Builder regionToZonesMap(Map<String, List<String>> regionToZonesMap) {
      this.regionToZonesMap = regionToZonesMap
      return this
    }

    @VisibleForTesting
    Builder locationToInstanceTypesMap(Map<String, Map> locationToInstanceTypesMap) {
      this.locationToInstanceTypesMap = locationToInstanceTypesMap
      return this
    }

    Builder instanceTypeDisks(List<GoogleInstanceTypeDisk> instanceTypeDisks) {
      this.instanceTypeDisks = instanceTypeDisks
      return this
    }

    Builder liveLookupsEnabled(boolean enabled) {
      this.liveLookupsEnabled = enabled
      return this
    }

    Builder consulConfig(ConsulConfig consulConfig) {
      if (consulConfig?.enabled) {
        consulConfig.applyDefaults()
        this.consulConfig = consulConfig
      }
      return this
    }

    Builder userDataFile(String userDataFile) {
      this.userDataFile = userDataFile
      return this
    }

    @VisibleForTesting
    Builder credentials(GoogleCredentials credentials) {
      this.credentials = credentials
      this.liveLookupsEnabled = false
      return this
    }

    @VisibleForTesting
    Builder compute(Compute compute) {
      this.compute = compute
      this.liveLookupsEnabled = false
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

      if (liveLookupsEnabled) {
        regionToZonesMap = queryRegions(compute, project)
        locationToInstanceTypesMap = queryInstanceTypes(compute, project, regionToZonesMap)
      }

      new GoogleNamedAccountCredentials(name,
                                        environment,
                                        accountType,
                                        GoogleCloudProvider.ID,
                                        requiredGroupMembership,
                                        permissions,
                                        credentials,
                                        project,
                                        applicationName,
                                        imageProjects,
                                        computeVersion,
                                        regionToZonesMap,
                                        locationToInstanceTypesMap,
                                        instanceTypeDisks,
                                        consulConfig,
                                        compute,
                                        userDataFile)

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
    return regionFromZone(zone, regionToZonesMap)
  }

  public static String regionFromZone(String zone, Map<String, List<String>> regionToZonesMap) {
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
    return convertRegionListToMap(regionList)
  }

  @VisibleForTesting
  static Map<String, List<String>> convertRegionListToMap(RegionList regionList) {
    return regionList.items.collectEntries { Region region ->
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

  private static Map<String, Map> queryInstanceTypes(Compute compute,
                                                     String project,
                                                     Map<String, List<String>> regionToZonesMap) {
    MachineTypeAggregatedList instanceTypeList = compute.machineTypes().aggregatedList(project).execute()
    String nextPageToken = instanceTypeList.getNextPageToken()
    Map<String, Map> zoneToInstanceTypesMap = convertInstanceTypeListToMap(instanceTypeList)

    while (nextPageToken) {
      instanceTypeList = compute.machineTypes().aggregatedList(project).setPageToken(nextPageToken).execute()
      nextPageToken = instanceTypeList.getNextPageToken()

      Map<String, Map> subsequentZoneToInstanceTypesMap = convertInstanceTypeListToMap(instanceTypeList)

      subsequentZoneToInstanceTypesMap.each { zone, instanceTypes ->
        if (zone in zoneToInstanceTypesMap) {
          zoneToInstanceTypesMap[zone].instanceTypes += instanceTypes.instanceTypes
          zoneToInstanceTypesMap[zone].vCpuMax = Math.max(zoneToInstanceTypesMap[zone].vCpuMax, instanceTypes.vCpuMax)
        } else {
          zoneToInstanceTypesMap[zone] = instanceTypes
        }
      }
    }

    populateRegionInstanceTypes(zoneToInstanceTypesMap, regionToZonesMap)

    return zoneToInstanceTypesMap
  }

  @VisibleForTesting
  static Map<String, Map> convertInstanceTypeListToMap(MachineTypeAggregatedList instanceTypeList) {
    // Populate zone to instance types mappings.
    def zoneToInstanceTypesMap = instanceTypeList.items.collectEntries { zone, machineTypesScopedList ->
      zone = GCEUtil.getLocalName(zone)

      if (machineTypesScopedList.machineTypes) {
        return [(zone): [
          instanceTypes: machineTypesScopedList.machineTypes.collect { it.name },
          vCpuMax      : machineTypesScopedList.machineTypes.max { it.guestCpus }.guestCpus
        ]]
      } else {
        return [:]
      }
    }

    return zoneToInstanceTypesMap
  }

  static void populateRegionInstanceTypes(Map<String, Map> locationToInstanceTypesMap,
                                          Map<String, List<String>> regionToZonesMap) {
    // Populate region to instance types mappings.
    regionToZonesMap.each { region, zoneNames ->
      // The RMIG will deploy to the last 3 zones (after sorting by zone name).
      if (zoneNames.size() > 3) {
        zoneNames = zoneNames.sort().drop(zoneNames.size() - 3)
      }

      def matchingInstanceTypesDescriptors = locationToInstanceTypesMap.findAll { zone, instanceTypesDescriptor ->
        zone in zoneNames
      }.values()

      if (matchingInstanceTypesDescriptors) {
        def matchingInstanceTypes = matchingInstanceTypesDescriptors.collect { it.instanceTypes }
        def firstZoneInstanceTypes = matchingInstanceTypes[0]
        def remainingZoneInstanceTypes = matchingInstanceTypes.drop(1)
        // Determine what instance types are present in all zones in this region.
        def commonInstanceTypes = remainingZoneInstanceTypes.inject(firstZoneInstanceTypes) { acc, el -> acc.intersect(el) }

        // Determine the maximum vCpu count for this region by identifying the smallest zonal vCpuMax.
        def vCpuMaxInRegion = matchingInstanceTypesDescriptors.min { it.vCpuMax }.vCpuMax

        locationToInstanceTypesMap[region] = [
          instanceTypes: commonInstanceTypes,
          vCpuMax: vCpuMaxInRegion
        ]
      }
    }
  }
}
