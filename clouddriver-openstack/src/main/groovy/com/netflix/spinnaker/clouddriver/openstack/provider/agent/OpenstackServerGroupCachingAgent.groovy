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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLaunchConfig
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.Server
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.LbPool

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS

import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

//TODO - Implement on-demand caching
@Slf4j
class OpenstackServerGroupCachingAgent extends AbstractOpenstackCachingAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set)

  final ObjectMapper objectMapper

  OpenstackServerGroupCachingAgent(OpenstackNamedAccountCredentials account, String region,
                                   final ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackServerGroupCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)

    // Lookup all instances and group by stack Id
    Map<String, List<Server>> instancesByStackId = clientProvider.getInstancesByServerGroup(region)

    clientProvider.listStacks(region)?.each { Stack stack ->
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

        cacheResultBuilder.namespace(SERVER_GROUPS.ns).keep(serverGroupKey).with {
          attributes = objectMapper.convertValue(openstackServerGroup, ATTRIBUTES)
          relationships[APPLICATIONS.ns].add(appKey)
          relationships[CLUSTERS.ns].add(clusterKey)
          relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
          relationships[INSTANCES.ns].addAll(instanceKeys)
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
}
