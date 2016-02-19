/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupsListInstancesRequest
import com.google.api.services.compute.model.InstanceWithNamedPorts
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*;

@Slf4j
class GoogleServerGroupCachingAgent extends AbstractGoogleCachingAgent {

  final String region

  final Set<AgentDataType> providedDataTypes = [
      AUTHORITATIVE.forType(SERVER_GROUPS.ns),
      INFORMATIVE.forType(APPLICATIONS.ns),
      INFORMATIVE.forType(CLUSTERS.ns),
      INFORMATIVE.forType(INSTANCES.ns),
      INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set

  String agentType = "${accountName}/${region}/${GoogleServerGroupCachingAgent.simpleName}"

  GoogleServerGroupCachingAgent(GoogleCloudProvider googleCloudProvider,
                                String googleApplicationName,
                                String accountName,
                                String region,
                                String project,
                                Compute compute,
                                ObjectMapper objectMapper) {
    this.googleCloudProvider = googleCloudProvider
    this.googleApplicationName = googleApplicationName
    this.accountName = accountName
    this.region = region
    this.project = project
    this.compute = compute
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<GoogleServerGroup> serverGroups = getServerGroups()
    buildCacheResult(providerCache, serverGroups)
  }

  List<GoogleServerGroup> getServerGroups() {
    List<GoogleServerGroup> serverGroups = Collections.synchronizedList(new ArrayList<GoogleServerGroup>())

    BatchRequest migsRequest = buildBatchRequest()
    BatchRequest instanceGroupsRequest = buildBatchRequest()

    List<String> zones = compute.regions().get(project, region).execute().getZones().collect { Utils.getLocalName(it) }
    zones?.each { String zone ->
      MIGSCallback migsCallback = new MIGSCallback(serverGroups: serverGroups,
                                                   zone: zone,
                                                   instanceGroupsRequest: instanceGroupsRequest)
      compute.instanceGroupManagers().list(project, zone).queue(migsRequest, migsCallback)
    }
    executeIfRequestsAreQueued(migsRequest)
    executeIfRequestsAreQueued(instanceGroupsRequest)

    serverGroups
  }

  CacheResult buildCacheResult(ProviderCache providerCache, List<GoogleServerGroup> serverGroups) {

    def crb = new CacheResultBuilder();

    serverGroups.each { GoogleServerGroup serverGroup ->
      def names = Names.parseName(serverGroup.name)
      def applicationName = names.app
      def clusterName = names.cluster

      def serverGroupKey = Keys.getServerGroupKey(googleCloudProvider,
                                                  serverGroup.name,
                                                  accountName,
                                                  serverGroup.zones[0])
      def clusterKey = Keys.getClusterKey(googleCloudProvider,
                                          accountName,
                                          applicationName,
                                          clusterName)
      def appKey = Keys.getApplicationKey(googleCloudProvider, applicationName)
      def instanceKeys = []
      def loadBalancerKeys = []

      crb.namespace(APPLICATIONS.ns).get(appKey).with {
        attributes.name = applicationName
        relationships[CLUSTERS.ns].add(clusterKey)
      }

      crb.namespace(CLUSTERS.ns).get(clusterKey).with {
        attributes.name = clusterName
        attributes.accountName = accountName
        relationships[APPLICATIONS.ns].add(appKey)
        relationships[SERVER_GROUPS.ns].add(serverGroupKey)
      }

      serverGroup.instances.each { GoogleInstance partialInstance ->
        def instanceKey = Keys.getInstanceKey(googleCloudProvider,
                                              accountName,
                                              partialInstance.name)
        instanceKeys << instanceKey
        crb.namespace(INSTANCES.ns).get(instanceKey).with {
          relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        }
      }

      crb.namespace(SERVER_GROUPS.ns).get(serverGroupKey).with {
        attributes = objectMapper.convertValue(serverGroup, ATTRIBUTES)
        relationships[APPLICATIONS.ns].add(appKey)
        relationships[CLUSTERS.ns].add(clusterKey)
        relationships[INSTANCES.ns].addAll(instanceKeys)
      }

      serverGroup.asg.loadBalancerNames.each { String loadBalancerName ->
        loadBalancerKeys << Keys.getLoadBalancerKey(googleCloudProvider,
                                                    region,
                                                    accountName,
                                                    loadBalancerName)
      }

      loadBalancerKeys.each { String loadBalancerKey ->
        crb.namespace(LOAD_BALANCERS.ns).get(loadBalancerKey).with {
          relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        }
      }
    }

    log.info("Caching ${crb.namespace(APPLICATIONS.ns).size()} applications in ${agentType}")
    log.info("Caching ${crb.namespace(CLUSTERS.ns).size()} clusters in ${agentType}")
    log.info("Caching ${crb.namespace(SERVER_GROUPS.ns).size()} server groups in ${agentType}")
    log.info("Caching ${crb.namespace(INSTANCES.ns).size()} instance relationships in ${agentType}")
    log.info("Caching ${crb.namespace(LOAD_BALANCERS.ns).size()} load balancer relationships in ${agentType}")

    crb.build()
  }

  class MIGSCallback<InstanceGroupManagerList> extends JsonBatchCallback<InstanceGroupManagerList> {

    List<GoogleServerGroup> serverGroups
    String zone
    BatchRequest instanceGroupsRequest

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error e.getMessage()
    }

    @Override
    void onSuccess(InstanceGroupManagerList instanceGroupManagerList, HttpHeaders responseHeaders) throws IOException {
      instanceGroupManagerList?.items?.each { InstanceGroupManager instanceGroupManager ->
        def names = Names.parseName(instanceGroupManager.name)
        def appName = names.app.toLowerCase()

        if (appName) {
          def serverGroup = new GoogleServerGroup(
              name: instanceGroupManager.name,
              region: region,
              zones: [zone],
              launchConfig: [createdTime: Utils.getTimeFromTimestamp(instanceGroupManager.creationTimestamp)],
              asg: [minSize        : instanceGroupManager.targetSize,
                    maxSize        : instanceGroupManager.targetSize,
                    desiredCapacity: instanceGroupManager.targetSize])
          serverGroups << serverGroup

          // The isDisabled property of a server group is set based on whether there are associated target pools.
          def loadBalancerNames = Utils.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(instanceGroupManager.getTargetPools())
          serverGroup.setDisabled(loadBalancerNames.empty)

          InstanceGroupsCallback instanceGroupsCallback = new InstanceGroupsCallback(serverGroup: serverGroup)
          compute.instanceGroups().listInstances(project,
                                                 zone,
                                                 serverGroup.name,
                                                 new InstanceGroupsListInstancesRequest()).queue(instanceGroupsRequest,
                                                                                                 instanceGroupsCallback)

          String instanceTemplateName = Utils.getLocalName(instanceGroupManager.instanceTemplate)
          InstanceTemplatesCallback instanceTemplatesCallback = new InstanceTemplatesCallback(serverGroup: serverGroup)
          compute.instanceTemplates().get(project, instanceTemplateName).queue(instanceGroupsRequest,
                                                                               instanceTemplatesCallback)
        }
      }
    }
  }


