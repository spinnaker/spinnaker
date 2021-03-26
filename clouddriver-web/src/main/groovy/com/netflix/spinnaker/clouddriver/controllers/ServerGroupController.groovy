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

package com.netflix.spinnaker.clouddriver.controllers

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.model.*
import com.netflix.spinnaker.clouddriver.model.view.ClusterViewModelPostProcessor
import com.netflix.spinnaker.clouddriver.model.view.ServerGroupViewModelPostProcessor
import com.netflix.spinnaker.clouddriver.requestqueue.RequestQueue
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.Canonical
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

import java.util.stream.Collectors
import java.util.stream.Stream

import static com.netflix.spinnaker.clouddriver.model.view.ModelObjectViewModelPostProcessor.applyExtensions
import static com.netflix.spinnaker.clouddriver.model.view.ModelObjectViewModelPostProcessor.applyExtensionsToObject

@Slf4j
@RestController
class ServerGroupController {

  private static final String INSTANCE_LOAD_BALANCER_HEALTH_TYPE = "LoadBalancer"
  private static final String INSTANCE_TARGET_GROUP_HEALTH_TYPE = "TargetGroup"

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  RequestQueue requestQueue

  @Autowired
  Optional<List<ClusterViewModelPostProcessor>> clusterViewModelPostProcessors = Optional.empty()

  @Autowired
  Optional<List<ServerGroupViewModelPostProcessor>> serverGroupViewModelPostProcessors = Optional.empty()

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @PostAuthorize("hasPermission(returnObject?.moniker?.app, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/applications/{application}/serverGroups/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  ServerGroup getServerGroupByApplication(@PathVariable String application, // needed to not break api
                                          @PathVariable String account,
                                          @PathVariable String region,
                                          @PathVariable() String name,
                                          @RequestParam(required = false, value = "includeDetails", defaultValue = "true") String includeDetails
  ) {
    return getServerGroup(account, region, name, includeDetails)
  }

  @PreAuthorize("hasPermission(#account, 'ACCOUNT', 'READ')")
  @PostAuthorize("hasPermission(returnObject?.moniker?.app, 'APPLICATION', 'READ')")
  @RequestMapping(value = "/serverGroups/{account}/{region}/{name:.+}", method = RequestMethod.GET)
  // TODO: /application and /serverGroup endpoints should be in their own controllers. See https://github.com/spinnaker/spinnaker/issues/2023
  ServerGroup getServerGroupByMoniker(@PathVariable String account,
                                      @PathVariable String region,
                                      @PathVariable String name,
                                      @RequestParam(required = false, value = "includeDetails", defaultValue = "true") String includeDetails) {
    return getServerGroup(account, region, name, includeDetails)
  }

  private ServerGroup getServerGroup(String account,
                                     String region,
                                     String name,
                                     String includeDetails) {

    Boolean shouldIncludeDetails = Boolean.valueOf(includeDetails)

    ServerGroup serverGroup = clusterProviders.stream()
      .map({ provider ->
        requestQueue.execute(name, { -> provider.getServerGroup(account, region, name, shouldIncludeDetails) })
      })
      .filter({ Objects.nonNull(it) })
      .findFirst()
      .orElseThrow({
        new NotFoundException(String.format("Server group not found (account: %s, region: %s, name: %s)", account, region, name))
      })

    return applyExtensionsToObject(serverGroupViewModelPostProcessors, serverGroup)
  }

