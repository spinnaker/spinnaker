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
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.codahale.metrics.Timer
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Keys.Namespace
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthProvider
import com.ryantenney.metrics.annotation.Metric
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.functions.Func1
import rx.functions.Func2
import rx.schedulers.Schedulers


@Component
@CompileStatic
class AmazonClusterProvider implements ClusterProvider<AmazonCluster> {
  @Autowired
  CacheService cacheService

  @Autowired
  List<HealthProvider> healthProviders

  @Metric
  Timer allClusters

  @Metric
  Timer clustersByApplication

  @Metric
  Timer clustersByApplicationAndAccount

  @Metric
  Timer clustersById

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    allClusters.time {
      def keys = cacheService.keysByType(Namespace.CLUSTERS)
      def clusters = (List<AmazonCluster>) keys.collect { cacheService.retrieve(it, AmazonCluster) }
      clusters ? getClustersWithServerGroups(clusters) : [:]
    }
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String application) {
    clustersByApplication.time {
      def keys = cacheService.keysByType(Namespace.CLUSTERS)
      def clusters = (List<AmazonCluster>) keys.findAll { it.startsWith("${Namespace.CLUSTERS}:${application}:") }.collect { cacheService.retrieve(it, AmazonCluster) }
      clusters ? getClustersWithServerGroups(clusters) : [:]
    }
  }

  @Override
  Set<AmazonCluster> getClusters(String application, String accountName) {
    clustersByApplicationAndAccount.time {
      def keys = cacheService.keysByType(Namespace.CLUSTERS)
      def clusters = keys.findAll { it.startsWith("${Namespace.CLUSTERS}:${application}:${accountName}:") }.collect { cacheService.retrieve(it, AmazonCluster) }
      (Set<AmazonCluster>) (clusters ? getClustersWithServerGroups(clusters)?.get(accountName) : [])
    }
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    clustersById.time {
      def cluster = cacheService.retrieve(Keys.getClusterKey(name, application, account), AmazonCluster)
      if (cluster) {
        def withServerGroups = getClustersWithServerGroups([cluster])
        cluster = withServerGroups[account]?.getAt(0)
      }
      cluster
    }
  }

  private Map<String, Set<AmazonCluster>> getClustersWithServerGroups(List<AmazonCluster> clusters) {
    final Set<String> serverGroupKeys = cacheService.keysByType(Namespace.SERVER_GROUPS)
    final Set<String> loadBalancerKeys = cacheService.keysByType(Namespace.LOAD_BALANCERS)
    final Set<String> serverGroupInstanceKeys = cacheService.keysByType(Namespace.SERVER_GROUP_INSTANCE)
    rx.Observable.from(clusters).flatMap {
      rx.Observable.from(it).observeOn(Schedulers.computation()).map { cluster ->
        clusterFiller(serverGroupKeys, loadBalancerKeys, serverGroupInstanceKeys, cluster as AmazonCluster)
        cluster
      }
    }.reduce([:], { Map results, AmazonCluster cluster ->
      if (!results.containsKey(cluster.accountName)) {
        results[cluster.accountName] = new HashSet<>()
      }
      ((Set)results[cluster.accountName as String]) << cluster
      results
    } as Func2<Map, ? super AmazonCluster, Map>).toBlocking().first()
  }

  void clusterFiller(Set<String> serverGroupKeys, Set<String> loadBalancerKeys, Set<String> serverGroupInstanceKeys, AmazonCluster cluster) {
    def serverGroups = serverGroupKeys.findAll { it.startsWith("${Namespace.SERVER_GROUPS}:${cluster.name}:${cluster.accountName}:") }
    if (!serverGroups) return
    for (loadBalancer in cluster.loadBalancers) {
      def elb = loadBalancerKeys.find { it == "${Namespace.LOAD_BALANCERS}:${loadBalancer.region}:${loadBalancer.name}:" }
      if (elb) {
        loadBalancer.elb = cacheService.retrieve(elb, LoadBalancerDescription)
      }
    }

    cluster.serverGroups = (Set)rx.Observable.from(serverGroups)
      .map({ String name -> cacheService.retrieve(name, AmazonServerGroup) })
      .filter({ it != null })
      .map(attributePopulator as Func1)
      .map(instancePopulator.curry(serverGroupInstanceKeys, cluster) as Func1)
      .reduce(new HashSet<AmazonServerGroup>(), { Set<AmazonServerGroup> objs, AmazonServerGroup obj -> objs << obj })
      .doOnError({ Throwable t -> t.printStackTrace() })
      .toBlocking()
      .firstOrDefault(new HashSet<AmazonServerGroup>()) as Set
  }

  final Closure attributePopulator = { AmazonServerGroup serverGroup ->
    def serverAttributes = new ServerGroupAttributeBuilder(serverGroup)
    serverGroup.launchConfig = serverAttributes.launchConfig
    serverGroup.image = serverAttributes.image
    serverGroup.buildInfo = serverAttributes.buildInfo
    serverGroup
  }

  final Closure instancePopulator = { Set<String> keys, AmazonCluster cluster, AmazonServerGroup serverGroup ->
    def instanceIds = keys.findAll { it.startsWith("${Namespace.SERVER_GROUP_INSTANCE}:${cluster.name}:${cluster.accountName}:${serverGroup.region}:${serverGroup.name}:") }.collect { it.split(':')[-1] }
    for (instanceId in instanceIds) {
      def ec2Instance = cacheService.retrieve(Keys.getInstanceKey(instanceId, serverGroup.region), Instance)
      if (ec2Instance) {
        serverGroup.instances << constructInstance(ec2Instance, serverGroup, cluster.accountName)
      }
    }
    serverGroup
  }

  AmazonInstance constructInstance(Instance ec2Instance, AmazonServerGroup serverGroup, String accountName) {
    def modelInstance = new AmazonInstance(ec2Instance.instanceId)
    modelInstance.instance = ec2Instance
    List<Health> healths = healthProviders.collect {
      it.getHealth(accountName, serverGroup, ec2Instance.instanceId)
    }
    modelInstance.health = healths
    modelInstance.isHealthy = healths.find { it?.isHealthy() } as boolean
    modelInstance
  }

  private class ServerGroupAttributeBuilder {
    AmazonServerGroup serverGroup

    ServerGroupAttributeBuilder(AmazonServerGroup serverGroup) {
      this.serverGroup = serverGroup
    }

    private LaunchConfiguration launchConfiguration
    private Image image
    private Map buildInfo

    LaunchConfiguration getLaunchConfig() {
      if (!this.launchConfiguration) {
        def launchConfigKey = Keys.getLaunchConfigKey(serverGroup.launchConfigName as String, serverGroup.region)
        this.launchConfiguration = cacheService.retrieve(launchConfigKey, LaunchConfiguration)
      }
      this.launchConfiguration
    }

    Image getImage() {
      if (!this.image) {
        def launchConfig = getLaunchConfig()
        if (!launchConfig) return null
        def imageKey = Keys.getImageKey(launchConfig.imageId, serverGroup.region)
        this.image = cacheService.retrieve(imageKey, Image)
      }
      this.image
    }

    Map getBuildInfo() {
      if (!this.buildInfo) {
        def appVersionTag = image?.tags?.find { it.key == "appversion" }?.value
        if (appVersionTag) {
          def appVersion = AppVersion.parseName(appVersionTag)
          if (appVersion) {
            buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>
            if (appVersion.buildJobName) {
              buildInfo.jenkins = [:] << [name: appVersion.buildJobName, number: appVersion.buildNumber]
            }
            def buildHost = image.tags.find { it.key == "build_host" }?.value
            if (buildHost) {
              ((Map) buildInfo.jenkins).host = buildHost
            }
          }
        }
      }
      buildInfo
    }
  }
}
