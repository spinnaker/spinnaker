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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.google.GoogleExecutor
import spock.lang.Specification

class GoogleNamedAccountCredentialsSpec extends Specification implements TestDefaults {
  private static final String PROJECT = "test-project"

  def 'regionlist should convert to map'() {
    setup:
      Region r1 = new Region()
      r1.setName("region1")
      r1.setZones(['region1/z1', 'region1/z2', 'region1/z3'])

      Region r2 = new Region()
      r2.setName("region2")
      r2.setZones(['region2/z4', 'region2/z5', 'region2/z6'])

      RegionList rl = new RegionList()
      rl.setItems([r1, r2])

    when:
      def map = GoogleNamedAccountCredentials.convertRegionListToMap(rl)

    then:
      map.size() == 2
      map.region1 == ['z1', 'z2', 'z3']
      map.region2 == ['z4', 'z5', 'z6']
  }

  def 'instanceTypeList should convert to map'() {
    setup:
      MachineTypeAggregatedList instanceTypeList = new ObjectMapper().convertValue(INSTANCE_TYPE_LIST, MachineTypeAggregatedList)

    when:
      def map = GoogleNamedAccountCredentials.convertInstanceTypeListToMap(instanceTypeList)
      GoogleNamedAccountCredentials.populateRegionInstanceTypes(map, REGION_TO_ZONES)

    then:
      'us-central1-a' in map
      map['us-central1-a'].instanceTypes?.size() == 15
      map['us-central1-a'].vCpuMax == 16
      'us-central1-b' in map
      map['us-central1-b'].instanceTypes?.size() == 18
      map['us-central1-b'].vCpuMax == 32
      // This proves that zone us-central1-a is not being considered when calculating regional capabilities.
      'us-central1' in map
      map['us-central1'].instanceTypes?.size() == 18
      map['us-central1'].vCpuMax == 32

      'us-east1-b' in map
      map['us-east1-b'].instanceTypes?.size() == 18
      map['us-east1-b'].vCpuMax == 32
      'us-east1' in map
      map['us-east1'].instanceTypes?.size() == 18
      map['us-east1'].vCpuMax == 32

      'europe-west1-b' in map
      map['europe-west1-b'].instanceTypes?.size() == 15
      map['europe-west1-b'].vCpuMax == 16
      'europe-west1-c' in map
      map['europe-west1-c'].instanceTypes?.size() == 18
      map['europe-west1-c'].vCpuMax == 32
      'europe-west1' in map
      map['europe-west1'].instanceTypes?.size() == 15
      map['europe-west1'].vCpuMax == 16

    when:
      // Do a deep copy of just the path we intend to modify.
      def instanceTypeListCopy = INSTANCE_TYPE_LIST + [:]
      instanceTypeListCopy.items = instanceTypeListCopy.items + [:]
      instanceTypeListCopy.items['zones/europe-west1-b'] = instanceTypeListCopy.items['zones/europe-west1-b'] + [:]

      // Let's make europe-west1-b support up to 32 guest cpus now.
      instanceTypeListCopy.items['zones/europe-west1-b'].machineTypes = INSTANCE_TYPES_WITH_32

      // Rebuild the map.
      instanceTypeList = new ObjectMapper().convertValue(instanceTypeListCopy, MachineTypeAggregatedList)
      map = GoogleNamedAccountCredentials.convertInstanceTypeListToMap(instanceTypeList)
      GoogleNamedAccountCredentials.populateRegionInstanceTypes(map, REGION_TO_ZONES)

    then:
      'europe-west1-b' in map
      map['europe-west1-b'].instanceTypes?.size() == 18
      map['europe-west1-b'].vCpuMax == 32
      'europe-west1' in map
      map['europe-west1'].instanceTypes?.size() == 18
      map['europe-west1'].vCpuMax == 32

    when:
      // Do a deep copy of just the path we intend to modify.
      instanceTypeListCopy = INSTANCE_TYPE_LIST + [:]
      instanceTypeListCopy.items = instanceTypeListCopy.items + [:]
      instanceTypeListCopy.items['zones/europe-west1-b'] = instanceTypeListCopy.items['zones/europe-west1-b'] + [:]

      // Let's make europe-west1-b support up to 64 guest cpus now.
      instanceTypeListCopy.items['zones/europe-west1-b'].machineTypes = INSTANCE_TYPES_WITH_64

      // Rebuild the map.
      instanceTypeList = new ObjectMapper().convertValue(instanceTypeListCopy, MachineTypeAggregatedList)
      map = GoogleNamedAccountCredentials.convertInstanceTypeListToMap(instanceTypeList)
      GoogleNamedAccountCredentials.populateRegionInstanceTypes(map, REGION_TO_ZONES)

    then:
      'europe-west1-b' in map
      map['europe-west1-b'].instanceTypes?.size() == 21
      map['europe-west1-b'].vCpuMax == 64
      'europe-west1' in map
      // Adding the 64 guest cpu types to just one zone is not enough to increase the max for the entire region.
      map['europe-west1'].instanceTypes?.size() == 18
      map['europe-west1'].vCpuMax == 32

    when:
      // Do a deep copy of just the paths we intend to modify.
      instanceTypeListCopy = INSTANCE_TYPE_LIST + [:]
      instanceTypeListCopy.items = instanceTypeListCopy.items + [:]
      instanceTypeListCopy.items['zones/europe-west1-b'] = instanceTypeListCopy.items['zones/europe-west1-b'] + [:]
      instanceTypeListCopy.items['zones/europe-west1-c'] = instanceTypeListCopy.items['zones/europe-west1-c'] + [:]
      instanceTypeListCopy.items['zones/europe-west1-d'] = instanceTypeListCopy.items['zones/europe-west1-d'] + [:]

      // Let's make all 3 zones in europe-west1 support up to 64 guest cpus now.
      instanceTypeListCopy.items['zones/europe-west1-b'].machineTypes = INSTANCE_TYPES_WITH_64
      instanceTypeListCopy.items['zones/europe-west1-c'].machineTypes = INSTANCE_TYPES_WITH_64
      instanceTypeListCopy.items['zones/europe-west1-d'].machineTypes = INSTANCE_TYPES_WITH_64

      // Rebuild the map.
      instanceTypeList = new ObjectMapper().convertValue(instanceTypeListCopy, MachineTypeAggregatedList)
      map = GoogleNamedAccountCredentials.convertInstanceTypeListToMap(instanceTypeList)
      GoogleNamedAccountCredentials.populateRegionInstanceTypes(map, REGION_TO_ZONES)

    then:
      'europe-west1-b' in map
      map['europe-west1-b'].instanceTypes?.size() == 21
      map['europe-west1-b'].vCpuMax == 64
      'europe-west1-c' in map
      map['europe-west1-c'].instanceTypes?.size() == 21
      map['europe-west1-c'].vCpuMax == 64
      'europe-west1-d' in map
      map['europe-west1-d'].instanceTypes?.size() == 21
      map['europe-west1-d'].vCpuMax == 64
      'europe-west1' in map
      map['europe-west1'].instanceTypes?.size() == 21
      map['europe-west1'].vCpuMax == 64
  }

