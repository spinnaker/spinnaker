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

import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.oort.clusters.Cluster
import com.netflix.spinnaker.oort.clusters.ClusterProvider
import com.netflix.spinnaker.oort.clusters.ClusterSummary
import com.netflix.spinnaker.oort.security.NamedAccountCredentials
import com.netflix.spinnaker.oort.security.NamedAccountCredentialsProvider
import com.netflix.spinnaker.oort.security.aws.AmazonAccountObject
import groovy.json.JsonSlurper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import org.springframework.web.client.RestTemplate

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger

@Component
class AmazonClusterProvider implements ClusterProvider {
  static final JsonSlurper jsonSlurper = new JsonSlurper()
  static final RestTemplate restTemplate = new RestTemplate()
  static def executorService = Executors.newFixedThreadPool(20)

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  NamedAccountCredentialsProvider namedAccountCredentialsProvider

  @Override
  List<Cluster> get(String application, String account) {
    List<Cluster> clusters = Cacher.get().get(account).get().get(application)
    extendServerGroups(application, account, clusters)
    clusters
  }

  private void extendServerGroups(String application, String account, List<AmazonCluster> clusters) {
    AmazonAccountObject amazonAccountObject = (AmazonAccountObject)namedAccountCredentialsProvider.getAccount(account)

    def callable = { AmazonServerGroup serverGroup ->
      String region = serverGroup.region as String
      serverGroup.instances = serverGroup.instances.collect { getInstance(amazonAccountObject.credentials, region, it.instanceId as String) +
        [eureka: getDiscoveryHealth(amazonAccountObject.discovery, region, application, it.instanceId as String)] }
      if (serverGroup.asg && serverGroup.asg.suspendedProcesses?.collect { it.processName }?.containsAll(["Terminate", "Launch"])) {
        serverGroup.status = "DISABLED"
      } else {
        serverGroup.status = "ENABLED"
      }
    }

    def callables = clusters.serverGroups.flatten().collect { callable.curry(it) }
    executorService.invokeAll(callables)*.get()
  }

  @Override
  Map<String, List<ClusterSummary>> getSummary(String application, String account) {
    List<Cluster> clusters = Cacher.get().get(account).get().get(application)?.values()?.flatten() ?: []
    clusters.collectEntries { Cluster cluster ->
      def instanceCount = (cluster.serverGroups.collect { it.getInstanceCount() }?.sum() ?: 0) as int
      def serverGroupNames = cluster.serverGroups.collect { it.name }
      [(cluster.name): new AmazonClusterSummary(this, application, cluster.name, cluster.serverGroups.size(), instanceCount, serverGroupNames)]
    }
  }

  @Override
  List<Cluster> getByName(String application, String account, String clusterName) {
    List<Cluster> clusters = Cacher.get().get(account).get().get(application).values()?.flatten() ?: []
    def theseClusters = clusters.findAll { it.name == clusterName }
    extendServerGroups(application, account, theseClusters)
    theseClusters
  }

  @Override
  List<Cluster> getByNameAndZone(String application, String account, String clusterName, String zone) {
    get(application, account)?.findAll { it.zone == zone }
  }

  def getInstance(AmazonCredentials amazonCredentials, String region, String instanceId) {
    try {
      def client = amazonClientProvider.getAmazonEC2(amazonCredentials, region)
      def request = new DescribeInstancesRequest().withInstanceIds(instanceId)
      def result = client.describeInstances(request)
      new HashMap(result.reservations?.instances?.getAt(0)?.getAt(0)?.properties)
    } catch (IGNORE) {
      [instanceId: instanceId, state: [name: "offline"]]
    }
  }

  static def getDiscoveryHealth(String discovery, String region, String application, String instanceId) {
    def unknown = [instanceId: instanceId, state: [name: "unknown"]]
    try {
      def text = restTemplate.getForObject(String.format("$discovery/discovery/v2/apps/$application", region), String)
      def json = jsonSlurper.parseText(text) as Map
      def instance = json.application.instance.find { it.dataCenterInfo.metadata.'instance-id' == instanceId }
      if (instance) {
        return [id: instanceId, status: instance.overriddenstatus != "UNKNOWN" ? instance.overriddenstatus : instance.status]
      }
    } catch (IGNORE) {
      return unknown
    }
  }

  @Component
  static class Cacher {
    private static final Logger log = Logger.getLogger(this.class.simpleName)
    private static def firstRun = true
    private static def lock = new ReentrantLock()
    private static ClusterCache clusterCache = new ClusterCache()

    @Autowired
    AmazonClientProvider amazonClientProvider

    @Autowired
    NamedAccountCredentialsProvider namedAccountCredentialsProvider

    static Map<String, AccountClusterCache> get() {
      ClusterCache.asNative()
    }

    @Scheduled(fixedRate = 30000l)
    void cacheClusters() {
      if (firstRun) {
        lock.lock()
      }
      def stopwatch = new StopWatch()
      stopwatch.start()
      log.info "Initializing Amazon Cluster Caching..."
      namedAccountCredentialsProvider.list().each { AmazonAccountObject accountCredentials ->
        def accountClusterCache = clusterCache.get(accountCredentials.name)
        if (!accountClusterCache) {
          clusterCache.put(amazonClientProvider, accountCredentials)
        }
      }
      clusterCache.list()*.reload()
      stopwatch.stop()
      log.info "Done with Amazon Cluster Caching in ${stopwatch.toString()}."
      if (lock.isLocked()) {
        lock.unlock()
      }
      if (firstRun) {
        firstRun = false
      }
    }
  }

  static class AccountClusterCache {
    final String name
    final AmazonClusterCachingAgent cachingAgent

    AccountClusterCache(AmazonAccountObject account, AmazonClientProvider provider) {
      this.name = account.name
      this.cachingAgent = new AmazonClusterCachingAgent(provider, account)
    }

    private Map cache = new ConcurrentHashMap()

    Map reload() {
      this.cache = executorService.submit(cachingAgent).get()
    }

    Map<String, Map<String, List<AmazonCluster>>> get() {
      if (!cache) {
        this.cache = reload()
      }
      new HashMap<>(this.cache)
    }
  }

  static class ClusterCache {
    private static final Map<String, AccountClusterCache> accountClusterCacheMap = [:]

    static AccountClusterCache get(String name) {
      if (accountClusterCacheMap.containsKey(name)) {
        accountClusterCacheMap.get(name)
      } else {
        null
      }
    }

    static List<AccountClusterCache> list() {
      accountClusterCacheMap.values() as List
    }

    static Map<String, AccountClusterCache> asNative() {
      accountClusterCacheMap
    }

    static void put(AmazonClientProvider provider, AmazonAccountObject obj) {
      accountClusterCacheMap.put(obj.name, new AccountClusterCache(obj, provider))
    }
  }

}