  class InstanceGroupsCallback<InstanceGroupsListInstances> extends JsonBatchCallback<InstanceGroupsListInstances> {

    GoogleServerGroup serverGroup

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error e.getMessage()
    }

    @Override
    void onSuccess(InstanceGroupsListInstances instanceGroupsListInstances, HttpHeaders responseHeaders) throws IOException {
      instanceGroupsListInstances?.items?.each { InstanceWithNamedPorts instance ->
        serverGroup.instances << new GoogleInstance(name: Utils.getLocalName(instance.instance))
      }
    }
  }


  class InstanceTemplatesCallback<InstanceTemplate> extends JsonBatchCallback<InstanceTemplate> {

    private static final String LOAD_BALANCER_NAMES = "load-balancer-names"

    GoogleServerGroup serverGroup

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      log.error e.getMessage()
    }

    @Override
    void onSuccess(InstanceTemplate instanceTemplate, HttpHeaders responseHeaders) throws IOException {
      serverGroup.launchConfig.with {
        instanceTemplate = instanceTemplate
        launchConfigurationName = instanceTemplate?.name
        instanceType = instanceTemplate?.properties?.machineType
      }

      def sourceImageUrl = instanceTemplate?.properties?.disks?.find { disk ->
        disk.boot
      }?.initializeParams?.sourceImage
      if (sourceImageUrl) {
        serverGroup.launchConfig.imageId = Utils.getLocalName(sourceImageUrl)
      }

      def instanceMetadata = instanceTemplate?.properties?.metadata
      if (instanceMetadata) {
        def metadataMap = Utils.buildMapFromMetadata(instanceMetadata)
        def loadBalancerNameList = metadataMap?.get(LOAD_BALANCER_NAMES)?.split(",")
        if (loadBalancerNameList) {
          serverGroup.asg.loadBalancerNames = loadBalancerNameList
        }
      }

      // Find all firewall rules in this network with target tags matching the tags of this instance template.
      serverGroup.set("networkName", Utils.getNetworkNameFromInstanceTemplate(instanceTemplate))
      serverGroup.set("instanceTemplateTags", instanceTemplate?.properties?.tags?.items)
    }
  }
}
