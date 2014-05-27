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

package com.netflix.bluespar.oort.clusters.aws

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.netflix.bluespar.amazon.security.AmazonClientProvider
import com.netflix.bluespar.amazon.security.AmazonCredentials
import com.netflix.bluespar.oort.clusters.Cluster
import com.netflix.bluespar.oort.clusters.ClusterProvider
import com.netflix.bluespar.oort.remoting.AggregateRemoteResource
import com.netflix.bluespar.oort.remoting.RemoteResource
import com.netflix.bluespar.oort.spring.ApplicationContextHolder
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger

@Component
class AmazonClusterProvider implements ClusterProvider {
  private static final String SERVER_GROUP_TYPE = "Amazon"

  @Value('${discovery.url.format:#{null}}')
  String discoveryUrlFormat

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  AggregateRemoteResource edda

  @Autowired
  AmazonCredentials amazonCredentials

  @Override
  Map<String, List<Cluster>> get(String deployable) {
    Map<String, List<Cluster>> clusterMap = Cacher.get().get(deployable) as Map
    def clusters = new ArrayList<>(clusterMap.values()?.flatten())
    clusters.each {
      it.serverGroups.each { serverGroup ->
        serverGroup.instances = serverGroup.instances.collect { getInstance(serverGroup.region, it.instanceId) + [eureka: getDiscoveryHealth(serverGroup.region, application, it.instanceId)]}
        if (serverGroup.asg && serverGroup.asg.suspendedProcesses?.collect { it.processName }?.containsAll(["Terminate", "Launch"])) {
          serverGroup.status = "DISABLED"
        } else {
          serverGroup.status = "ENABLED"
        }
      }
    }
    clusterMap
  }

  @Override
  List<Cluster> getByName(String deployable, String clusterName) {
    get(deployable)?.get(clusterName)
  }

  @Override
  List<Cluster> getByNameAndZone(String deployable, String clusterName, String zone) {
    get(deployable)?.get(clusterName)?.findAll { it.zone == zone }
  }

  def getInstance(String region, String instanceId) {
    try {
      def client = amazonClientProvider.getAmazonEC2(amazonCredentials, region)
      def request = new DescribeInstancesRequest().withInstanceIds(instanceId)
      def result = client.describeInstances(request)
      new HashMap(result.reservations?.instances?.getAt(0)?.getAt(0)?.properties)
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "offline"]] }
  }

  def getDiscoveryHealth(String region, String application, String instanceId) {
    if (!discoveryUrlFormat) return
    try {
      def map = edda.getRemoteResource(region) get "/REST/v2/discovery/applications/$application;_pp:(instances:(id,status,overriddenStatus))"
      def instance = map.instances.find { it.id == instanceId }
      if (instance) {
        return [id: instanceId, status: instance.overriddenStatus?.name != "UNKNOWN" ? instance.overriddenStatus.name : instance.status.name]
      }
    } catch (IGNORE) { [instanceId: instanceId, state: [name: "unknown"]] }
  }

  @Component
  static class Cacher {
    private static final Logger log = Logger.getLogger(this.class.simpleName)
    private static def firstRun = true
    private static def lock = new ReentrantLock()
    private static def map = new ConcurrentHashMap()
    private static def executorService = Executors.newFixedThreadPool(20)

    @Autowired
    AggregateRemoteResource edda

    @Autowired
    AmazonCredentials amazonCredentials

    static Map get() {
      lock.lock()
      def m = new HashMap(map)
      lock.unlock()
      m
    }

    @Scheduled(fixedRate = 30000l)
    void cacheClusters() {
      if (firstRun) {
        lock.lock()
      }

      def run = new ConcurrentHashMap()
      def stopwatch = new StopWatch()
      stopwatch.start()
      log.info "Beginning caching amazon clusters."
      def asgCallable = { String region, RemoteResource remoteResource ->
        try {
          [(region): remoteResource.query("/REST/v2/aws/autoScalingGroups;_expand").collectEntries {
            [(it.autoScalingGroupName): it]
          }]
        } catch (Exception e) {
          log.error "Problem retrieving ASGs for $region", e
        }
      }
      def launchConfigCallable = { String region, RemoteResource remoteResource ->
        [(region): remoteResource.query("/REST/v2/aws/launchConfigurations;_expand").collectEntries {
          [(it.launchConfigurationName): it]
        }]
      }
      def imageCallable = { String region, RemoteResource remoteResource ->
        [(region): remoteResource.query("/REST/v2/aws/images;_expand").collectEntries {
          [(it.imageId): it]
        }]
      }
      def callables = ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collect { region ->
        [asgCallable, launchConfigCallable, imageCallable].collect {
          it.curry(region, edda.getRemoteResource(region))
        }
      }?.flatten()

      def results = executorService.invokeAll(callables)*.get()
      def asgCache = (Map<String, List<Map>>)results.getAt(0) + results.getAt(3) + results.getAt(6) + results.getAt(9)
      def launchConfigCache = results.getAt(1) + results.getAt(4) + results.getAt(7) + results.getAt(10)
      def imageCache = results.getAt(2) + results.getAt(5) + results.getAt(8) + results.getAt(11)

      for (Map.Entry entry : asgCache) {
        def region = entry.key
        def asgs = entry.value?.values() as List

        for (Map asg in asgs) {
          try {
            def names = Names.parseName(asg.autoScalingGroupName)
            if (!run.containsKey(names.app)) {
              run[names.app] = [:]
            }
            if (!run[names.app].containsKey(names.cluster)) {
              run[names.app][names.cluster] = []
            }
            Cluster cluster = run[names.app][names.cluster].find { it.zone == region }
            if (!cluster) {
              cluster = new AmazonCluster(name: names.cluster, zone: region, serverGroups: [])
              run[names.app][names.cluster] << cluster
            }
            ApplicationContextHolder.applicationContext.autowireCapableBeanFactory.autowireBean(cluster)

            Map launchConfig = launchConfigCache[region][asg.launchConfigurationName] as Map
            String userData = new String(Base64.decodeBase64(launchConfig.userData as String))

            def resp = [name: names.group, type: SERVER_GROUP_TYPE, region: region, cluster: names.cluster, instances: asg.instances,
                        created: asg.createdTime, launchConfig: launchConfig, userData: userData, capacity: [min: asg.minSize, max: asg.maxSize, desired: asg.desiredCapacity], asg: asg]

            Map image = imageCache[region][launchConfig.imageId] as Map
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
          } catch (IGNORE) {}
        }
      }
      if (!lock.isLocked()) {
        lock.lock()
      }
      map = run.sort { a, b -> a.key.toLowerCase() <=> b.key.toLowerCase() }
      lock.unlock()
      if (firstRun) {
        firstRun = false
      }
      stopwatch.stop()
      log.info "Done caching amazon clusters in ${stopwatch.shortSummary()}"
    }
  }

}