  private List<Map<String, Object>> expandedList(String application, String cloudProvider) {
    return clusterProviders.stream()
      .filter({
        cloudProvider != null
          ? cloudProvider.equalsIgnoreCase(it.getCloudProviderId())
          : true
      })
      .flatMap({ ClusterProvider cp ->
        def details = requestQueue.execute(application, { cp.getClusterDetails(application) })

        Optional.ofNullable(details)
          .map({
            it.values().stream()
              .filter({ Objects.nonNull(it) })
              .flatMap({ it.stream() })
              .filter({ Objects.nonNull(it) })
              .map( { cluster ->
                applyExtensionsToObject(clusterViewModelPostProcessors, cluster)
              })
          })
          .orElse(Stream.empty())
      })
      .flatMap({ Cluster c ->
        Optional.ofNullable(c.getServerGroups())
          .map({ groups ->
            groups.stream()
              .map({ serverGroup ->
                applyExtensionsToObject(serverGroupViewModelPostProcessors, serverGroup)
              })
              .map({ serverGroup ->
                expanded(serverGroup, c)
              })
          })
          .orElse(Stream.empty())
      })
      .collect(Collectors.toList())
  }

  private Map<String, Object> expanded(ServerGroup serverGroup, Cluster cluster) {
    Map<String, Object> sg = objectMapper.convertValue(serverGroup, Map)
    sg.put("accountName", cluster.getAccountName())
    Moniker moniker = cluster.getMoniker()
    sg.put("cluster", moniker.getCluster())
    sg.put("application", moniker.getApp())
    sg.put("stack", moniker.getStack())
    sg.put("freeFormDetail", moniker.getDetail())
    sg.put("account", cluster.getAccountName())
    return sg
  }

