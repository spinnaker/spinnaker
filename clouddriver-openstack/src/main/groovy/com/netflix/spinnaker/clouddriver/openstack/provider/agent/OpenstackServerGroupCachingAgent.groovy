/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLaunchConfig
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import com.netflix.spinnaker.clouddriver.openstack.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPool

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.ON_DEMAND

import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES
import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.ServerGroup

@Slf4j
class OpenstackServerGroupCachingAgent extends AbstractOpenstackCachingAgent implements OnDemandAgent {

  final Set<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set)

  final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport
  final String agentType = "${account.name}/${region}/${OpenstackServerGroupCachingAgent.simpleName}"
  final String onDemandAgentType = "${agentType}-OnDemand"

  OpenstackServerGroupCachingAgent(final OpenstackNamedAccountCredentials account, final String region,
                                   final ObjectMapper objectMapper, final Registry registry) {
    super(account, region)
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${ID}:${ServerGroup}")
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: System.currentTimeMillis())

    List<Stack> stacks = clientProvider.listStacks(region)

    List<String> serverGroupKeys = stacks.collect { Keys.getServerGroupKey(it.name, accountName, region) }
    providerCache.getAll(ON_DEMAND.ns, serverGroupKeys).each { CacheData cacheData ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // managed instance groups. Furthermore, cache data that hasn't been moved to the proper namespace needs to be
      // updated in the ON_DEMAND cache, so don't evict data without a processedCount > 0.
      if (cacheData.attributes.cacheTime < cacheResultBuilder.startTime && cacheData.attributes.processedCount > 0) {
        cacheResultBuilder.onDemand.toEvict << cacheData.id
      } else {
        cacheResultBuilder.onDemand.toKeep[cacheData.id] = cacheData
      }
    }

    CacheResult result = buildCacheResult(providerCache, cacheResultBuilder, stacks)

    result?.cacheResults?.get(ON_DEMAND.ns)?.each { CacheData cacheData ->
      cacheData.attributes.processedTime = System.currentTimeMillis()
      cacheData.attributes.processedCount = (cacheData.attributes.processedCount ?: 0) + 1
    }

    result
  }

  protected CacheResult buildCacheResult(ProviderCache providerCache, CacheResultBuilder cacheResultBuilder, List<Stack> stacks) {
    // Lookup all instances and group by stack Id
    Map<String, List<? extends Server>> instancesByStackId = clientProvider.getInstancesByServerGroup(region)

    stacks?.each { Stack stack ->
      String serverGroupName = stack.name
      Names names = Names.parseName(serverGroupName)
      if (!names && !names.app && !names.cluster) {
        log.info("Skipping server group ${serverGroupName}")
      } else {
        String applicationName = names.app
        String clusterName = names.cluster

        String serverGroupKey = Keys.getServerGroupKey(serverGroupName, accountName, region)
        String clusterKey = Keys.getClusterKey(accountName, applicationName, clusterName)
        String appKey = Keys.getApplicationKey(applicationName)

        cacheResultBuilder.namespace(APPLICATIONS.ns).keep(appKey).with {
          attributes.name = applicationName
          relationships[CLUSTERS.ns].add(clusterKey)
        }

        cacheResultBuilder.namespace(CLUSTERS.ns).keep(clusterKey).with {
          attributes.name = clusterName
          attributes.accountName = accountName
          relationships[APPLICATIONS.ns].add(appKey)
          relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        }

        String loadBalancerKey = null
        Stack detail = clientProvider.getStack(region, stack.name)
        if (detail && detail.parameters) {
          LbPool pool = clientProvider.getLoadBalancerPool(region, detail.parameters['pool_id'])
          if (pool) {
            loadBalancerKey = Keys.getLoadBalancerKey(pool.name, pool.id, accountName, region)
            cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
              relationships[SERVER_GROUPS.ns].add(serverGroupKey)
            }
          }
        }

        List<String> instanceKeys = []
        instancesByStackId[stack.id]?.each { Server server ->
          String instanceKey = Keys.getInstanceKey(server.id, accountName, region)
          cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).relationships[SERVER_GROUPS.ns].add(serverGroupKey)
          instanceKeys.add(instanceKey)
        }

        Set<String> loadBalancerIds = loadBalancerKey ? Sets.newHashSet(loadBalancerKey) : Collections.emptySet()
        OpenstackServerGroup openstackServerGroup = buildServerGroup(providerCache, detail, loadBalancerIds)

        if (shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)) {
          moveOnDemandDataToNamespace(cacheResultBuilder, serverGroupKey)
        } else {
          cacheResultBuilder.namespace(SERVER_GROUPS.ns).keep(serverGroupKey).with {
            attributes = objectMapper.convertValue(openstackServerGroup, ATTRIBUTES)
            relationships[APPLICATIONS.ns].add(appKey)
            relationships[CLUSTERS.ns].add(clusterKey)
            relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
            relationships[INSTANCES.ns].addAll(instanceKeys)
          }
        }
      }
    }

    cacheResultBuilder.namespaceBuilders.keySet().each { String namespace ->
      log.info("Caching ${cacheResultBuilder.namespace(namespace).keepSize()} ${namespace} in ${agentType}")
    }

    log.info("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.info("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

    cacheResultBuilder.build()
  }

  boolean shouldUseOnDemandData(CacheResultBuilder cacheResultBuilder, String serverGroupKey) {
    CacheData cacheData = cacheResultBuilder.onDemand.toKeep[serverGroupKey]
    cacheData ? cacheData.attributes.cacheTime >= cacheResultBuilder.startTime : false
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder, String serverGroupKey) {
    Map<String, List<MutableCacheData>> onDemandData = objectMapper.readValue(
      cacheResultBuilder.onDemand.toKeep[serverGroupKey].attributes.cacheResults as String,
      new TypeReference<Map<String, List<MutableCacheData>>>() {})

    onDemandData.each { String namespace, List<MutableCacheData> cacheDatas ->
      cacheDatas.each { MutableCacheData cacheData ->
        cacheResultBuilder.namespace(namespace).keep(cacheData.id).with {
          attributes = cacheData.attributes
          relationships = cacheData.relationships
        }
        cacheResultBuilder.onDemand.toKeep.remove(cacheData.id)
      }
    }
  }

  /**
   * Helper method for creating server group.
   * @param providerCache
   * @param stack
   * @param loadbalancerIds
   * @return
   */
  OpenstackServerGroup buildServerGroup(ProviderCache providerCache, Stack stack, Set<String> loadbalancerIds) {
    Map<String, Object> launchConfig = buildLaunchConfig(stack.parameters)
    Map<String, Object> openstackImage = buildImage(providerCache, (String) launchConfig?.image)

    //TODO this check will change once we have a config that indicates the openstack version, e.g. kilo, liberty, mitaka
    //kilo stack create times have a 'Z' on the end. It was removed in liberty and mitaka.
    ZonedDateTime zonedDateTime
    try {
      //kilo
      zonedDateTime = ZonedDateTime.parse(stack.creationTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    } catch(DateTimeParseException e) {
      //liberty+
      zonedDateTime = ZonedDateTime.parse(stack.creationTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    OpenstackServerGroup.builder()
      .account(accountName)
      .region(region)
      .name(stack.name)
      .createdTime(stack.creationTime ? zonedDateTime.toInstant().toEpochMilli() : ZonedDateTime.now().toInstant().toEpochMilli())
      .scalingConfig(buildScalingConfig(stack))
      .launchConfig(launchConfig)
      .loadBalancers(loadbalancerIds)
      .image(openstackImage)
      .buildInfo(buildInfo((Map<String, String>) openstackImage?.properties))
      .disabled(loadbalancerIds.isEmpty()) //TODO - Determine if we need to check to see if the stack is suspended
      .build()
  }

  /**
   * Creates build info from image definition.
   * @return
   */
  Map<String, Object> buildInfo(Map<String, String> properties) {
    Map<String, Object> result = [:]

    if (properties) {
      String appVersionKey = properties.get('appversion')

      if (appVersionKey) {
        AppVersion appVersion = AppVersion.parseName(appVersionKey)

        result.packageName = appVersion.packageName
        result.version = appVersion.version
        result.commit = appVersion.commit

        String buildHost = properties.get('build_host')
        String buildInfoUrl = properties.get('build_info_url')

        if (appVersion.buildJobName) {
          Map<String, String> jenkinsMap = [name: appVersion.buildJobName, number: appVersion.buildNumber]
          if (buildHost) {
            jenkinsMap.put('host', buildHost)
          }
          result.jenkins = jenkinsMap
        }

        if (buildInfoUrl) {
          result.buildInfoUrl = buildInfoUrl
        }
      }
    }

    result
  }
  /**
   * Builds scaling config map from stack definition.
   * @param stack
   * @return
   */
  Map<String, Object> buildScalingConfig(Stack stack) {
    Map<String, Object> result = Maps.newHashMap()

    if (stack) {
      result.put('minSize', stack.parameters?.get('min_size') ?: 0)
      result.put('maxSize', stack.parameters?.get('max_size') ?: 0)
    }

    result
  }

  /**
   * Builds a new launch config based upon template parameters.
   * @param parameters
   * @return
   */
  Map<String, Object> buildLaunchConfig(Map<String, String> parameters) {
    Map<String, Object> result = Collections.emptyMap()

    if (parameters) {
      OpenstackLaunchConfig launchConfig = OpenstackLaunchConfig.builder()
        .image(parameters.get('image'))
        .instanceType(parameters.get('flavor'))
        .networkId(parameters.get('network_id'))
        .loadBalancerId(parameters.get('pool_id'))
        .securityGroups(parameters.get('security_groups')?.split(',')?.toList())
        .build()

      result = objectMapper.convertValue(launchConfig, ATTRIBUTES)
    }

    result
  }

  /**
   * Builds an image from cache.  Needed for build info, so looking up now.
   * @param imageId
   * @return
   */
  Map<String, Object> buildImage(ProviderCache providerCache, String image) {
    Map<String, Object> result = null

    CacheData cacheData = providerCache.get(IMAGES.ns, Keys.getImageKey(image, accountName, region))
    if (cacheData) {
      result = cacheData.attributes
    }

    result
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == ServerGroup && cloudProvider == ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    OnDemandAgent.OnDemandResult result = null

    if (data.containsKey("serverGroupName") && data.account == accountName && data.region == region) {
      result = new OnDemandAgent.OnDemandResult(sourceAgentType: onDemandAgentType, evictions: [:].withDefault { _ -> [] })

      String serverGroupName = data.serverGroupName.toString()
      String serverGroupKey = Keys.getServerGroupKey(serverGroupName, accountName, region)

      Stack stack = metricsSupport.readData {
        try {
          clientProvider.getStack(region, serverGroupName)
        } catch (OpenstackProviderException ope) {
          //Do nothing ... Exception is thrown if a stack isn't found
        }
      }

      if (stack) {
        CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
        result.cacheResult = metricsSupport.transformData {
          buildCacheResult(providerCache, cacheResultBuilder, stack ? [stack] : [])
        }

        if (result.cacheResult.cacheResults.values().flatten().empty) {
          // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
          providerCache.evictDeletedItems(ON_DEMAND.ns, [serverGroupKey])
        } else {
          metricsSupport.onDemandStore {
            CacheData cacheData = new DefaultCacheData(
              serverGroupKey,
              TimeUnit.MINUTES.toSeconds(10) as Integer, // ttl
              [
                cacheTime     : System.currentTimeMillis(),
                cacheResults  : objectMapper.writeValueAsString(result.cacheResult.cacheResults),
                processedCount: 0,
                processedTime : null
              ],
              [:]
            )

            providerCache.putCacheData(ON_DEMAND.ns, cacheData)
          }
        }
      } else {
        result.evictions[SERVER_GROUPS.ns].add(serverGroupKey)
      }

      log.info("On demand cache refresh succeeded. Data: ${data}. Added ${stack ? 1 : 0} items to the cache.")
    }

    result
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys = providerCache.getIdentifiers(ON_DEMAND.ns).findAll { String key ->
      Map<String, String> parsedKey = Keys.parse(key)
      parsedKey && parsedKey.account == accountName && parsedKey.region == region
    }

    providerCache.getAll(ON_DEMAND.ns, keys).collect { CacheData cacheData ->
      [
        details       : Keys.parse(cacheData.id),
        cacheTime     : cacheData.attributes.cacheTime,
        processedCount: cacheData.attributes.processedCount,
        processedTime : cacheData.attributes.processedTime
      ]
    }
  }
}