  def 'zoneList should convert to cpu platforms map'() {
    setup:
      List<Zone> zoneItemsList = new ObjectMapper().convertValue(ZONE_ITEMS_LIST, new TypeReference<List<Zone>>() {})
      ZoneList zoneList = new ZoneList(items: zoneItemsList)
      Map<String, List<String>> locationToCpuPlatformsMap = new HashMap<>()

    when:
      GoogleNamedAccountCredentials.populateLocationToCpuPlatformsMap(zoneList, locationToCpuPlatformsMap)
      GoogleNamedAccountCredentials.populateRegionCpuPlatforms(locationToCpuPlatformsMap, REGION_TO_ZONES)

    then:
      locationToCpuPlatformsMap['asia-east1-a'] == ["Intel Ivy Bridge", "Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['asia-east1-b'] == ["Intel Ivy Bridge", "Intel Skylake"]
      locationToCpuPlatformsMap['asia-east1-c'] == ["Intel Ivy Bridge"]
      locationToCpuPlatformsMap['asia-east1'] == ["Intel Ivy Bridge"]

      locationToCpuPlatformsMap['asia-northeast1-a'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['asia-northeast1-b'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['asia-northeast1-c'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['asia-northeast1'] == ["Intel Broadwell"]

      locationToCpuPlatformsMap['asia-southeast1-a'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['asia-southeast1-b'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['asia-southeast1'] == ["Intel Broadwell"]

      locationToCpuPlatformsMap['europe-west1-b'] == ["Intel Sandy Bridge", "Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['europe-west1-c'] == ["Intel Ivy Bridge", "Intel Broadwell"]
      locationToCpuPlatformsMap['europe-west1-d'] == ["Intel Haswell", "Intel Skylake"]
      !locationToCpuPlatformsMap['europe-west1']

      !locationToCpuPlatformsMap['europe-west2-a']
      !locationToCpuPlatformsMap['europe-west2-b']
      !locationToCpuPlatformsMap['europe-west2-c']
      !locationToCpuPlatformsMap['europe-west2']

      locationToCpuPlatformsMap['us-central1-a'] == ["Intel Sandy Bridge", "Intel Broadwell"]
      locationToCpuPlatformsMap['us-central1-b'] == ["Intel Haswell", "Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['us-central1-c'] == ["Intel Haswell", "Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['us-central1-f'] == ["Intel Ivy Bridge", "Intel Broadwell"]
      locationToCpuPlatformsMap['us-central1'] == ["Intel Broadwell"]

      !locationToCpuPlatformsMap['us-east1-a']
      locationToCpuPlatformsMap['us-east1-b'] == ["Intel Haswell", "Intel Broadwell"]
      locationToCpuPlatformsMap['us-east1-c'] == ["Intel Haswell", "Intel Broadwell"]
      locationToCpuPlatformsMap['us-east1-d'] == ["Intel Haswell", "Intel Broadwell"]
      locationToCpuPlatformsMap['us-east1'] == ["Intel Haswell", "Intel Broadwell"]

      locationToCpuPlatformsMap['us-east4-a'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['us-east4-b'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['us-east4-c'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['us-east4'] == ["Intel Broadwell"]

      locationToCpuPlatformsMap['us-west1-a'] == ["Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['us-west1-b'] == ["Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['us-west1-c'] == ["Intel Broadwell"]
      locationToCpuPlatformsMap['us-west1'] == ["Intel Broadwell"]
  }

  def 'instanceTypeList with pagination should aggregate all pages'() {
    setup:
      // Create first page of results
      def firstPageData = [
        items: [
          'zones/us-central1-a': [
            machineTypes: INSTANCE_TYPES_WITH_16
          ]
        ],
        nextPageToken: 'page2token'
      ]
      MachineTypeAggregatedList firstPage = new ObjectMapper().convertValue(firstPageData, MachineTypeAggregatedList)

      // Create second page of results
      def secondPageData = [
        items: [
          'zones/us-central1-b': [
            machineTypes: INSTANCE_TYPES_WITH_32
          ]
        ],
        nextPageToken: null
      ]
      MachineTypeAggregatedList secondPage = new ObjectMapper().convertValue(secondPageData, MachineTypeAggregatedList)

    when:
      // Process first page
      def map = GoogleNamedAccountCredentials.convertInstanceTypeListToMap(firstPage)

      // Process second page and merge
      def secondMap = GoogleNamedAccountCredentials.convertInstanceTypeListToMap(secondPage)
      secondMap.each { zone, instanceTypes ->
        if (zone in map) {
          map[zone].instanceTypes += instanceTypes.instanceTypes
          map[zone].vCpuMax = Math.max(map[zone].vCpuMax, instanceTypes.vCpuMax)
        } else {
          map[zone] = instanceTypes
        }
      }

    then:
      'us-central1-a' in map
      map['us-central1-a'].instanceTypes?.size() == 15
      map['us-central1-a'].vCpuMax == 16
      'us-central1-b' in map
      map['us-central1-b'].instanceTypes?.size() == 18
      map['us-central1-b'].vCpuMax == 32
  }

  def 'zoneList with pagination should aggregate all pages'() {
    setup:
      // Create first page with some zones
      List<Zone> firstPageZones = [
        new Zone(name: "us-central1-a", availableCpuPlatforms: ["Intel Sandy Bridge", "Intel Broadwell"]),
        new Zone(name: "us-central1-b", availableCpuPlatforms: ["Intel Haswell", "Intel Broadwell", "Intel Skylake"])
      ]
      ZoneList firstPage = new ZoneList(items: firstPageZones, nextPageToken: 'page2token')

      // Create second page with more zones
      List<Zone> secondPageZones = [
        new Zone(name: "us-central1-c", availableCpuPlatforms: ["Intel Haswell", "Intel Broadwell", "Intel Skylake"]),
        new Zone(name: "us-central1-f", availableCpuPlatforms: ["Intel Ivy Bridge", "Intel Broadwell"])
      ]
      ZoneList secondPage = new ZoneList(items: secondPageZones, nextPageToken: null)

      Map<String, List<String>> locationToCpuPlatformsMap = new HashMap<>()

    when:
      // Process first page
      GoogleNamedAccountCredentials.populateLocationToCpuPlatformsMap(firstPage, locationToCpuPlatformsMap)

      // Process second page
      GoogleNamedAccountCredentials.populateLocationToCpuPlatformsMap(secondPage, locationToCpuPlatformsMap)

    then:
      locationToCpuPlatformsMap.size() == 4
      locationToCpuPlatformsMap['us-central1-a'] == ["Intel Sandy Bridge", "Intel Broadwell"]
      locationToCpuPlatformsMap['us-central1-b'] == ["Intel Haswell", "Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['us-central1-c'] == ["Intel Haswell", "Intel Broadwell", "Intel Skylake"]
      locationToCpuPlatformsMap['us-central1-f'] == ["Intel Ivy Bridge", "Intel Broadwell"]
  }

  def 'acceleratorTypeList with pagination should aggregate all pages'() {
    setup:
      // Create first page of accelerator types
      def firstPageData = [
        items: [
          'zones/us-central1-a': [
            acceleratorTypes: [
              [name: 'nvidia-tesla-k80', description: 'NVIDIA Tesla K80'],
              [name: 'nvidia-tesla-p4', description: 'NVIDIA Tesla P4']
            ]
          ]
        ],
        nextPageToken: 'page2token'
      ]
      AcceleratorTypeAggregatedList firstPage = new ObjectMapper().convertValue(firstPageData, AcceleratorTypeAggregatedList)

      // Create second page of accelerator types
      def secondPageData = [
        items: [
          'zones/us-central1-b': [
            acceleratorTypes: [
              [name: 'nvidia-tesla-v100', description: 'NVIDIA Tesla V100'],
              [name: 'nvidia-tesla-p100', description: 'NVIDIA Tesla P100']
            ]
          ]
        ],
        nextPageToken: null
      ]
      AcceleratorTypeAggregatedList secondPage = new ObjectMapper().convertValue(secondPageData, AcceleratorTypeAggregatedList)

    when:
      // Process first page
      def map = GoogleNamedAccountCredentials.convertAcceleratorTypeListToMap(firstPage)

      // Process second page and merge (this simulates the pagination logic in queryAcceleratorTypes)
      def secondMap = GoogleNamedAccountCredentials.convertAcceleratorTypeListToMap(secondPage)
      secondMap.each { zone, acceleratorTypes ->
        if (zone in map) {
          map[zone].acceleratorTypes += acceleratorTypes.acceleratorTypes
        } else {
          map[zone] = acceleratorTypes
        }
      }

    then:
      'us-central1-a' in map
      map['us-central1-a'].acceleratorTypes != null
      'us-central1-b' in map
      map['us-central1-b'].acceleratorTypes != null
  }

  def 'queryAcceleratorTypes should use page token for pagination'() {
    setup:
      GoogleExecutor.globalRegistry = new NoopRegistry()
      def computeMock = Mock(Compute)
      def acceleratorTypesMock = Mock(Compute.AcceleratorTypes)
      def firstCallMock = Mock(Compute.AcceleratorTypes.AggregatedList)
      def secondCallMock = Mock(Compute.AcceleratorTypes.AggregatedList)

      // First page response
      def firstPageData = [
        items: [
          'zones/us-central1-a': [
            acceleratorTypes: [
              [name: 'nvidia-tesla-k80', description: 'NVIDIA Tesla K80']
            ]
          ]
        ],
        nextPageToken: 'token123'
      ]
      AcceleratorTypeAggregatedList firstPage = new ObjectMapper().convertValue(firstPageData, AcceleratorTypeAggregatedList)

      // Second page response
      def secondPageData = [
        items: [
          'zones/us-central1-b': [
            acceleratorTypes: [
              [name: 'nvidia-tesla-v100', description: 'NVIDIA Tesla V100']
            ]
          ]
        ],
        nextPageToken: null
      ]
      AcceleratorTypeAggregatedList secondPage = new ObjectMapper().convertValue(secondPageData, AcceleratorTypeAggregatedList)

    when:
      def result = GoogleNamedAccountCredentials.queryAcceleratorTypes(computeMock, PROJECT)

    then:
      // First call without page token
      1 * computeMock.acceleratorTypes() >> acceleratorTypesMock
      1 * acceleratorTypesMock.aggregatedList(PROJECT) >> firstCallMock
      1 * firstCallMock.execute() >> firstPage

      // Second call WITH page token
      1 * computeMock.acceleratorTypes() >> acceleratorTypesMock
      1 * acceleratorTypesMock.aggregatedList(PROJECT) >> secondCallMock
      1 * secondCallMock.setPageToken('token123') >> secondCallMock
      1 * secondCallMock.execute() >> secondPage

      // Verify both zones are in the result
      result.containsKey('us-central1-a')
      result.containsKey('us-central1-b')
  }

  def 'queryInstanceTypes should use page token for pagination'() {
    setup:
      GoogleExecutor.globalRegistry = new NoopRegistry()
      def computeMock = Mock(Compute)
      def machineTypesMock = Mock(Compute.MachineTypes)
      def firstCallMock = Mock(Compute.MachineTypes.AggregatedList)
      def secondCallMock = Mock(Compute.MachineTypes.AggregatedList)

      // First page response
      def firstPageData = [
        items: [
          'zones/us-central1-a': [
            machineTypes: INSTANCE_TYPES_WITH_16
          ]
        ],
        nextPageToken: 'token456'
      ]
      MachineTypeAggregatedList firstPage = new ObjectMapper().convertValue(firstPageData, MachineTypeAggregatedList)

      // Second page response
      def secondPageData = [
        items: [
          'zones/us-central1-b': [
            machineTypes: INSTANCE_TYPES_WITH_32
          ]
        ],
        nextPageToken: null
      ]
      MachineTypeAggregatedList secondPage = new ObjectMapper().convertValue(secondPageData, MachineTypeAggregatedList)

    when:
      def result = GoogleNamedAccountCredentials.queryInstanceTypes(computeMock, PROJECT, [:])

    then:
      // First call without page token
      1 * computeMock.machineTypes() >> machineTypesMock
      1 * machineTypesMock.aggregatedList(PROJECT) >> firstCallMock
      1 * firstCallMock.execute() >> firstPage

      // Second call WITH page token
      1 * computeMock.machineTypes() >> machineTypesMock
      1 * machineTypesMock.aggregatedList(PROJECT) >> secondCallMock
      1 * secondCallMock.setPageToken('token456') >> secondCallMock
      1 * secondCallMock.execute() >> secondPage

      // Verify both zones are in the result
      result.containsKey('us-central1-a')
      result.containsKey('us-central1-b')
      result['us-central1-a'].instanceTypes?.size() == 15
      result['us-central1-b'].instanceTypes?.size() == 18
  }

  def 'queryCpuPlatforms should use page token for pagination'() {
    setup:
    GoogleExecutor.globalRegistry = new NoopRegistry()
      def computeMock = Mock(Compute)
      def zonesMock = Mock(Compute.Zones)
      def firstCallMock = Mock(Compute.Zones.List)
      def secondCallMock = Mock(Compute.Zones.List)

      // First page with some zones
      List<Zone> firstPageZones = [
        new Zone(name: "us-central1-a", availableCpuPlatforms: ["Intel Sandy Bridge", "Intel Broadwell"])
      ]
      ZoneList firstPage = new ZoneList(items: firstPageZones, nextPageToken: 'token789')

      // Second page with more zones
      List<Zone> secondPageZones = [
        new Zone(name: "us-central1-b", availableCpuPlatforms: ["Intel Haswell", "Intel Broadwell", "Intel Skylake"])
      ]
      ZoneList secondPage = new ZoneList(items: secondPageZones, nextPageToken: null)

    when:
      def result = GoogleNamedAccountCredentials.queryCpuPlatforms(computeMock, PROJECT, [:])

    then:
      // First call without page token
      1 * computeMock.zones() >> zonesMock
      1 * zonesMock.list(PROJECT) >> firstCallMock
      1 * firstCallMock.execute() >> firstPage

      // Second call WITH page token
      1 * computeMock.zones() >> zonesMock
      1 * zonesMock.list(PROJECT) >> secondCallMock
      1 * secondCallMock.setPageToken('token789') >> secondCallMock
      1 * secondCallMock.execute() >> secondPage

      // Verify both zones are in the result
      result.containsKey('us-central1-a')
      result.containsKey('us-central1-b')
      result['us-central1-a'] == ["Intel Sandy Bridge", "Intel Broadwell"]
      result['us-central1-b'] == ["Intel Haswell", "Intel Broadwell", "Intel Skylake"]
  }
}
