/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.model.aws

import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.Tag
import com.codahale.metrics.Timer
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Keys.Namespace
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.HealthProvider
import com.netflix.spinnaker.oort.model.HealthState
import spock.lang.Specification
import spock.lang.Unroll

class AmazonClusterProviderSpec extends Specification {

  CacheService cacheService = Mock(CacheService)
  AmazonClusterProvider provider = new AmazonClusterProvider(cacheService: cacheService, healthProviders: [])
  Timer timer = new Timer()

  def setup() {
    AmazonClusterProvider.declaredFields.findAll { it.type == Timer }.each {
      provider.setProperty(it.name, timer)
    }
  }

  void "getting all clusters is keyed on account and fully populated"() {
    setup:
    def objects = getCommonObjects()

    when:
    def clusters = provider.getClusters()

    then:
    clusters[objects.accountName][0].is(objects.cluster)
    1 * cacheService.keysByType(Namespace.CLUSTERS) >> objects.clusterKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUPS) >> objects.serverGroupKeys
    1 * cacheService.keysByType(Namespace.LOAD_BALANCERS) >> objects.loadBalancerKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUP_INSTANCES) >> objects.serverGroupInstanceKeys
    1 * cacheService.retrieve(objects.clusterKey, _) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey, _) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey, _) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey, _) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey, _) >> objects.instance
  }

  void "getting cluster for a specific application is keyed on account and fully populated"() {
    setup:
    def objects = getCommonObjects()

    when:
    def clusters = provider.getClusters(objects.appName)

    then:
    clusters[objects.accountName][0].is(objects.cluster)
    1 * cacheService.keysByType(Namespace.CLUSTERS) >> objects.clusterKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUPS) >> objects.serverGroupKeys
    1 * cacheService.keysByType(Namespace.LOAD_BALANCERS) >> objects.loadBalancerKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUP_INSTANCES) >> objects.serverGroupInstanceKeys
    1 * cacheService.retrieve(objects.clusterKey, _) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey, _) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey, _) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey, _) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey, _) >> objects.instance

    when:
    clusters = provider.getClusters("oort")

    then:
    !clusters
    1 * cacheService.keysByType(Namespace.CLUSTERS) >> objects.clusterKeys
  }

  void "getting cluster for a specific application and account returns cluster set fully populated"() {
    setup:
    def objects = getCommonObjects()

    when:
    def clusters = provider.getClusters(objects.appName, objects.accountName)

    then:
    clusters[0].is(objects.cluster)
    1 * cacheService.keysByType(Namespace.CLUSTERS) >> objects.clusterKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUPS) >> objects.serverGroupKeys
    1 * cacheService.keysByType(Namespace.LOAD_BALANCERS) >> objects.loadBalancerKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUP_INSTANCES) >> objects.serverGroupInstanceKeys
    1 * cacheService.retrieve(objects.clusterKey, _) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey, _) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey, _) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey, _) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey, _) >> objects.instance
  }

  void "getting a specific cluster for a specific application in a specific account returns the fully populated cluster"() {
    setup:
    def objects = getCommonObjects()

    when:
    def cluster = provider.getCluster(objects.appName, objects.accountName, objects.clusterName)

    then:
    cluster.is(objects.cluster)
    1 * cacheService.keysByType(Namespace.SERVER_GROUPS) >> objects.serverGroupKeys
    1 * cacheService.keysByType(Namespace.LOAD_BALANCERS) >> objects.loadBalancerKeys
    1 * cacheService.keysByType(Namespace.SERVER_GROUP_INSTANCES) >> objects.serverGroupInstanceKeys
    1 * cacheService.retrieve(objects.clusterKey, _) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey, _) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey, _) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey, _) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey, _) >> objects.instance
  }

  void "cluster filler should aggregate all known data about a cluster"() {
    setup:
    def objects = getCommonObjects()

    when:
    provider.clusterFiller(objects.serverGroupKeys, objects.loadBalancerKeys, objects.serverGroupInstanceKeys, objects.cluster)

    then:
    1 * cacheService.retrieve(objects.serverGroupKey, _) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey, _) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey, _) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey, _) >> objects.instance
    objects.cluster.serverGroups
    objects.cluster.serverGroups[0].name == objects.serverGroupName
    objects.cluster.serverGroups[0].buildInfo.package_name == "kato"
    objects.cluster.serverGroups[0].buildInfo.version == "1.0"
    objects.cluster.serverGroups[0].buildInfo.commit == "01f12f7"
    objects.cluster.serverGroups[0].buildInfo.jenkins.host == "http://builds.netflix.com/"
    objects.cluster.serverGroups[0].image == objects.image
    objects.cluster.serverGroups[0].instances[0].name == objects.instanceId
  }

  void "should populate multiple instances"() {
    setup:
    def objects = getCommonObjects()
    def secondInstanceId = objects.instanceId + '-2'
    def secondInstanceKey = Keys.getInstanceKey(secondInstanceId, objects.region)
    def secondServerGroupInstanceKey = Keys.getServerGroupInstanceKey(objects.serverGroupName, secondInstanceId, objects.accountName, objects.region)
    def secondInstance = new Instance().withInstanceId(secondInstanceId)
    objects.serverGroupInstanceKeys << secondServerGroupInstanceKey

    when:
    provider.clusterFiller(objects.serverGroupKeys, objects.loadBalancerKeys, objects.serverGroupInstanceKeys, objects.cluster)

    then:
    1 * cacheService.retrieve(objects.serverGroupKey, _) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey, _) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey, _) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey, _) >> objects.instance
    1 * cacheService.retrieve(secondInstanceKey, _) >> secondInstance
    objects.cluster.serverGroups[0].instances.size() == 2
  }

  @Unroll
  void "should populate instances with health"() {
    def mockHealthProvider = Mock(HealthProvider)
    provider.healthProviders = [mockHealthProvider]

    when:
    def result = provider.constructInstance(new Instance(instanceId: "123"), null, null)

    then:
    result.isHealthy() == isHealthy

    and:
    1 * mockHealthProvider.getHealth(null, null, "123") >> new AwsInstanceHealth(state: healthState)
    0 * _

    where:
    healthState         | isHealthy
    HealthState.Up      | true
    HealthState.Down    | false
    HealthState.Unknown | false
  }

  @Unroll
  void "should populate instances with health based on multiple providers"() {
    def mockHealthProvider1 = Mock(HealthProvider)
    def mockHealthProvider2 = Mock(HealthProvider)
    provider.healthProviders = [mockHealthProvider1, mockHealthProvider2]

    when:
    def result = provider.constructInstance(new Instance(instanceId: "123"), null, null)

    then:
    result.isHealthy() == isHealthy

    and:
    1 * mockHealthProvider1.getHealth(null, null, "123") >> new AwsInstanceHealth(state: healthStates[0])
    1 * mockHealthProvider2.getHealth(null, null, "123") >> new AwsInstanceHealth(state: healthStates[1])
    0 * _

    where:
    healthStates                            | isHealthy
    HealthState.with { [Up, Up] }           | true
    HealthState.with { [Up, Down] }         | false
    HealthState.with { [Up, Unknown] }      | true
    HealthState.with { [Down, Up] }         | false
    HealthState.with { [Down, Down] }       | false
    HealthState.with { [Down, Unknown] }    | false
    HealthState.with { [Unknown, Up] }      | true
    HealthState.with { [Unknown, Down] }    | false
    HealthState.with { [Unknown, Unknown] } | false
  }

  @Unroll
  void "should consider instances as unhealthy without health providers"() {
    provider.healthProviders = []

    when:
    def result = provider.constructInstance(new Instance(instanceId: "123"), null, null)

    then:
    !result.isHealthy()

    and:
    0 * _
  }

  def getCommonObjects() {
    def appName = "kato"
    def clusterName = "kato-main"
    def serverGroupName = "kato-main-v000"
    def account = "test"
    def type = "aws"
    def region = "us-east-1"
    def launchConfigName = "kato-main-v000-123456"
    def imageId = "ami-123456"
    def instanceId = "i-123456"
    def serverGroupKey = Keys.getServerGroupKey(serverGroupName, account, region)
    def serverGroup = new AmazonServerGroup(serverGroupName, type, region)
    serverGroup.launchConfigName = launchConfigName
    def instanceServerGroupKey = Keys.getServerGroupInstanceKey(serverGroupName, instanceId, account, region)
    def instanceKey = Keys.getInstanceKey(instanceId, region)
    def instance = new Instance().withInstanceId(instanceId)
    def imageKey = Keys.getImageKey(imageId, region)
    def image = new Image().withTags(new Tag("appversion", "kato-1.0-h34.01f12f7/kato-nflx/34"), new Tag("build_host", "http://builds.netflix.com/"))
    def launchConfigKey = Keys.getLaunchConfigKey(launchConfigName, region)
    def launchConfig = new LaunchConfiguration().withLaunchConfigurationName(launchConfigName).withImageId(imageId)
    def clusterKey = Keys.getClusterKey(clusterName, appName, account)
    def cluster = new AmazonCluster(name: clusterName, accountName: account)
    def keys = [serverGroupKey, instanceKey, imageKey, launchConfigKey, instanceServerGroupKey, clusterKey] as Set
    def serverGroupKeys = [serverGroupKey] as Set
    def loadBalancerKeys = [] as Set
    def serverGroupInstanceKeys = [instanceServerGroupKey] as Set
    def clusterKeys = [clusterKey] as Set

    [serverGroupKey: serverGroupKey, serverGroup: serverGroup, launchConfigKey: launchConfigKey, launchConfig: launchConfig,
      imageKey: imageKey, image: image, instanceKey: instanceKey, instance: instance, region: region, keys: keys, cluster: cluster,
      serverGroupName: serverGroupName, instanceId: instanceId, appName: appName, clusterName: clusterName,
      accountName: account, clusterKey: clusterKey, clusterKeys: clusterKeys, serverGroupKeys: serverGroupKeys,
      loadBalancerKeys: loadBalancerKeys, serverGroupInstanceKeys: serverGroupInstanceKeys]

  }

}
