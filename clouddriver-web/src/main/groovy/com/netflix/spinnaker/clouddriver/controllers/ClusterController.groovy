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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.*

import java.util.stream.Collectors
import java.util.stream.Stream

import static com.netflix.spinnaker.clouddriver.model.view.ModelObjectViewModelPostProcessor.applyExtensions
import static com.netflix.spinnaker.clouddriver.model.view.ModelObjectViewModelPostProcessor.applyExtensionsToObject

@Slf4j
@RestController
@RequestMapping("/applications/{application}/clusters")
class ClusterController {

  public static final Comparator<ServerGroup> OLDEST_TO_NEWEST = Comparator
    .comparingLong({ ServerGroup sg -> sg.getCreatedTime() })

  public static final Comparator<ServerGroup> BIGGEST_TO_SMALLEST = Comparator
    .comparingInt({ ServerGroup sg ->
      Optional.ofNullable(sg.getInstances()).map({ it.size() }).orElse(0)
    })
    .thenComparing(OLDEST_TO_NEWEST)
    .reversed()

  @Autowired
  List<ApplicationProvider> applicationProviders

  @Autowired
  List<ClusterProvider> clusterProviders

  @Autowired
  RequestQueue requestQueue

  @Autowired
  ServerGroupController serverGroupController

  @Autowired
  Optional<List<ClusterViewModelPostProcessor>> clusterExtensions = Optional.empty()

