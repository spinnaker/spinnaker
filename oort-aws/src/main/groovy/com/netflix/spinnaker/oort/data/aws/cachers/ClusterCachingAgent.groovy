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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.Keys.Namespace
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract

@CompileStatic
class ClusterCachingAgent extends AbstractInfrastructureCachingAgent {
  ClusterCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownAsgs = [:]

  void load() {
    def autoScaling = amazonClientProvider.getAutoScaling(account.credentials, region)
    def asgs = autoScaling.describeAutoScalingGroups()
    def allAsgs = asgs.autoScalingGroups.collectEntries { AutoScalingGroup asg -> [(asg.autoScalingGroupName): asg] }
    def asgsThisRun = (Map<String, Integer>)allAsgs.collectEntries { asgName, asg -> [(asgName): asg.hashCode()] }
    Map<String, Integer> changedAsgNames = specialSubtract(asgsThisRun, lastKnownAsgs)
    Set<String> missingAsgNames = new HashSet<String>(lastKnownAsgs.keySet())
    missingAsgNames.removeAll(asgsThisRun.keySet())

    if (changedAsgNames) {
      log.info "$cachePrefix - Loading ${changedAsgNames.size()} new or changed server groups"
      for (String asgName in changedAsgNames.keySet()) {
        AutoScalingGroup asg = (AutoScalingGroup) allAsgs[asgName]
        def names = Names.parseName(asg.autoScalingGroupName)
        loadApp(names)
        loadCluster(account, asg, names, region)
        loadServerGroups(account, asg, names, region)
      }
    }
    if (missingAsgNames) {
      log.info "$cachePrefix - Removing ${missingAsgNames.size()} server groups"
      for (String asgName in missingAsgNames) {
        removeServerGroup(account, asgName, region)
      }
    }
    if (!changedAsgNames && !missingAsgNames) {
      log.info "$cachePrefix - Nothing new to process"
    }
    lastKnownAsgs = asgsThisRun
  }

  void loadApp(Names names) {
    def appName = names.app.toLowerCase()
    if (!appName) {
      return
    }
    def application = cacheService.retrieve(Keys.getApplicationKey(appName), AmazonApplication) ?: new AmazonApplication(name: appName)
    cacheService.put(Keys.getApplicationKey(application.name), application)
  }

  void loadCluster(AmazonNamedAccount account, AutoScalingGroup asg, Names names, String region) {
    def appName = names.app.toLowerCase()
    def cluster = cacheService.retrieve(Keys.getClusterKey(names.cluster, appName, account.name), AmazonCluster)
    if (!cluster) {
      cluster = new AmazonCluster(name: names.cluster, accountName: account.name)
    }
    cluster.loadBalancers.addAll(asg.loadBalancerNames.collect { new AmazonLoadBalancer(it, region) } as Set)
    cacheService.put(Keys.getClusterKey(names.cluster, appName, account.name), cluster)

    for (loadBalancerName in asg.loadBalancerNames) {
      cacheService.put(Keys.getLoadBalancerServerGroupKey(loadBalancerName, account.name, asg.autoScalingGroupName, region), [:])
      cacheService.put(Keys.getApplicationLoadBalancerKey(appName, loadBalancerName, account.name, region), [:])
    }
  }

  void loadServerGroups(AmazonNamedAccount account, AutoScalingGroup asg, Names names, String region) {
    def serverGroup = new AmazonServerGroup(names.group, "aws", region)
    serverGroup.launchConfigName = asg.launchConfigurationName
    serverGroup.asg = asg
    cacheService.put(Keys.getServerGroupKey(names.group, account.name, region), serverGroup)
  }

  void removeServerGroup(AmazonNamedAccount account, String asgName, String region) {
    // Check if we need to clean up this cluster
    def names = Names.parseName(asgName)
    def appName = names.app.toLowerCase()
    def clusterServerGroups = cacheService.keysByType(Namespace.SERVER_GROUPS).find { it.startsWith "${Namespace.SERVER_GROUPS}:${names.cluster}:${account.name}:" }
    if (!clusterServerGroups) {
      cacheService.free(Keys.getClusterKey(names.cluster, names.app.toLowerCase(), account.name))
    }
    def serverGroupKey = Keys.getServerGroupKey(asgName, account.name, region)
    def retrieved = cacheService.retrieve(serverGroupKey, AmazonServerGroup)
    if (retrieved && retrieved.asg) {
      def loadBalancerServerGroups = cacheService.keysByType(Namespace.LOAD_BALANCER_SERVER_GROUPS)
      AutoScalingGroup asg = (AutoScalingGroup) retrieved.asg
      asg.loadBalancerNames.each { String loadBalancerName ->
        def loadBalancerMatchesForApplication = loadBalancerServerGroups.count { it.startsWith "${Namespace.LOAD_BALANCER_SERVER_GROUPS}:${loadBalancerName}:${appName}"}
        if (loadBalancerMatchesForApplication == 1) {
          cacheService.free(Keys.getApplicationLoadBalancerKey(appName, loadBalancerName, account.name, region))
        }
        cacheService.free(Keys.getLoadBalancerServerGroupKey(loadBalancerName, account.name, asgName, region))
      }
    }
    cacheService.free(serverGroupKey)
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:clu]"
  }
}
