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

package com.netflix.apinnaker.oort.model.aws

import com.codahale.metrics.Timer
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.ec2.model.Tag
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonClusterProvider
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import spock.lang.Shared
import spock.lang.Specification

class AmazonClusterProviderSpec extends Specification {

  @Shared
  AmazonClusterProvider provider

  @Shared
  CacheService cacheService

  Timer timer = new Timer()

  def setup() {
    provider = new AmazonClusterProvider(healthProviders: [])
    cacheService = Mock(CacheService)
    provider.cacheService = cacheService
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
    1 * cacheService.keys() >> objects.keys
    1 * cacheService.retrieve(objects.clusterKey) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey) >> objects.instance
  }

  void "getting cluster for a specific application is keyed on account and fully populated"() {
    setup:
    def objects = getCommonObjects()

    when:
    def clusters = provider.getClusters(objects.appName)

    then:
    clusters[objects.accountName][0].is(objects.cluster)
    1 * cacheService.keys() >> objects.keys
    1 * cacheService.retrieve(objects.clusterKey) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey) >> objects.instance

    when:
    clusters = provider.getClusters("oort")

    then:
    !clusters
    1 * cacheService.keys() >> objects.keys
  }

  void "getting cluster for a specific application and account returns cluster set fully populated"() {
    setup:
    def objects = getCommonObjects()

    when:
    def clusters = provider.getClusters(objects.appName, objects.accountName)

    then:
    clusters[0].is(objects.cluster)
    1 * cacheService.keys() >> objects.keys
    1 * cacheService.retrieve(objects.clusterKey) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey) >> objects.instance
  }

  void "getting a specific cluster for a specific application in a specific account returns the fully populated cluster"() {
    setup:
    def objects = getCommonObjects()

    when:
    def cluster = provider.getCluster(objects.appName, objects.accountName, objects.clusterName)

    then:
    cluster.is(objects.cluster)
    1 * cacheService.keys() >> objects.keys
    1 * cacheService.retrieve(objects.clusterKey) >> objects.cluster
    1 * cacheService.retrieve(objects.serverGroupKey) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey) >> objects.instance
  }

  void "cluster filler should aggreagte all known data about a cluster"() {
    setup:
    def objects = getCommonObjects()

    when:
    provider.clusterFiller(objects.keys, objects.cluster)

    then:
    1 * cacheService.retrieve(objects.serverGroupKey) >> objects.serverGroup
    1 * cacheService.retrieve(objects.launchConfigKey) >> objects.launchConfig
    1 * cacheService.retrieve(objects.imageKey) >> objects.image
    1 * cacheService.retrieve(objects.instanceKey) >> objects.instance
    objects.cluster.serverGroups
    objects.cluster.serverGroups[0].name == objects.serverGroupName
    objects.cluster.serverGroups[0].buildInfo.package_name == "kato"
    objects.cluster.serverGroups[0].buildInfo.version == "1.0"
    objects.cluster.serverGroups[0].buildInfo.commit == "01f12f7"
    objects.cluster.serverGroups[0].buildInfo.jenkins.host == "http://builds.netflix.com/"
    objects.cluster.serverGroups[0].image == objects.image
    objects.cluster.serverGroups[0].instances[0].name == objects.instanceId
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
    [serverGroupKey: serverGroupKey, serverGroup: serverGroup, launchConfigKey: launchConfigKey, launchConfig: launchConfig,
      imageKey: imageKey, image: image, instanceKey: instanceKey, instance: instance, keys: keys, cluster: cluster,
      serverGroupName: serverGroupName, instanceId: instanceId, appName: appName, clusterName: clusterName,
      accountName: account, clusterKey: clusterKey]
  }

}
