/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.provider.view

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider
import com.netflix.spinnaker.clouddriver.aws.data.Keys
import com.netflix.spinnaker.clouddriver.aws.model.AmazonServerGroup
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import org.junit.jupiter.api.BeforeEach
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_CONFIGS
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.LAUNCH_TEMPLATES
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

class AmazonClusterProviderSpec extends Specification {
  def cacheView = Mock(Cache)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def amazonCloudProvider = Mock(AmazonCloudProvider)
  def awsProvider = Mock(AwsProvider)

  @Subject
  def provider = new AmazonClusterProvider(amazonCloudProvider, cacheView, awsProvider)

  def app = "app"
  def account = "test"
  def region = "us-east-1"
  def clusterName = "app-main"
  String clusterId = Keys.getClusterKey(clusterName, app, account)
  def clusterAttributes = [name: clusterName, application: app]

  def serverGroupName = "app-main-v000"
  String serverGroupId = Keys.getServerGroupKey(clusterName, serverGroupName, account, region)
  def serverGroup = [
    name: serverGroupName,
    instances: [],
    asg: [:]
  ]

  def launchTemplateName = "$serverGroupName-123"
  def launchConfigName = "$serverGroupName-123"

  @BeforeEach
  def setup() {
    serverGroup.asg.clear()
  }

  def "should get cluster details with build info"() {
    given:
    def imageId = "ami-1"
    def imageKey = Keys.getImageKey(imageId, account, region)
    def imageAttributes = [
      imageId: imageId,
      tags: [appversion: "app-0.487.0-h514.f4be391/job/1"]
    ]

    serverGroup.asg = [ launchConfigurationName: launchConfigName]
    def launchConfiguration = new DefaultCacheData(
      Keys.getLaunchConfigKey(launchConfigName, account, region),
      [imageId: imageId],
      [images: [imageKey]]
    )

    and:
    cacheView.supportsGetAllByApplication() >> false
    cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(app)) >> new DefaultCacheData(
      Keys.getApplicationKey(app), [name: app], [serverGroups: [serverGroupId], clusters: [clusterId]]
    )
    cacheView.getAll(LAUNCH_CONFIGS.ns, _ as Set) >> [launchConfiguration]
    cacheView.filterIdentifiers(CLUSTERS.ns, _) >> [clusterId]
    cacheView.getAll(CLUSTERS.ns, _ as Collection<String>) >> [new DefaultCacheData(clusterId, clusterAttributes, [serverGroups: [serverGroupId]])]
    cacheView.getAll(SERVER_GROUPS.ns, [ serverGroupId ], _ as CacheFilter) >> [
      new DefaultCacheData(serverGroupId, serverGroup, [launchConfigs: [launchConfiguration.id]])
    ]
    cacheView.getAll(IMAGES.ns, _ as Set) >> [
      new DefaultCacheData(imageKey, imageAttributes, [:])
    ]

    when:
    def result = provider.getClusterDetails(app)

    then:
    def clusters = result.values()
    def allServerGroups = clusters*.serverGroups.flatten() as Set<AmazonServerGroup>