  private List<ServerGroupViewModel> summaryList(String application, String cloudProvider) {

    List<ServerGroupViewModel> serverGroupViews = clusterProviders.stream()
      .filter({
        cloudProvider != null
          ? cloudProvider.equalsIgnoreCase(it.getCloudProviderId())
          : true
      })
      .flatMap({ provider ->
        Map<String, Set<Cluster>> clusterMap = requestQueue.execute(application, {
          provider.getClusterDetails(application)
        })

        return Optional.ofNullable(clusterMap)
          .map({ it.values() })
          .map({ it.stream().flatMap({ it.stream() }) })
          .orElse(Stream.empty())
      })
      .flatMap({ Cluster cluster ->
        cluster.getServerGroups().stream()
          .map({ serverGroup ->
            new ServerGroupViewModel(applyExtensionsToObject(serverGroupViewModelPostProcessors, serverGroup), cluster.name, cluster.accountName)
          })

      })
      .collect(Collectors.toList())

    return serverGroupViews
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/applications/{application}/serverGroups", method = RequestMethod.GET)
  List<Object> list(@PathVariable String application,
                    @RequestParam(required = false, value = "expand", defaultValue = "false") String expand,
                    @RequestParam(required = false, value = "cloudProvider") String cloudProvider,
                    @RequestParam(required = false, value = "clusters") List<String> clusters) {

    boolean isExpanded = Boolean.valueOf(expand)

    if (clusters != null && !clusters.isEmpty()) {
      return buildSubsetForClusters(clusters, application, isExpanded)
    }
    if (clusters != null) {
      return List.of()
    }
    if (isExpanded) {
      return expandedList(application, cloudProvider)
    }
    return summaryList(application, cloudProvider)
  }

  @PostFilter("hasPermission(filterObject?.application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(value = "/serverGroups", method = RequestMethod.GET)
  List<ServerGroupViewModel> getServerGroups(
    @RequestParam(required = false, value = "applications") List<String> applications,
    @RequestParam(required = false, value = "ids") List<String> ids,
    @RequestParam(required = false, value = "cloudProvider") String cloudProvider) {

    boolean hasApplications = applications != null && !applications.isEmpty()
    boolean hasIds = ids != null && !ids.isEmpty()
    if ((hasApplications && hasIds) || (!hasApplications && !hasIds)) {
      throw new IllegalArgumentException("Provide either 'applications' or 'ids' parameter (but not both)")
    }

    if (hasApplications) {
      return getServerGroupsForApplications(applications, cloudProvider)
    } else {
      return getServerGroupsForIds(ids)
    }
  }

  private List<ServerGroupViewModel> getServerGroupsForApplications(List<String> applications, String cloudProvider) {
    return applications.stream()
      .flatMap({ it -> summaryList(it, cloudProvider).stream() })
      .collect(Collectors.toList())
  }

  private List<ServerGroupViewModel> getServerGroupsForIds(List<String> serverGroupIds) {
    List<String[]> allIdTokens = serverGroupIds.stream()
      .map({ it.split(":") })
      .collect(Collectors.toList())

    String invalidIds = allIdTokens.stream()
      .filter({ it.length != 3 })
      .map({ String.join(":", it) })
      .collect(Collectors.joining(", "))

    if (!invalidIds.isBlank()) {
      throw new IllegalArgumentException("Expected ids in the format <account>:<region>:<name> but got invalid ids: " +
        invalidIds)
    }

    allIdTokens.stream()
      .map({ idTokens ->
        String account = idTokens[0]
        String region = idTokens[1]
        String name = idTokens[2]
        try {
          ServerGroup serverGroup = getServerGroup(account, region, name, "true")
          return new ServerGroupViewModel(serverGroup, serverGroup.getMoniker().getCluster(), account)
        } catch (e) {
          log.error("Couldn't get server group {}:{}:{}", account, region, name, e)
          return null
        }
      })
      .filter({ Objects.nonNull(it) })
      .collect(Collectors.toList())
  }

  private List<Object> buildSubsetForClusters(List<String> clusters, String application, boolean isExpanded) {
    List<Cluster> matches = clusters.stream()
      .flatMap({ accountAndName ->
        String[] components = accountAndName.split(":")
        if (components.length == 2) {
          String account = components[0]
          String clusterName = components[1]
          if (!account.isEmpty() && !clusterName.isEmpty()) {
            return clusterProviders.stream()
              .map({ clusterProvider ->
                Cluster cluster = requestQueue.execute(application, {
                  clusterProvider.getCluster(application, account, clusterName)
                })
                return applyExtensionsToObject(clusterViewModelPostProcessors, cluster)
              })
          }
        }
        return null
      })
      .filter({ it != null })
      .collect(Collectors.toList())

    return matches.stream()
      .flatMap({ cluster ->
        cluster.getServerGroups().stream()
          .map({
            ServerGroup sg = applyExtensionsToObject(serverGroupViewModelPostProcessors, it)
            isExpanded
              ? expanded(sg, cluster)
              : new ServerGroupViewModel(sg, cluster.name, cluster.accountName)
          })
      })
      .collect(Collectors.toList())
  }

  static class ServerGroupViewModel {
    String name
    String account
    String region
    String cluster
    String vpcId
    String type
    String cloudProvider
    String instanceType
    String application
    Boolean isDisabled
    Moniker moniker
    Map buildInfo
    Long createdTime

    ServerGroup.Capacity capacity

    List<InstanceViewModel> instances
    Set<String> loadBalancers
    Set<String> targetGroups
    Set<String> securityGroups
    ServerGroup.InstanceCounts instanceCounts
    Map<String, Object> tags
    Map<String, String> labels
    Map providerMetadata
    List<ServerGroupManager.ServerGroupManagerSummary> serverGroupManagers

    @JsonIgnore
    Map<String, Object> extraAttributes = new HashMap<>()

    @JsonAnyGetter
    Map<String, Object> getExtraAttributes() {
      return extraAttributes
    }

    ServerGroupViewModel(ServerGroup serverGroup, String clusterName, String accountName) {
      def instanceViews = Optional.ofNullable(serverGroup.getInstances())
        .map({ instances ->
          instances.stream()
            .filter({ it != null })
            .map({ new InstanceViewModel(it) })
            .collect(Collectors.toList())
        })
        .orElse(List.of())


      cluster = clusterName
      type = serverGroup.getType()
      cloudProvider = serverGroup.getCloudProvider()
      name = serverGroup.getName()
      application = Names.parseName(serverGroup.getName()).getApp()
      account = accountName
      region = serverGroup.getRegion()
      createdTime = serverGroup.getCreatedTime()
      isDisabled = serverGroup.isDisabled()
      instances = instanceViews
      instanceCounts = serverGroup.getInstanceCounts()
      securityGroups = serverGroup.getSecurityGroups()
      loadBalancers = serverGroup.getLoadBalancers()
      serverGroupManagers = serverGroup.getServerGroupManagers()
      instanceType = serverGroup.getInstanceType()
      moniker = serverGroup.getMoniker()

      def tags = serverGroup.getTags()
      if (tags != null && !tags.isEmpty()) {
        this.tags = tags
      }

      def labels = serverGroup.getLabels()
      if (labels != null && !labels.isEmpty()) {
        this.labels = labels
      }

      // TODO: deal with duck typing
      if (serverGroup.hasProperty("buildInfo")) {
        buildInfo = serverGroup.buildInfo
      }
      if (serverGroup.hasProperty("vpcId")) {
        vpcId = serverGroup.vpcId
      }
      if (serverGroup.hasProperty("providerMetadata")) {
        providerMetadata = serverGroup.providerMetadata
      }
      if (serverGroup.hasProperty("targetGroups")) {
        targetGroups = serverGroup.targetGroups
      }

      capacity = serverGroup.getCapacity()

      Optional.ofNullable(serverGroup.extraAttributes).ifPresent { extraAttributes.putAll(it) }
    }
  }

  static class InstanceViewModel {
    String id
    String name
    List<Health> health
    String healthState
    Long launchTime
    String availabilityZone

    InstanceViewModel(Instance instance) {
      id = instance.name
      name = instance.humanReadableName
      healthState = instance.getHealthState().toString()
      launchTime = instance.getLaunchTime()
      availabilityZone = instance.getZone()
      health = instance.health.collect { health ->

        String type = (String) health.get("type")

        String healthState = Optional.ofNullable(health.get("state"))
          .map({ it.toString() })
          .orElse(null)

        Object status = health.get("status")
        if (type == INSTANCE_LOAD_BALANCER_HEALTH_TYPE && health.containsKey("loadBalancers")) {
          List<HealthDetail> loadBalancers

          Object lbs = health.get("loadBalancers")
          if (lbs instanceof Collection) {
            loadBalancers = lbs.stream()
              .map({
                // TODO: deal with duck typing
                new HealthDetail(it.loadBalancerName, it.state, it.description, it.healthState)
              })
              .collect(Collectors.toList())
          }

          def metric = new LoadBalancerHealth()
          metric.setType(type)
          metric.setState(healthState)
          metric.setStatus(status)
          metric.setLoadBalancers(loadBalancers)
          return metric
        } else if (type == INSTANCE_TARGET_GROUP_HEALTH_TYPE && health.containsKey("targetGroups")) {
          List<HealthDetail> targetGroups

          Object tgs = health.get("targetGroups")
          if (tgs instanceof Collection) {
            targetGroups = tgs.stream()
              .map({
                // TODO: deal with duck typing
                new HealthDetail(it.targetGroupName, it.state, it.description, it.healthState)
              })
              .collect(Collectors.toList())
          }

          def metric = new TargetGroupHealth()
          metric.setType(type)
          metric.setState(healthState)
          metric.setStatus(status)
          metric.setTargetGroups(targetGroups)
          return metric
        } else {
          return new Health(type, healthState, status)
        }
      }
    }
  }

  @Canonical
  static class Health {
    String type
    String state
    Object status
  }

  static class LoadBalancerHealth extends Health {
    List<HealthDetail> loadBalancers
  }

  static class TargetGroupHealth extends Health {
    List<HealthDetail> targetGroups
  }

  @Canonical
  static class HealthDetail {
    Object name
    Object state
    Object description
    Object healthState
  }
}
