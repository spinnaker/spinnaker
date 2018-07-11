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
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.cache.OnDemandAware
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.ServerGroupParameters
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLaunchConfig
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackServerGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.openstack.utils.DateUtils
import groovy.util.logging.Slf4j
import org.openstack4j.model.heat.Stack
import org.openstack4j.model.network.ext.status.LoadBalancerV2Status

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.ServerGroup
import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.IMAGES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackServerGroupCachingAgent extends AbstractOpenstackCachingAgent implements OnDemandAgent, OnDemandAware {

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
    List<Stack> stacks = clientProvider.listStacks(region)
    List<String> serverGroupKeys = stacks.collect { Keys.getServerGroupKey(it.name, accountName, region) }

    buildLoadDataCache(providerCache, serverGroupKeys) { CacheResultBuilder cacheResultBuilder ->
      buildCacheResult(providerCache, cacheResultBuilder, stacks)
    }
  }

  protected CacheResult buildCacheResult(ProviderCache providerCache, CacheResultBuilder cacheResultBuilder, List<Stack> stacks) {
    // Lookup all instances and group by stack Id
    Map<String, List<String>> instancesByStackId = getInstanceIdsByStack(region, stacks)

    stacks?.each { Stack stack ->
      try {
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

          Stack detail = clientProvider.getStack(region, stack.name)
          Set<String> loadBalancerKeys = [].toSet()
          Set<LoadBalancerV2Status> statuses = [].toSet()
          if (detail && detail.parameters) {
            statuses = ServerGroupParameters.fromParamsMap(detail.parameters).loadBalancers?.collect { loadBalancerId ->
              LoadBalancerV2Status status = null
              try {
                status = clientProvider.getLoadBalancerStatusTree(region, loadBalancerId)?.loadBalancerV2Status
                if (status) {
                  String loadBalancerKey = Keys.getLoadBalancerKey(status.name, status.id, accountName, region)
                  cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
                    relationships[SERVER_GROUPS.ns].add(serverGroupKey)
                  }
                  loadBalancerKeys << loadBalancerKey
                }
              } catch (OpenstackProviderException e) {
                //Do nothing ... Load balancer not found.
              }
              status
            }?.findAll()?.toSet()
          }

          List<String> instanceKeys = []
          instancesByStackId[stack.id]?.each { String id ->
            String instanceKey = Keys.getInstanceKey(id, accountName, region)
            cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).relationships[SERVER_GROUPS.ns].add(serverGroupKey)
            instanceKeys.add(instanceKey)
          }

          OpenstackServerGroup openstackServerGroup = buildServerGroup(providerCache, detail, statuses, instanceKeys)

          if (shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)) {
            moveOnDemandDataToNamespace(objectMapper, typeReference, cacheResultBuilder, serverGroupKey)
          } else {
            cacheResultBuilder.namespace(SERVER_GROUPS.ns).keep(serverGroupKey).with {
              attributes = objectMapper.convertValue(openstackServerGroup, ATTRIBUTES)
              relationships[APPLICATIONS.ns].add(appKey)
              relationships[CLUSTERS.ns].add(clusterKey)
              relationships[LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
              relationships[INSTANCES.ns].addAll(instanceKeys)
            }
          }
        }
      } catch (Exception e) {
        log.error("Error building cache for stack ${stack}", e)
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
   * Transform stacks into a map of stacks by instance ids.
   * @param stacks
   * @return
   */
  Map<String, List<String>> getInstanceIdsByStack(String region, List<Stack> stacks) {
    Map<String, Future<List<String>>> resourceMap = stacks.collectEntries {
      String name = it.name
      String id = it.id
      [(id) : CompletableFuture.supplyAsync {
        clientProvider.getInstanceIdsForStack(region, name)
      }.exceptionally { t -> [] } ]
    }

    CompletableFuture.allOf(resourceMap.values().flatten() as CompletableFuture[]).join()

    resourceMap.collectEntries([:]) { [it.key, it.value.get()] }
  }

  /**
   * Helper method for creating server group.
   * @param providerCache
   * @param stack
   * @param loadbalancerIds
   * @return
   */
  OpenstackServerGroup buildServerGroup(ProviderCache providerCache, Stack stack, Set<LoadBalancerV2Status> statuses, List<String> instanceKeys) {
    ServerGroupParameters params = ServerGroupParameters.fromParamsMap(stack?.parameters ?: [:])
    Map<String, Object> launchConfig = buildLaunchConfig(params)
    Map<String, Object> openstackImage = buildImage(providerCache, (String) launchConfig?.image)
    Map<String, Object> advancedConfig = buildAdvancedConfig(params)
    Set<String> loadbalancerIds = statuses.collect { status -> Keys.getLoadBalancerKey(status.name, status.id, accountName, region) }

    OpenstackServerGroup.builder()
      .account(accountName)
      .region(region)
      .name(stack?.name)
      .createdTime(stack == null ? null : DateUtils.parseZonedDateTime(stack.creationTime).toInstant().toEpochMilli())
      .scalingConfig(buildScalingConfig(params))
      .launchConfig(launchConfig)
      .loadBalancers(loadbalancerIds)
      .image(openstackImage)
      .buildInfo(buildInfo((Map<String, String>) openstackImage?.properties))
      .disabled(calculateServerGroupStatus(providerCache, statuses, instanceKeys))
      .subnetId(params.subnetId)
      .advancedConfig(advancedConfig)
      .tags(params.tags ?: [:])
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

        if (appVersion) {
          result.packageName = appVersion.packageName
          result.version = appVersion.version
          result.commit = appVersion.commit
        }

        String buildHost = properties.get('build_host')
        String buildInfoUrl = properties.get('build_info_url')

        if (appVersion && appVersion.buildJobName) {
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
  Map<String, Object> buildScalingConfig(ServerGroupParameters parameters) {
    Map<String, Object> result = Maps.newHashMap()

    if (parameters) {
      // Using a default value of 0 for min, max, & desired size
      result.put('minSize', parameters.minSize ?: 0)
      result.put('maxSize', parameters.maxSize ?: 0)
      result.put('desiredSize', parameters.desiredSize ?: 0)
      result.put('autoscalingType', parameters.autoscalingType ? parameters.autoscalingType.jsonValue() : "")
      [up:parameters.scaleup, down:parameters.scaledown].each {
        result.put("scale${it.key}".toString(), objectMapper.convertValue(it.value, ATTRIBUTES))
      }
    }

    result
  }

  /**
   * Builds a new launch config based upon template parameters.
   * @param parameters
   * @return
   */
  Map<String, Object> buildLaunchConfig(ServerGroupParameters parameters) {
    Map<String, Object> result = Collections.emptyMap()

    if (parameters) {
      OpenstackLaunchConfig launchConfig = OpenstackLaunchConfig.builder()
        .image(parameters.image)
        .instanceType(parameters.instanceType)
        .networkId(parameters.networkId)
        .loadBalancerId(parameters.loadBalancers?.join(","))
        .securityGroups(parameters.securityGroups)
        .associatePublicIpAddress(parameters.floatingNetworkId != null)
        .floatingNetworkId(parameters.floatingNetworkId)
        .build()

      result = ((Map<String, Object>)objectMapper.convertValue(launchConfig, ATTRIBUTES)).findAll { it.value }
    }

    result
  }

  /**
   * Builds advanced config from the advanced server group inputs.
   * @param parameters
   * @return
   */
  Map<String, Object> buildAdvancedConfig(ServerGroupParameters parameters) {
    Map<String, Object> params = [:]
    if (parameters.sourceUserDataType) {
      params << [userDataType:parameters.sourceUserDataType]
    }
    if (parameters.sourceUserData) {
      params << [userData:parameters.sourceUserData]
    }
    params
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

  // TODO drmaas if we cache the load balancer status tree for each load balancer, we can do this calculation
  // in the OpenstackClusterProvider instead.
  /**
   * If no instances match load balancer members, or if there are no instanceKeys or load balancer statuses,
   * then this will return true (disabled).
   *
   * calculate server group healthStatus
   * @param statuses
   * @return
   */
  boolean calculateServerGroupStatus(ProviderCache providerCache, Set<LoadBalancerV2Status> statuses, List<String> instanceKeys) {

    //when all members for this server group are disabled, the server group is disabled, otherwise it is enabled.
    Map<String, String> memberStatusMap = statuses?.collectEntries { lbStatus ->
      lbStatus.listenerStatuses?.collectEntries { listenerStatus ->
        listenerStatus.lbPoolV2Statuses?.collectEntries { poolStatus ->
          poolStatus.memberStatuses?.collectEntries { memberStatus ->
            [(memberStatus.address): memberStatus.operatingStatus.toString()]
          }
        }
      }
    }

    // Read instances from cache and create a map indexed by ipv4/ipv6 address to compare to load balancer member status
    Collection<CacheData> instancesData = providerCache.getAll(INSTANCES.ns, instanceKeys, RelationshipCacheFilter.none())
    Map<String, CacheData> addressCacheDataMap = instancesData.collectEntries { data ->
      [(data.attributes.ipv4): data, (data.attributes.ipv6): data]
    }

    // Find corresponding instance id, save key for caching below, and add new lb health based upon current member status
    memberStatusMap
      .findAll { key, value ->
        key == addressCacheDataMap[key]?.attributes?.ipv4?.toString() ||
        key == addressCacheDataMap[key]?.attributes?.ipv6?.toString()
      }
      .every { key, value -> value == "DISABLED" }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == ServerGroup && cloudProvider == ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    OnDemandAgent.OnDemandResult result = null

    if (data.containsKey("serverGroupName") && data.account == accountName && data.region == region) {
      String serverGroupName = data.serverGroupName.toString()
      String serverGroupKey = Keys.getServerGroupKey(serverGroupName, accountName, region)

      Stack stack = metricsSupport.readData {
          clientProvider.getStack(region, serverGroupName)
      }

      CacheResult cacheResult = metricsSupport.transformData {
        buildCacheResult(providerCache, new CacheResultBuilder(startTime: Long.MAX_VALUE), stack ? [stack] : [])
      }

      processOnDemandCache(cacheResult, objectMapper, metricsSupport, providerCache, serverGroupKey)
      result = buildOnDemandCache(stack, onDemandAgentType, cacheResult, SERVER_GROUPS.ns, serverGroupKey)

      log.info("On demand cache refresh succeeded. Data: ${data}. Added ${stack ? 1 : 0} items to the cache.")
    }

    result
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    getAllOnDemandCacheByRegionAndAccount(providerCache, accountName, region)
  }
}
