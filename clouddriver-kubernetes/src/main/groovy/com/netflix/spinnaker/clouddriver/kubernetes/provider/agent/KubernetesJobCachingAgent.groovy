/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.kubernetes.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.extensions.Job

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesJobCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {
  final KubernetesCloudProvider kubernetesCloudProvider
  final String accountName
  final String namespace
  final String category = 'job'
  final KubernetesCredentials credentials
  final ObjectMapper objectMapper

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    INFORMATIVE.forType(Keys.Namespace.APPLICATIONS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.CLUSTERS.ns),
    INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.JOBS.ns),
    INFORMATIVE.forType(Keys.Namespace.PROCESSES.ns),
  ] as Set)

  KubernetesJobCachingAgent(KubernetesCloudProvider kubernetesCloudProvider,
                            String accountName,
                            KubernetesCredentials credentials,
                            String namespace,
                            ObjectMapper objectMapper,
                            Registry registry) {
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.namespace = namespace
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "$kubernetesCloudProvider.id:$OnDemandAgent.OnDemandType.Job")
  }

  @Override
  String getProviderName() {
    KubernetesProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${namespace}/${KubernetesJobCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
  }
  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("jobName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    if (data.region != namespace) {
      return null
    }

    def jobName = data.jobName.toString()

    Job job = metricsSupport.readData {
      loadJob(jobName)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([job], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getJobKey(accountName, namespace, jobName)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getJobKey(accountName, namespace, jobName),
          10 * 60, // ttl is 10 minutes
          [
            cacheTime: System.currentTimeMillis(),
            cacheResults: jsonResult,
            processedCount: 0,
            processedTime: null
          ],
          [:]
        )

        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this server group if it no longer exists.
    Map<String, Collection<String>> evictions = job ? [:] : [
      (Keys.Namespace.JOBS.ns): [
        Keys.getJobKey(accountName, namespace, jobName)
      ]
    ]

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && parse.namespace == namespace && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, keys).collect {
      [
        details  : Keys.parse(it.id),
        cacheTime: it.attributes.cacheTime,
        processedCount: it.attributes.processedCount,
        processedTime: it.attributes.processedTime
      ]
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    OnDemandAgent.OnDemandType.Job == type && cloudProvider == kubernetesCloudProvider.id
  }

  List<Job> loadJobs() {
    credentials.apiAdaptor.getJobs(namespace)
  }

  Job loadJob(String name) {
    credentials.apiAdaptor.getJob(namespace, name)
  }

  List<Pod> loadPods(String jobName) {
    credentials.apiAdaptor.getJobPods(namespace, jobName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<Job> jobList = loadJobs()

    def evictFromOnDemand = []
    def keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
      jobList.collect { Keys.getJobKey(accountName, namespace, it.metadata.name) }).each {
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        evictFromOnDemand << it
      } else {
        keepInOnDemand << it
      }
    }

    def result = buildCacheResult(jobList, keepInOnDemand.collectEntries { [(it.id): it] }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  private static void cache(Map<String, List<CacheData>> cacheResults, String namespace, Map<String, CacheData> cacheDataById) {
    cacheResults[namespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (existingCacheData) {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      } else {
        cacheDataById[it.id] = it
      }
    }
  }

  private CacheResult buildCacheResult(List<Job> jobs, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedJobs = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedProcesses = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    for (Job job : jobs) {
      if (!job) {
        continue
      }

      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getJobKey(accountName, namespace, job.metadata.name)] : null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() { })
        cache(cacheResults, Keys.Namespace.APPLICATIONS.ns, cachedApplications)
        cache(cacheResults, Keys.Namespace.CLUSTERS.ns, cachedClusters)
        cache(cacheResults, Keys.Namespace.JOBS.ns, cachedJobs)
        cache(cacheResults, Keys.Namespace.PROCESSES.ns, cachedProcesses)
      } else {
        def jobName = job.metadata.name
        def pods = loadPods(jobName)
        def names = Names.parseName(jobName)
        def applicationName = names.app
        def clusterName = names.cluster

        def jobKey = Keys.getJobKey(accountName, namespace, jobName)
        def applicationKey = Keys.getApplicationKey(applicationName)
        def clusterKey = Keys.getClusterKey(accountName, applicationName, category, clusterName)
        def processKeys = []
        def loadBalancerKeys = KubernetesUtil.getLoadBalancers(job).collect({
          Keys.getLoadBalancerKey(accountName, namespace, it)
        })

        cachedApplications[applicationKey].with {
          attributes.name = applicationName
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.JOBS.ns].add(jobKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        cachedClusters[clusterKey].with {
          attributes.name = clusterName
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.JOBS.ns].add(jobKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        pods.forEach { pod ->
          def key = Keys.getProcessKey(accountName, namespace, pod.metadata.name)
          processKeys << key
          cachedProcesses[key].with {
            relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
            relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
            relationships[Keys.Namespace.JOBS.ns].add(jobKey)
            relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          }
        }

        loadBalancerKeys.forEach { loadBalancerKey ->
          cachedLoadBalancers[loadBalancerKey].with {
            relationships[Keys.Namespace.JOBS.ns].add(jobKey)
            relationships[Keys.Namespace.PROCESSES.ns].addAll(processKeys)
          }
        }

        cachedJobs[jobKey].with {
          attributes.name = jobName
          attributes.job = job
          attributes.account = accountName
          attributes.namespace = namespace
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          relationships[Keys.Namespace.PROCESSES.ns].addAll(processKeys)
        }
      }
    }

    log.info("Caching ${cachedApplications.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedJobs.size()} jobs in ${agentType}")
    log.info("Caching ${cachedProcesses.size()} processes in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.APPLICATIONS.ns): cachedApplications.values(),
      (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (Keys.Namespace.CLUSTERS.ns): cachedClusters.values(),
      (Keys.Namespace.JOBS.ns): cachedJobs.values(),
      (Keys.Namespace.PROCESSES.ns): cachedProcesses.values(),
      (Keys.Namespace.ON_DEMAND.ns): onDemandKeep.values()
    ],[
      (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
    ])

  }
}
