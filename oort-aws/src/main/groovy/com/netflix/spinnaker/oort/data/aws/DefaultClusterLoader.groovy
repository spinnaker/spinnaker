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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.Image
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.frigga.Names
import com.netflix.spinnaker.oort.config.OortDefaults
import com.netflix.spinnaker.oort.model.aws.AmazonCluster
import com.netflix.spinnaker.oort.model.aws.AmazonLoadBalancer
import com.netflix.spinnaker.oort.model.aws.AmazonServerGroup
import com.netflix.spinnaker.oort.security.NamedAccountProvider
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import groovy.transform.CompileStatic
import org.apache.directmemory.cache.CacheService
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
@CompileStatic
class DefaultClusterLoader implements ApplicationListener<AmazonDataLoadEvent>, ClusterLoader {
  private static final Logger log = Logger.getLogger(this)
  private final ExecutorService clusterLoaderExecutor = Executors.newFixedThreadPool(200)
  final Map<String, Map<String, Image>> imageCache = [:]
  final Map<String, Map<String, LaunchConfiguration>> launchConfigCache = [:]
  final Map<String, Map<String, LoadBalancerDescription>> loadBalancerCache = [:]

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  NamedAccountProvider namedAccountProvider

  @Autowired
  CacheService<String, AmazonCluster> clusterCacheService

  @Autowired
  ExecutorService taskExecutor

  @Autowired
  OortDefaults oortDefaults

  @Async("taskExecutor")
  @Scheduled(fixedRate = 30000l)
  void loadImages() {
    invokeMultiAccountMultiRegionClosure this.loadImagesCallable
  }

  @Async("taskExecutor")
  @Scheduled(fixedRate = 30000l)
  void loadLaunchConfigs() {
    invokeMultiAccountMultiRegionClosure this.loadLaunchConfigsCallable
  }

  @Async("taskExecutor")
  @Scheduled(fixedRate = 30000l)
  void loadLoadBalancers() {
    invokeMultiAccountMultiRegionClosure this.loadLoadBalancersCallable
  }

  void invokeMultiAccountMultiRegionClosure(Closure closure) {
    def callables = []
    for (account in accounts) {
      for (region in account.regions) {
        callables << closure.curry(account, region)
      }
    }
    taskExecutor.invokeAll(callables)
  }

  private Collection<AmazonNamedAccount> getAccounts() {
    (Collection<AmazonNamedAccount>)namedAccountProvider.accountNames.collectMany({ String name ->
      def namedAccount = namedAccountProvider.get(name)
      (namedAccount.type == AmazonCredentials) ? [namedAccount] : []
    } as Closure<Collection<AmazonNamedAccount>>)
  }

  final Closure loadImagesCallable = { AmazonNamedAccount account, String region ->
    log.info "Caching Amazon Images in $region for $account.name..."
    def ec2 = amazonClientProvider.getAmazonEC2(account.credentials, region)
    def images = ec2.describeImages().images
    if (!imageCache.containsKey(region)) {
      imageCache[region] = new HashMap<>()
    }
    def cache = imageCache[region]
    for (image in images) {
      cache[image.imageId] = image
    }
    log.info "Finished caching Amazon Images in $region for $account.name."
  }

  final Closure loadLaunchConfigsCallable = { AmazonNamedAccount account, String region ->
    log.info "Caching Amazon Launch Configurations in $region..."
    if (!launchConfigCache.containsKey(region)) {
      launchConfigCache[region] = new HashMap<>()
    }
    def cache = launchConfigCache[region]
    def autoScaling = amazonClientProvider.getAutoScaling(account.credentials, region)
    def result = autoScaling.describeLaunchConfigurations()
    List<LaunchConfiguration> launchConfigs = []
    while (true) {
      launchConfigs.addAll result.launchConfigurations
      if (result.nextToken) {
        result = autoScaling.describeLaunchConfigurations(new DescribeLaunchConfigurationsRequest().withNextToken(result.nextToken))
      } else {
        break
      }
    }
    for (launchConfig in launchConfigs) {
      cache[launchConfig.launchConfigurationName] = launchConfig
    }
    log.info "Finsihed caching Amazon Launch Configurations in $region for $account.name."
  }

  final Closure loadLoadBalancersCallable = { AmazonNamedAccount account, String region ->
    log.info "Caching Amazon Load Balancers in $region for $account.name..."
    if (!loadBalancerCache.containsKey(region)) {
      loadBalancerCache[region] = new HashMap<>()
    }
    def cache = loadBalancerCache[region]
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(account.credentials, region)
    def result = loadBalancing.describeLoadBalancers()
    List<LoadBalancerDescription> loadBalancers = []
    while (true) {
      loadBalancers.addAll result.loadBalancerDescriptions
      if (result.nextMarker) {
        result = loadBalancing.describeLoadBalancers(new DescribeLoadBalancersRequest().withMarker(result.nextMarker))
      } else {
        break
      }
    }
    for (loadBalancer in loadBalancers) {
      cache[loadBalancer.loadBalancerName] = loadBalancer
    }
    log.info "Finished caching Amazon Load Balancers in $region for $account.name."
  }

  @Override
  void onApplicationEvent(AmazonDataLoadEvent event) {
    def loader = loader.curry(event)
    clusterLoaderExecutor.submit(loader)
  }

  private final Closure loader = { AmazonDataLoadEvent event ->
    def asg = event.autoScalingGroup
    def names = Names.parseName(asg.autoScalingGroupName)

    def key = "${event.amazonNamedAccount.name}:${names.cluster}".toString()
    def cluster = clusterCacheService.retrieve(key) ?: new AmazonCluster(name: names.cluster, accountName: event.amazonNamedAccount.name)

    def serverGroup = cluster.serverGroups.find { it.name == names.group && it.type == "Amazon" && it.region == event.region }
    if (!serverGroup) {
      serverGroup = new AmazonServerGroup(names.group, "Amazon", event.region)
      cluster.serverGroups << serverGroup
    }
    if (launchConfigCache.containsKey(event.region) && launchConfigCache[event.region].containsKey(asg.launchConfigurationName)) {
      LaunchConfiguration launchConfiguration = launchConfigCache[event.region][asg.launchConfigurationName]
      serverGroup.launchConfiguration = launchConfiguration
      if (imageCache.containsKey(event.region) && imageCache[event.region].containsKey(launchConfiguration.imageId)) {
        Image image = imageCache[event.region][launchConfiguration.imageId]
        serverGroup.image = image
      }
    }
    if (asg.loadBalancerNames) {
      for (loadBalancerName in asg.loadBalancerNames) {
        def loadBalancer = cluster.loadBalancers.find { it.name == loadBalancerName }
        if (!loadBalancer) {
          loadBalancer = new AmazonLoadBalancer(loadBalancerName)
          cluster.loadBalancers << loadBalancer
        }
        if (loadBalancerCache.containsKey(event.region) && loadBalancerCache[event.region].containsKey(loadBalancerName)) {
          loadBalancer.serverGroups << asg.autoScalingGroupName
        }
      }
    }
    log.info "Saving ${event.region} ${cluster.accountName} ${cluster.name} ${asg.autoScalingGroupName}"
    clusterCacheService.put key, cluster, 300000
  }

  protected void shutdownAndWait(int seconds) {
    clusterLoaderExecutor.shutdown()
    clusterLoaderExecutor.awaitTermination(seconds, TimeUnit.SECONDS)
  }
}
