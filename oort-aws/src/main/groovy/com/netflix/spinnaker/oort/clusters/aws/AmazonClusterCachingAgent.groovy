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

package com.netflix.spinnaker.oort.clusters.aws

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest
import com.amazonaws.services.autoscaling.model.LaunchConfiguration
import com.amazonaws.services.ec2.model.Image
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.oort.clusters.Cluster
import com.netflix.spinnaker.oort.security.aws.AmazonAccountObject
import com.netflix.spinnaker.oort.spring.ApplicationContextHolder
import groovy.transform.InheritConstructors
import org.apache.commons.codec.binary.Base64
import org.springframework.util.StopWatch

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Logger

class AmazonClusterCachingAgent implements Callable<Map> {
  private static final String SERVER_GROUP_TYPE = "Amazon"
  private static final Logger log = Logger.getLogger(this.class.simpleName)

  private final AmazonAccountObject credentials
  private final AmazonClientProvider amazonClientProvider

  private static final ExecutorService executorService = Executors.newFixedThreadPool(20)

  AmazonClusterCachingAgent(AmazonClientProvider amazonClientProvider, AmazonAccountObject credentials) {
    this.amazonClientProvider = amazonClientProvider
    this.credentials = credentials
  }

  String getName() {
    credentials.name
  }

  Map call() {
    def cache = new ConcurrentHashMap()
    def regions = credentials.regions

    def stopwatch = new StopWatch()
    stopwatch.start()
    def callables = regions.collectMany { String region ->
      [asgCallable, launchConfigCallable, imageCallable, loadBalancerCallable].collect {
        it.curry(region)
      }
    }

    List<Map> results = (List<Map>) executorService.invokeAll(callables)*.get()
    stopwatch.stop()
    log.info("Done retrieving Amazon caches in ${stopwatch.toString()}")
    def asgCache = [:]
    def launchConfigCache = [:]
    def imageCache = [:]
    def loadBalancerCache = [:]

    results.each { Map result ->
      if (result instanceof AsgResults) {
        asgCache += result
      } else if (result instanceof LaunchConfigResults) {
        launchConfigCache += result
      } else if (result instanceof ImageResults) {
        imageCache += result
      } else if (result instanceof LoadBalancerResults) {
        loadBalancerCache += result
      }
    }

    for (Map.Entry entry : asgCache) {
      def region = entry.key
      def asgs = entry.value?.values() as List

      for (AutoScalingGroup asg in asgs) {
        try {
          def names = Names.parseName(asg.autoScalingGroupName)
          if (!cache.containsKey(names.app)) {
            cache[names.app] = [:]
          }
          if (!cache[names.app].containsKey(names.cluster)) {
            cache[names.app][names.cluster] = []
          }
          Cluster cluster = cache[names.app][names.cluster].find { it.zone == region }
          if (!cluster) {
            cluster = new AmazonCluster(name: names.cluster, zone: region, serverGroups: [])
            cache[names.app][names.cluster] << cluster
          }
          ApplicationContextHolder.applicationContext.autowireCapableBeanFactory.autowireBean(cluster)

          LaunchConfiguration launchConfig = null
          String userData = null
          if (launchConfigCache.get(region).containsKey(asg.launchConfigurationName)) {
            launchConfig = launchConfigCache.get(region).get(asg.launchConfigurationName) as LaunchConfiguration
            userData = launchConfig.userData ? new String(Base64.decodeBase64(launchConfig.userData as String)) : null
          }

          def loadBalancers = asg.loadBalancerNames.collect { loadBalancerCache[region][it] }

          def resp = [name   : names.group, type: SERVER_GROUP_TYPE, region: region, cluster: names.cluster, instances: asg.instances, loadBalancers: loadBalancers,
                      created: asg.createdTime, launchConfig: launchConfig, userData: userData, capacity: [min: asg.minSize, max: asg.maxSize, desired: asg.desiredCapacity], asg: asg]

          Image image = imageCache[region][launchConfig.imageId]
          def buildVersion = image?.tags?.find { it.key == "appversion" }?.value
          if (buildVersion) {
            def props = AppVersion.parseName(buildVersion)?.properties
            if (props) {
              resp.buildInfo = ["buildNumber", "commit", "packageName", "buildJobName"].collectEntries {
                [(it): props[it]]
              }
            }
          }

          def serverGroup = new AmazonServerGroup(names.group, resp)
          cluster.serverGroups << serverGroup
        } catch (Exception e) {
          log.info "Error, ${e.message}"
        }
      }
    }
    cache.sort { a, b -> a.key.toLowerCase() <=> b.key.toLowerCase() }
  }

  @InheritConstructors
  private static class AsgResults extends HashMap {}

  final def asgCallable = { String region ->
    def autoScaling = amazonClientProvider.getAutoScaling(credentials.credentials, region)
    def result = autoScaling.describeAutoScalingGroups()
    List<AutoScalingGroup> asgs = []
    while (true) {
      asgs.addAll result.autoScalingGroups
      if (result.nextToken) {
        result = autoScaling.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withNextToken(result.nextToken))
      } else {
        break
      }
    }
    new AsgResults([(region): asgs.collectEntries {
      [(it.autoScalingGroupName): it]
    }])
  }

  @InheritConstructors
  private static class LaunchConfigResults extends HashMap {}

  final def launchConfigCallable = { String region ->
    def autoScaling = amazonClientProvider.getAutoScaling(credentials.credentials, region)
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
    new LaunchConfigResults([(region): launchConfigs.collectEntries {
      [(it.launchConfigurationName): it]
    }])
  }

  @InheritConstructors
  private static class ImageResults extends HashMap {}

  final def imageCallable = { String region ->
    def ec2 = amazonClientProvider.getAmazonEC2(credentials.credentials, region)
    new ImageResults([(region): ec2.describeImages().images.collectEntries {
      [(it.imageId): it]
    }])
  }

  @InheritConstructors
  private static class LoadBalancerResults extends HashMap {}

  final def loadBalancerCallable = { String region ->
    def loadBalancing = amazonClientProvider.getAmazonElasticLoadBalancing(credentials.credentials, region)
    new LoadBalancerResults([(region): loadBalancing.describeLoadBalancers().loadBalancerDescriptions.collectEntries {
      [(it.loadBalancerName): it]
    }])
  }

}