  @Autowired
  Optional<List<ServerGroupViewModelPostProcessor>> serverGroupExtensions = Optional.empty()

  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission() and hasPermission(#application, 'APPLICATION', 'READ')")
  @PostAuthorize("@authorizationSupport.filterForAccounts(returnObject)")
  @RequestMapping(method = RequestMethod.GET)
  Map<String, Set<String>> listByAccount(@PathVariable String application) {
    List<Application> apps = applicationProviders.stream()
      .map({ it.getApplication(application) })
      .filter({ it != null })
      .sorted(Comparator.comparing({ Application it -> it.getName().toLowerCase() }))
      .collect(Collectors.toList())

    Map<String, Set<String>> clusterNames = mergeClusters(apps)
    return clusterNames
  }

  private Map<String, Set<String>> mergeClusters(List<Application> a) {
    Map<String, Set<String>> map = new HashMap<>()

    a.stream()
      .flatMap({ it.getClusterNames().entrySet().stream() })
      .forEach({ entry ->
        map.computeIfAbsent(entry.getKey(), { new HashSet<>() }).addAll(entry.getValue())
      })
    return map
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}", method = RequestMethod.GET)
  Set<ClusterViewModel> getForAccount(@PathVariable String application, @PathVariable String account) {

    Set<ClusterViewModel> clusters = clusterProviders.stream()
      .map({ it.getClusters(application, account, false) })
      .filter({ it != null })
      .flatMap({
        applyExtensions(clusterExtensions, it).stream()
      })
      .map({ Cluster cluster -> ClusterViewModel.from(cluster) })
      .collect(Collectors.toSet())
    if (clusters.isEmpty()) {
      throw new NotFoundException("No clusters found (application: ${application}, account: ${account})")
    }
    clusters
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{name:.+}", method = RequestMethod.GET)
  Set<Cluster> getForAccountAndName(@PathVariable String application,
                                    @PathVariable String account,
                                    @PathVariable String name,
                                    @RequestParam(required = false, value = "expand", defaultValue = "true") boolean expand) {
    def clusters = clusterProviders.stream()
      .map({ provider ->
        applyExtensionsToObject(clusterExtensions,
          requestQueue.execute(application, { provider.getCluster(application, account, name, expand) }))
      })
      .filter({ it != null })
      .collect(Collectors.toSet())

    if (clusters.isEmpty()) {
      throw new NotFoundException(String.format(
        "Cluster not found (application: %s, account: %s, name: %s)", application, account, name))
    }
    return clusters
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{name:.+}/{type}", method = RequestMethod.GET)
  Cluster getForAccountAndNameAndType(@PathVariable String application,
                                      @PathVariable String account,
                                      @PathVariable String name,
                                      @PathVariable String type,
                                      @RequestParam(required = false, value = "expand", defaultValue = "true") boolean expand) {

    def clusterProvider = clusterProviders.find { it.cloudProviderId == type }
    if (!clusterProvider) {
      throw new NotFoundException("No cluster provider of type: ${type} found that can handle cluster: ${name} in application: ${application}, account: ${account}")
    }

    Cluster cluster = applyExtensionsToObject(clusterExtensions,
      requestQueue.execute(application, { clusterProvider.getCluster(application, account, name, expand) })
    )

    if (!cluster) {
      throw new NotFoundException(String.format(
        "No clusters found (application: %s, account: %s, type: %s)", application, account, type))
    }
    return cluster
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{clusterName:.+}/{type}/serverGroups", method = RequestMethod.GET)
  Set<ServerGroup> getServerGroups(@PathVariable String application,
                                   @PathVariable String account,
                                   @PathVariable String clusterName,
                                   @PathVariable String type,
                                   @RequestParam(value = "region", required = false) String region,
                                   @RequestParam(required = false, value = "expand", defaultValue = "true") boolean expand) {
    Cluster cluster = applyExtensionsToObject(clusterExtensions, getForAccountAndNameAndType(application, account, clusterName, type, expand))

    Stream<ServerGroup> serverGroups = cluster.getServerGroups().stream()

    if (!StringUtils.isEmpty(region)) {
      serverGroups = serverGroups.filter({ region.equals(it.getRegion()) })
    }

    def result = serverGroups
      .map({ applyExtensionsToObject(serverGroupExtensions, it) })
      .collect(Collectors.toSet())

    return result
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{clusterName:.+}/{type}/serverGroups/{serverGroupName:.+}", method = RequestMethod.GET)
  def getServerGroup(@PathVariable String application,
                     @PathVariable String account,
                     @PathVariable String clusterName,
                     @PathVariable String type,
                     @PathVariable String serverGroupName,
                     @RequestParam(value = "region", required = false) String region) {
    // we can optimize loads iff the cloud provider supports loading minimal clusters (ie. w/o instances)
    def providers = providers(type)

    if (providers.isEmpty()) {
      log.warn("No cluster provider found for type (type: {}, account: {})", type, account)
    }

    List<ServerGroup> serverGroups = providers.stream()
      .flatMap({ p ->
        boolean isExpanded = !p.supportsMinimalClusters()
        Stream<ServerGroup> serverGroups =
          applyExtensions(serverGroupExtensions, getServerGroups(application, account, clusterName, type, region, isExpanded))
            .stream()
            .filter({
              serverGroupName.equals(it.getName()) &&
                (StringUtils.isEmpty(region) || region.equals(it.getRegion()))
            })

        return isExpanded
          ? serverGroups
          : serverGroups
          .map({ ServerGroup sg ->
            return serverGroupController.getServerGroupByApplication(application, account, sg.getRegion(), sg.getName(), "true")
          })
      })
      .collect(Collectors.toList())

    if (serverGroups.isEmpty()) {
      throw new NotFoundException(String.format("Server group not found (account: %s, name: %s, type: %s)", account, serverGroupName, type))
    }

    // TODO: maybe break up this API into 2 different routes instead of returning 2 types
    return StringUtils.isEmpty(region)
      ? serverGroups
      : serverGroups.get(0)
  }

  private List<ClusterProvider> providers(String cloudProvider) {
    return clusterProviders.stream()
      .filter({ cloudProvider.equals(it.getCloudProviderId()) })
      .collect(Collectors.toList())
  }

  /**
   * @param scope Should be either a region or zone, depending on the cloud provider.
   * @return A dynamically determined server group using a {@code TargetServerGroup} specifier.
   */
  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{clusterName:.+}/{cloudProvider}/{scope}/serverGroups/target/{target:.+}", method = RequestMethod.GET)
  ServerGroup getTargetServerGroup(@PathVariable String application,
                                   @PathVariable String account,
                                   @PathVariable String clusterName,
                                   @PathVariable String cloudProvider,
                                   @PathVariable String scope,
                                   @PathVariable String target,
                                   @RequestParam(value = "onlyEnabled", required = false, defaultValue = "false") String onlyEnabled,
                                   @RequestParam(value = "validateOldest", required = false, defaultValue = "true") String validateOldest) {
    TargetServerGroup tsg
    try {
      tsg = TargetServerGroup.fromString(target)
    } catch (IllegalArgumentException e) {
      throw new NotFoundException(String.format("Target not found (target: %s)", target))
    }

    // we can optimize loads iff the cloud provider supports loading minimal clusters (ie. w/o instances)
    def providers = providers(cloudProvider)
    if (providers.isEmpty()) {
      log.warn("No cluster provider found for cloud provider (cloudProvider: {}, account: {})", cloudProvider, account)
    }

    boolean enabledOnly = Boolean.parseBoolean(onlyEnabled)

    Set<ServerGroup> alreadyExpanded = new HashSet<>()

    // load all server groups w/o instance details (this is reasonably efficient)
    Stream<ServerGroup> filteredServerGroups = providers.stream()
      .flatMap({ p ->
        boolean isExpanded = !p.supportsMinimalClusters()
        Stream<ServerGroup> serverGroups = getServerGroups(application, account, clusterName, cloudProvider, null /* region */, isExpanded)
          .stream()
          .filter({
            boolean scopeMatch = scope.equals(it.getRegion()) ||
              Optional.ofNullable(it.getZones())
                .map({ it.contains(scope) })
                .orElse(false)

            boolean enableMatch = enabledOnly ? !it.isDisabled() : true

            return scopeMatch && enableMatch
          })
          .map({ serverGroup ->
            if (isExpanded) {
              alreadyExpanded.add(serverGroup) // this is kind of gross
            }
            return serverGroup
          })

        return serverGroups
      })
      .filter({ it.getCreatedTime() != null })

    Optional<ServerGroup> maybe = Optional.empty()

    switch (tsg) {
      case TargetServerGroup.CURRENT:
        maybe = filteredServerGroups
          .sorted(OLDEST_TO_NEWEST.reversed())
          .findFirst()
        break
      case TargetServerGroup.PREVIOUS:
        def serverGroups = filteredServerGroups
          .sorted(OLDEST_TO_NEWEST.reversed())
          .limit(2)
          .collect(Collectors.toList())
        if (serverGroups.size() == 1) {
          throw new NotFoundException("Target not found (target: ${target})")
        } else if (serverGroups.size() > 1) {
          maybe = Optional.of(serverGroups.get(1))
        }
        break
      case TargetServerGroup.OLDEST:
        // At least two expected, but some cases just want the oldest no matter what.
        boolean validate = Boolean.parseBoolean(validateOldest)
        def serverGroups = filteredServerGroups
          .sorted(OLDEST_TO_NEWEST)
          .limit(2)
          .collect(Collectors.toList())
        if (validate && serverGroups.size() == 1) {
          throw new NotFoundException(String.format("Target not found (target: %s)", target))
        }
        maybe = Optional.of(serverGroups.get(0))
        break
      case TargetServerGroup.LARGEST:
        // Choose the server group with the most instances, falling back to newest in the case of a tie.
        maybe = filteredServerGroups
          .sorted(BIGGEST_TO_SMALLEST)
          .findFirst()
        break
      case TargetServerGroup.FAIL:
        def serverGroups = filteredServerGroups.collect(Collectors.toList())
        if (serverGroups.size() > 1) {
          String names = serverGroups.stream()
            .map({ it.getName() })
            .collect(Collectors.joining(", "))
          throw new NotFoundException(String.format("More than one target found (scope: %s, serverGroups: %s)", scope, names))
        }
        maybe = serverGroups.size() == 1 ? Optional.of(serverGroups.get(0)) : Optional.empty()
    }

    ServerGroup result = maybe
      .map({ ServerGroup serverGroup ->
        if (alreadyExpanded.contains(serverGroup)) {
          // server group was already expanded on initial load
          return serverGroup
        }
        return serverGroupController.getServerGroupByApplication(application, account, serverGroup.getRegion(), serverGroup.getName(), "true")
      })
      .orElseThrow({
        new NotFoundException(String.format(
          "No server groups found (account: %s, location: %s, cluster: %s, type: %S)", account, scope, clusterName, cloudProvider))
      })
    return result
  }

  @PreAuthorize("hasPermission(#application, 'APPLICATION', 'READ') && hasPermission(#account, 'ACCOUNT', 'READ')")
  @RequestMapping(value = "/{account:.+}/{clusterName:.+}/{cloudProvider}/{scope}/serverGroups/target/{target:.+}/{summaryType:.+}", method = RequestMethod.GET)
  Summary getServerGroupSummary(@PathVariable String application,
                                @PathVariable String account,
                                @PathVariable String clusterName,
                                @PathVariable String cloudProvider,
                                @PathVariable String scope,
                                @PathVariable String target,
                                @PathVariable String summaryType,
                                @RequestParam(value = "onlyEnabled", required = false, defaultValue = "false") String onlyEnabled) {
    ServerGroup sg = getTargetServerGroup(application,
      account,
      clusterName,
      cloudProvider,
      scope,
      target,
      onlyEnabled,
      "false" /* validateOldest */)

    if ("image".equalsIgnoreCase(summaryType)) {
      return sg.getImageSummary()
    } else if ("images".equalsIgnoreCase(summaryType)) {
      return sg.getImagesSummary()
    } else {
      String method = "get" + StringUtils.capitalize(summaryType) + "Summary"
      try {
        // TODO: this is gross, is it used for anything besides ImageSummary?
        log.warn("Getting summary (type: {}) may be removed unless explicit support is added", summaryType)
        return (Summary) sg.getClass().getMethod(method).invoke(sg)
      } catch (ReflectiveOperationException e) {
        throw new NotFoundException(String.format("Summary not found (type: %s)", summaryType))
      }
    }
  }

  @Canonical
  static class ClusterViewModel {
    String name
    String account
    Moniker moniker
    List<String> loadBalancers
    List<String> serverGroups

    static ClusterViewModel from(Cluster cluster) {
      def result = new ClusterViewModel()
      result.setName(cluster.getName())
      result.setAccount(cluster.getAccountName())
      result.setMoniker(cluster.getMoniker())
      result.setLoadBalancers(cluster.getLoadBalancers().stream()
        .map({ it.getName() })
        .collect(Collectors.toList()))
      result.setServerGroups(cluster.getServerGroups().stream()
        .map({ it.getName() })
        .collect(Collectors.toList()))
      return result
    }
  }
}
