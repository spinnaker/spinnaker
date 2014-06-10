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
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.CacheService
import com.netflix.spinnaker.oort.model.ClusterProvider
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import rx.schedulers.Schedulers
import rx.util.functions.Func2

@Component
@CompileStatic
class AmazonClusterProvider implements ClusterProvider<AmazonCluster> {
  @Autowired
  CacheService cacheService

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    def keys = cacheService.keys()
    def clusters = (List<AmazonCluster>) keys.findAll { it.startsWith("clusters:") }.collect { cacheService.retrieve(it) }
    getClustersWithServerGroups keys, clusters
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String application) {
    def keys = cacheService.keys()
    def clusters = (List<AmazonCluster>) keys.findAll { it.startsWith("clusters:${application}:") }.collect { (AmazonCluster) cacheService.retrieve(it) }
    getClustersWithServerGroups keys, clusters
  }

  @Override
  Set<AmazonCluster> getClusters(String application, String accountName) {
    def keys = cacheService.keys()
    def clusters = keys.findAll { it.startsWith("clusters:${application}:${accountName}:") }.collect { (AmazonCluster) cacheService.retrieve(it) }
    (Set<AmazonCluster>) getClustersWithServerGroups(keys, clusters)?.get(accountName)
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    def keys = cacheService.keys()
    def cluster = (AmazonCluster) cacheService.retrieve(Keys.getClusterKey(name, application, account))
    if (cluster) {
      def withServerGroups = getClustersWithServerGroups(keys, [cluster])
      cluster = withServerGroups[account]?.getAt(0)
    }
    cluster
  }

  private Map<String, Set<AmazonCluster>> getClustersWithServerGroups(Set<String> keys, List<AmazonCluster> clusters) {
    rx.Observable.from(clusters).flatMap {
      rx.Observable.from(it).observeOn(Schedulers.io()).map { cluster ->
        clusterFiller(keys, cluster as AmazonCluster)
        cluster
      }
    }.reduce([:], { Map results, AmazonCluster cluster ->
      if (!results.containsKey(cluster.accountName)) {
        results[cluster.accountName] = new HashSet<>()
      }
      ((Set)results[cluster.accountName as String]) << cluster
      results
    } as Func2<Map, ? super AmazonCluster, Map>).toBlockingObservable().first()
  }

  void clusterFiller(Set<String> keys, AmazonCluster cluster) {
    def serverGroups = keys.findAll { it.startsWith("serverGroups:${cluster.name}:${cluster.accountName}:") }
    if (!serverGroups) return
    for (loadBalancer in cluster.loadBalancers) {
      def elb = keys.find { it == "loadBalancers:${loadBalancer.region}:${loadBalancer.name}:" }
      if (elb) {
        loadBalancer.elb = cacheService.retrieve(elb)
      }
    }
    cluster.serverGroups = serverGroups.collect {
      AmazonServerGroup serverGroup = (AmazonServerGroup)cacheService.retrieve(it)
      if (serverGroup) {
        def launchConfigKey = Keys.getLaunchConfigKey(serverGroup.launchConfigName as String, serverGroup.region)
        if (keys.contains(launchConfigKey)) {
          LaunchConfiguration launchConfig = (LaunchConfiguration)cacheService.retrieve(launchConfigKey)
          serverGroup.launchConfig = launchConfig
          def imageKey = Keys.getImageKey(launchConfig.imageId, serverGroup.region)
          if (keys.contains(imageKey)) {
            Map<Object, Object> buildInfo = [:]
            def image = (Image) cacheService.retrieve(imageKey)
            if (image) {
              def appVersionTag = image.tags.find { it.key == "appversion" }?.value
              if (appVersionTag) {
                def appVersion = AppVersion.parseName(appVersionTag)
                if (appVersion) {
                  buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>
                  if (appVersion.buildJobName) {
                    buildInfo.jenkins = [:] << [name: appVersion.buildJobName, number: appVersion.buildNumber]
                  }
                  def buildHost = image.tags.find { it.key == "build_host" }?.value
                  if (buildHost) {
                    ((Map)buildInfo.jenkins).host = buildHost
                  }
                }
              }
            }
            serverGroup.buildInfo = buildInfo
            serverGroup.image = image
          }
        }
        def instanceIds = keys.findAll { it.startsWith("serverGroupsInstance:${cluster.name}:${cluster.accountName}:${serverGroup.region}:${serverGroup.name}:") }.collect { it.split(':')[-1] }
        for (instanceId in instanceIds) {
          def ec2Instance = cacheService.retrieve(Keys.getInstanceKey(instanceId, serverGroup.region))
          if (ec2Instance) {
            def modelInstance = new AmazonInstance(instanceId)
            modelInstance.instance = ec2Instance
            serverGroup.instances << modelInstance
          }
        }
      }
      serverGroup
    } as Set
    cluster.serverGroups.removeAll([null])
  }
}
