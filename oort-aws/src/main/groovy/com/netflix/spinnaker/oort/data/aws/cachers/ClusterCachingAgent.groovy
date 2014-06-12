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
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import reactor.event.Event

import static com.netflix.spinnaker.oort.ext.MapExtensions.specialSubtract
import static reactor.event.selector.Selectors.object

@CompileStatic
class ClusterCachingAgent extends AbstractInfrastructureCachingAgent {
  ClusterCachingAgent(AmazonNamedAccount account, String region) {
    super(account, region)
  }

  private Map<String, Integer> lastKnownAsgs = [:]

  void load() {
    reactor.on(object("newAsg"), this.&loadApp)
    reactor.on(object("newAsg"), this.&loadCluster)
    reactor.on(object("newAsg"), this.&loadServerGroups)
    reactor.on(object("missingAsg"), this.&removeServerGroup)

    def autoScaling = amazonClientProvider.getAutoScaling(account.credentials, region)
    def asgs = autoScaling.describeAutoScalingGroups()
    def allAsgs = asgs.autoScalingGroups.collectEntries { AutoScalingGroup asg -> [(asg.autoScalingGroupName): asg] }
    def asgsThisRun = (Map<String, Integer>)allAsgs.collectEntries { asgName, asg -> [(asgName): asg.hashCode()] }
    Map<String, Integer> changedAsgNames = specialSubtract(asgsThisRun, lastKnownAsgs)
    Set<String> missingAsgNames = lastKnownAsgs.keySet() - asgsThisRun.keySet()

    if (changedAsgNames) {
      log.info "$cachePrefix - Loading ${changedAsgNames.size()} new or changed server groups"
      for (String asgName in changedAsgNames.keySet()) {
        AutoScalingGroup asg = (AutoScalingGroup) allAsgs[asgName]
        def names = Names.parseName(asg.autoScalingGroupName)
        reactor.notify("newAsg", Event.wrap(new FriggaWrappedAutoScalingGroup(account, asg, names, region)))
      }
    }
    if (missingAsgNames) {
      log.info "$cachePrefix - Removing ${missingAsgNames.size()} server groups"
      for (String asgName in missingAsgNames) {
        reactor.notify("missingAsg", Event.wrap(new RemoveServerGroupNotification(account, asgName, region)))
      }
    }
    if (!changedAsgNames && !missingAsgNames) {
      log.info "$cachePrefix - Nothing new to process"
    }
    lastKnownAsgs = asgsThisRun
  }

  void loadApp(Event<FriggaWrappedAutoScalingGroup> event) {
    def names = event.data.names

    def appName = names.app.toLowerCase()
    if (!appName) {
      return
    }
    def application = (AmazonApplication) cacheService.retrieve(Keys.getApplicationKey(appName)) ?: new AmazonApplication(name: appName)
    cacheService.put(Keys.getApplicationKey(application.name), application)
  }

  void loadCluster(Event<FriggaWrappedAutoScalingGroup> event) {
    def account = event.data.account
    def asg = event.data.autoScalingGroup
    def names = event.data.names
    def region = event.data.region

    def cluster = (AmazonCluster) cacheService.retrieve(Keys.getClusterKey(names.cluster, names.app, account.name))
    if (!cluster) {
      cluster = new AmazonCluster(name: names.cluster, accountName: account.name)
    }
    cluster.loadBalancers = asg.loadBalancerNames.collect { new AmazonLoadBalancer(it, region) } as Set
    cacheService.put(Keys.getClusterKey(names.cluster, names.app, account.name), cluster)
  }

  void loadServerGroups(Event<FriggaWrappedAutoScalingGroup> event) {
    def account = event.data.account
    def asg = event.data.autoScalingGroup
    def names = event.data.names
    def region = event.data.region

    def serverGroup = new AmazonServerGroup(names.group, "aws", region)
    serverGroup.launchConfigName = asg.launchConfigurationName
    serverGroup.asg = asg
    cacheService.put(Keys.getServerGroupKey(names.group, account.name, region), serverGroup)
  }

  void removeServerGroup(Event<RemoveServerGroupNotification> event) {
    def account = event.data.account.name
    def asgName = event.data.asgName
    def region = event.data.region

    cacheService.free(Keys.getServerGroupKey(asgName, account, region))
  }

  @Canonical
  static class RemoveServerGroupNotification {
    AmazonNamedAccount account
    String asgName
    String region
  }

  @Canonical
  static class FriggaWrappedAutoScalingGroup {
    AmazonNamedAccount account
    AutoScalingGroup autoScalingGroup
    Names names
    String region
  }

  private String getCachePrefix() {
    "[caching:$region:${account.name}:clu]"
  }
}