    clusters.size() == 1
    allServerGroups.size() == 1
    allServerGroups[0].launchConfig != null
    allServerGroups[0].buildInfo != null
    allServerGroups[0].image != null
    allServerGroups[0].launchTemplate == null
    allServerGroups[0].mixedInstancesPolicy == null
  }

  def "should get cluster details by app with build info"() {
    given:
    def imageId = "ami-1"
    def imageKey = Keys.getImageKey(imageId, account, region)
    def imageAttributes = [
      imageId: imageId,
      tags: [appversion: "app-0.487.0-h514.f4be391/job/514"]
    ]

    serverGroup.asg = [ launchConfigurationName: launchConfigName]
    def launchConfiguration = new DefaultCacheData(
      Keys.getLaunchConfigKey(launchConfigName, account, region),
      [imageId: imageId],
      [images: [imageKey]]
    )

    def cluster = new DefaultCacheData(clusterId, clusterAttributes, [serverGroups: [serverGroupId]])
    def serverGroup = new DefaultCacheData(serverGroupId, serverGroup, [launchConfigs: [launchConfiguration.id]])
    def image = new DefaultCacheData(imageKey, imageAttributes, [:])

    and:
    cacheView.getAllByApplication(_, _, _) >> [
      serverGroups: [serverGroup],
      clusters: [cluster],
      launchConfigs: [launchConfiguration],
      images: [image]
    ]

    cacheView.getAll(LAUNCH_CONFIGS.ns, _ as Set) >> [launchConfiguration]
    cacheView.filterIdentifiers(CLUSTERS.ns, _) >> [cluster.id]
    cacheView.getAll(CLUSTERS.ns, _ as Collection<String>) >> [cluster]
    cacheView.getAll(SERVER_GROUPS.ns, [ serverGroupId ], _ as CacheFilter) >> [serverGroup]

    cacheView.getAll(IMAGES.ns, _ as Set) >> [image]

    when:
    def result = provider.getClusterDetails(app)

    then:
    def clusters = result.values()
    def allServerGroups = clusters*.serverGroups.flatten() as Set<AmazonServerGroup>

    clusters.size() == 1
    allServerGroups.size() == 1
    allServerGroups[0].launchConfig != null
    allServerGroups[0].buildInfo != null
    allServerGroups[0].image != null
    allServerGroups[0].launchTemplate == null
    allServerGroups[0].mixedInstancesPolicy == null
  }

  def "should resolve server group launch config"() {
    given:
    serverGroup.asg = [ launchConfigurationName: launchConfigName]
    def launchConfiguration = new DefaultCacheData(
      Keys.getLaunchConfigKey(launchConfigName, account, "us-east-1"), [ imageId: "ami-1"], [:])

    and:
    cacheView.getAll(LAUNCH_CONFIGS.ns, _ as Set) >> [launchConfiguration]
    cacheView.get(CLUSTERS.ns, clusterId) >> new DefaultCacheData(clusterId, clusterAttributes, [serverGroups: [serverGroupId]])
    cacheView.getAll(SERVER_GROUPS.ns, [ serverGroupId ], _ as CacheFilter) >> [
      new DefaultCacheData(serverGroupId, serverGroup, [launchConfigs: [launchConfiguration.id]])
    ]

    when:
    def result = provider.getCluster(app, account, clusterName)

    then:
    with(result) {
      def sg = serverGroups.first()
      type == "aws"
      name == clusterName
      accountName == account
      result.serverGroups.size() == 1
      sg.launchConfig == launchConfiguration.attributes
      sg.launchTemplate == null
      sg.mixedInstancesPolicy == null
    }
  }

  @Unroll
  def "should resolve server group launch template"() {
    given:
    serverGroup.asg = [
      launchTemplate: [
        launchTemplateName: launchTemplateName,
        version: asgLaunchTemplateVersion
      ]
    ]

    def defaultVersion = [
      launchTemplateName: launchTemplateName,
      versionNumber: 0,
      defaultVersion: true,
      launchTemplateData: [
        imageId: "ami-345"
      ]
    ]

    def latestVersion = [
      launchTemplateName: launchTemplateName,
      versionNumber: 1,
      defaultVersion: false,
      launchTemplateData: [
        imageId: "ami-123"
      ]
    ]

    def launchTemplate = new DefaultCacheData(
      Keys.getLaunchTemplateKey(launchTemplateName, account, "us-east-1"),
      [
        launchTemplateName: launchTemplateName,
        latestVersion: latestVersion,
        versions: [
          defaultVersion,
          latestVersion
        ]
      ], [:])

    and:
    cacheView.getAll(LAUNCH_TEMPLATES.ns, _ as Set) >> [launchTemplate]
    cacheView.get(CLUSTERS.ns, clusterId) >> new DefaultCacheData(clusterId, clusterAttributes, [serverGroups: [serverGroupId]])
    cacheView.getAll(SERVER_GROUPS.ns, [ serverGroupId ], _ as CacheFilter) >> [
      new DefaultCacheData(serverGroupId, serverGroup, [launchTemplates: [launchTemplate.id]])
    ]

    when:
    def result = provider.getCluster(app, account, clusterName)

    then:
    result.serverGroups.size() == 1
    result.serverGroups[0].launchConfig == null
    result.serverGroups[0].mixedInstancesPolicy == null
    result.serverGroups[0].launchTemplate.versionNumber == resolvedVersion

    where:
    asgLaunchTemplateVersion | resolvedVersion
    '1'                      | 1
    '$Default'               | 0
    '$Latest'                | 1
  }

  def "should get server group with expected properties for asg with launch configuration"() {
    given:
    def lcKey = Keys.getLaunchConfigKey(launchConfigName, account, region)
    def lcCacheAttr = [imageId: "ami-1"]
    def lcCache = new DefaultCacheData(lcKey, lcCacheAttr, [serverGroups: [serverGroupId]])

    and:
    def imageKey = Keys.getImageKey("ami-1", account, region)
    def imageCacheAttr = [imageId: "ami-1", tags: [appversion: "app-0.487.0-h514.f4be391/job/1"]]
    def imageCache = new DefaultCacheData(imageKey, imageCacheAttr, [:])

    and:
    serverGroup.asg = [ launchConfigName: launchConfigName]
    serverGroup["launchConfigName"] = launchConfigName
    def sgCache = new DefaultCacheData(serverGroupId, serverGroup, [launchConfigs: [lcCache.id]])

    and:
    cacheView.get(SERVER_GROUPS.ns, serverGroupId) >> sgCache
    cacheView.get(LAUNCH_CONFIGS.ns, lcKey) >> lcCache
    cacheView.get(IMAGES.ns, imageKey) >> imageCache

    when:
    def actualServerGroup = provider.getServerGroup(account, region, serverGroupName, false)

    then:
    actualServerGroup.image["imageId"] == imageCacheAttr["imageId"]
    actualServerGroup.launchConfig == lcCacheAttr
    actualServerGroup.launchTemplate == null
    actualServerGroup.mixedInstancesPolicy == null
  }

  def "should get server group with expected properties for asg with launch template"() {
    given:
    def latestVersion = [
      launchTemplateName: launchTemplateName,
      versionNumber: 1,
      defaultVersion: false,
      launchTemplateData: [
        imageId: "ami-1"
      ]
    ]
    def ltKey = Keys.getLaunchTemplateKey(launchTemplateName, account, region)
    def ltCacheAttr = [ launchTemplateName: launchTemplateName,
      latestVersion: latestVersion,
      versions: [latestVersion]]
    def ltCache = new DefaultCacheData(ltKey, ltCacheAttr, [serverGroups: [serverGroupId]])

    and:
    def imageKey = Keys.getImageKey("ami-1", account, region)
    def imageCacheAttr = [imageId: "ami-1", tags: [appversion: "app-0.487.0-h514.f4be391/job/1"]]
    def imageCache = new DefaultCacheData(imageKey, imageCacheAttr, [:])

    and:
    serverGroup.asg = [
      launchTemplate: [
        launchTemplateName: launchTemplateName,
        version: '1'
      ]
    ]
    def sgCache = new DefaultCacheData(serverGroupId, serverGroup, [launchTemplates: [ltCache.id]])

    and:
    cacheView.get(SERVER_GROUPS.ns, serverGroupId) >> sgCache
    cacheView.get(LAUNCH_TEMPLATES.ns, ltKey) >> ltCache
    cacheView.get(IMAGES.ns, imageKey) >> imageCache

    when:
    def actualServerGroup = provider.getServerGroup(account, region, serverGroupName, false)

    then:
    actualServerGroup.image["imageId"] == imageCacheAttr["imageId"]
    actualServerGroup.launchTemplate == latestVersion
    actualServerGroup.launchConfig == null
    actualServerGroup.mixedInstancesPolicy == null
  }

  def "should get server group with expected properties for asg with mixed instances policy"() {
    given:
    def latestVersion = [
      launchTemplateName: launchTemplateName,
      versionNumber: 1,
      defaultVersion: false,
      launchTemplateData: [
        imageId: "ami-1",
        instanceType: "some.type.small"
      ]
    ]
    def ltKey = Keys.getLaunchTemplateKey(launchTemplateName, account, region)
    def ltCacheAttr = [ launchTemplateName: launchTemplateName,
                        latestVersion: latestVersion,
                        versions: [latestVersion]]
    def ltCache = new DefaultCacheData(ltKey, ltCacheAttr, [serverGroups: [serverGroupId]])

    and:
    def imageKey = Keys.getImageKey("ami-1", account, region)
    def imageCacheAttr = [imageId: "ami-1", tags: [appversion: "app-0.487.0-h514.f4be391/job/1"]]
    def imageCache = new DefaultCacheData(imageKey, imageCacheAttr, [:])

    and:
    serverGroup.asg = [
      mixedInstancesPolicy: [
        instancesDistribution: [
          onDemandAllocationStrategy: "prioritized",
          onDemandBaseCapacity: 1,
          onDemandPercentageAboveBaseCapacity: 50,
          spotAllocationStrategy: "lowest-price",
          spotInstancePools: 4,
          spotMaxPrice: "1"
        ],
        launchTemplate: [
          launchTemplateSpecification: [
            launchTemplateName: launchTemplateName,
            version: "\$Latest"
          ],
          overrides: overrides
        ]
      ]
    ]
    def sgCache = new DefaultCacheData(serverGroupId, serverGroup, [launchTemplates: [ltCache.id]])

    and:
    cacheView.get(SERVER_GROUPS.ns, serverGroupId) >> sgCache
    cacheView.get(LAUNCH_TEMPLATES.ns, ltKey) >> ltCache
    cacheView.get(IMAGES.ns, imageKey) >> imageCache

    when:
    def actualServerGroup = provider.getServerGroup(account, region, serverGroupName, false)

    then:
    actualServerGroup.image["imageId"] == imageCacheAttr["imageId"]
    actualServerGroup.launchConfig == null
    actualServerGroup.launchTemplate == null
    actualServerGroup.mixedInstancesPolicy.allowedInstanceTypes == expectedAllowedInstanceTypes
    actualServerGroup.mixedInstancesPolicy.instancesDistribution == serverGroup.asg["mixedInstancesPolicy"]["instancesDistribution"]
    actualServerGroup.mixedInstancesPolicy.launchTemplates == expectedInstanceTypeInLtData ? latestVersion : {latestVersion.clone(); latestVersion["launchTemplateData"].remove("instanceType")}
    actualServerGroup.mixedInstancesPolicy.launchTemplateOverridesForInstanceType == overrides

    where:
                        overrides                             || expectedInstanceTypeInLtData | expectedAllowedInstanceTypes
                                            null              ||     "some.type.small"        | ["some.type.small"]
    [[instanceType: "some.type.large", weightedCapacity: 2],
     [instanceType: "some.type.xlarge", weightedCapacity: 4]] ||           null               | ["some.type.large", "some.type.xlarge"]
  }
}
