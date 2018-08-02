/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model.callbacks

import com.google.api.services.compute.model.Metadata
import com.google.api.services.compute.model.PathMatcher
import com.google.api.services.compute.model.PathRule
import com.google.api.services.compute.model.UrlMap
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import groovy.util.logging.Slf4j
import org.springframework.util.ClassUtils

import java.text.SimpleDateFormat

@Slf4j
class Utils {
  public static final String TARGET_POOL_NAME_PREFIX = "tp"
  public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX"

  static long getTimeFromTimestamp(String timestamp) {
    if (timestamp) {
      return new SimpleDateFormat(SIMPLE_DATE_FORMAT).parse(timestamp).getTime()
    } else {
      return System.currentTimeMillis()
    }
  }

  /**
   * Return a single port string if a port range refers to a single port (e.g. 80-80).
   *
   * @param portRange - Port range to parse.
   * @return - Single port if the ports in the port range are the same.
   */
  static String derivePortOrPortRange(String portRange) {
    if (!portRange || !portRange.contains('-')) {
      return portRange
    }
    def tokens = portRange.split('-')
    if (tokens.length != 2) {
      throw new IllegalArgumentException("Port range ${portRange} formatted improperly.")
    }

    tokens[0] != tokens[1] ? portRange : tokens[0]
  }

  static String getLocalName(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    int lastIndex = fullUrl.lastIndexOf('/')

    return lastIndex != -1 ? fullUrl.substring(lastIndex + 1) : fullUrl
  }

  static GoogleTargetProxyType getTargetProxyType(String fullUrl) {
    if (!fullUrl) {
      throw new IllegalArgumentException("Target proxy url ${fullUrl} malformed.")
    }

    int lastIndex = fullUrl.lastIndexOf('/')
    if (lastIndex == -1) {
      throw new IllegalArgumentException("Target proxy url ${fullUrl} malformed.")
    }
    String withoutName = fullUrl.substring(0, lastIndex)
    switch (getLocalName(withoutName)) {
      case 'targetHttpProxies':
        return GoogleTargetProxyType.HTTP
        break
      case 'targetHttpsProxies':
        return GoogleTargetProxyType.HTTPS
        break
      case 'targetSslProxies':
        return GoogleTargetProxyType.SSL
        break
      case 'targetTcpProxies':
        return GoogleTargetProxyType.TCP
        break
      default:
        throw new IllegalArgumentException("Target proxy url ${fullUrl} has unknown type.")
        break
    }
  }

  static String getZoneFromInstanceUrl(String fullUrl) {
    def zones = "zones/"
    fullUrl.substring(fullUrl.indexOf(zones) + zones.length(),
                      fullUrl.indexOf("instances/") - 1)
  }

  static String getHealthCheckType(String fullUrl) {
    if (!fullUrl) {
      throw new IllegalArgumentException("Health check url ${fullUrl} malformed.")
    }

    int lastIndex = fullUrl.lastIndexOf('/')
    if (lastIndex == -1) {
      throw new IllegalArgumentException("Health check url ${fullUrl} malformed.")
    }
    String withoutName = fullUrl.substring(0, lastIndex)
    return getLocalName(withoutName)
  }

  // TODO(duftler): Consolidate this method with the same one from kato/GCEUtil and move to a common library.
  static Map<String, String> buildMapFromMetadata(Metadata metadata) {
    metadata.items?.collectEntries { Metadata.Items metadataItems ->
      [(metadataItems.key): metadataItems.value]
    }
  }

  /**
   * Parses region from a full server group Url of the form:
   *
   * "https://www.googleapis.com/compute/v1/projects/$projectName/zones/$zone/instanceGroups/$serverGroupName"
   * OR
   * "https://www.googleapis.com/compute/v1/projects/$projectName/regions/$region/instanceGroups/$serverGroupName"
   */
  static String getRegionFromGroupUrl(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    def urlParts = fullUrl.split("/")

    if (urlParts.length < 4) {
      throw new IllegalArgumentException("Server group Url ${fullUrl} malformed.")
    }

    String regionsOrZones = urlParts[urlParts.length - 4]
    switch (regionsOrZones) {
      case "regions":
        return urlParts[urlParts.length - 3]
        break
      case "zones":
        def zone = urlParts[urlParts.length - 3]
        def lastDash = zone.lastIndexOf("-")
        return zone.substring(0, lastDash)
        break
      default:
        throw new IllegalArgumentException("Server group Url ${fullUrl} malformed.")
        break
    }
  }

  static String getZoneFromGroupUrl(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    def urlParts = fullUrl.split("/")

    if (urlParts.length < 4) {
      throw new IllegalArgumentException("Server group url ${fullUrl} malformed.")
    }

    String regionsOrZones = urlParts[urlParts.length - 4]
    switch (regionsOrZones) {
      case "regions":
        throw new IllegalArgumentException("Can't parse a zone from regional group url ${fullUrl}.")
        break
      case "zones":
        return urlParts[urlParts.length - 3]
        break
      default:
        throw new IllegalArgumentException("Server group url ${fullUrl} malformed.")
        break
    }
  }

  /**
   * Determine if a server group is regional or zonal from the fullUrl.
   * @param fullUrl
   * @return Type of server group.
   */
  static GoogleServerGroup.ServerGroupType determineServerGroupType(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    def urlParts = fullUrl.split("/")

    if (urlParts.length < 4) {
      throw new IllegalArgumentException("Server group Url ${fullUrl} malformed.")
    }

    String regionsOrZones = urlParts[urlParts.length - 4]
    switch (regionsOrZones) {
      case 'regions':
        return GoogleServerGroup.ServerGroupType.REGIONAL
        break
      case 'zones':
        return GoogleServerGroup.ServerGroupType.ZONAL
        break
      default:
        throw new IllegalArgumentException("Server group Url ${fullUrl} malformed.")
        break
    }
    return regionsOrZones
  }

  // TODO(duftler): Consolidate this method with the same one from kato/GCEUtil and move to a common library.
  static List<String> deriveNetworkLoadBalancerNamesFromTargetPoolUrls(List<String> targetPoolUrls) {
    if (targetPoolUrls) {
      return targetPoolUrls.collect { targetPoolUrl ->
        def targetPoolLocalName = getLocalName(targetPoolUrl)

        targetPoolLocalName.split("-$TARGET_POOL_NAME_PREFIX-")[0]
      }
    } else {
      return []
    }
  }

  static Object getImmutableCopy(def value) {
    def valueClass = value.getClass()

    if (ClassUtils.isPrimitiveOrWrapper(valueClass) || valueClass == String.class) {
      return value
    } else if (value instanceof Cloneable) {
      return value.clone()
    } else if (value) {
      return value.toString()
    } else {
      return null
    }
  }

  static List<GoogleBackendService> getBackendServicesFromHttpLoadBalancerView(GoogleHttpLoadBalancer.View googleLoadBalancer) {
    List<GoogleBackendService> backendServices = [googleLoadBalancer.defaultService]
    List<GooglePathMatcher> pathMatchers = googleLoadBalancer?.hostRules?.collect { GoogleHostRule hostRule -> hostRule.pathMatcher }
    pathMatchers?.each { GooglePathMatcher pathMatcher ->
      backendServices << pathMatcher.defaultService
      pathMatcher?.pathRules?.each { GooglePathRule googlePathRule ->
        backendServices << googlePathRule.backendService
      }
    }
    return backendServices
  }

  static List<String> getBackendServicesFromUrlMap(UrlMap urlMap) {
    List<String> backendServices = [GCEUtil.getLocalName(urlMap.defaultService)]
    urlMap?.pathMatchers?.each { PathMatcher pathMatcher ->
      backendServices << GCEUtil.getLocalName(pathMatcher.defaultService)
      pathMatcher?.pathRules?.each { PathRule pathRule ->
        backendServices << GCEUtil.getLocalName(pathRule.service)
      }
    }
    return backendServices
  }

  static boolean determineHttpLoadBalancerDisabledState(GoogleHttpLoadBalancer loadBalancer,
                                                        GoogleServerGroup serverGroup) {
    def httpLoadBalancersFromMetadata = serverGroup.asg.get(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)
    def backendServicesFromMetadata = serverGroup.asg.get(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)
    List<List<GoogleLoadBalancedBackend>> serviceBackends = getBackendServicesFromHttpLoadBalancerView(loadBalancer.view)
        .findAll { it && it.name in backendServicesFromMetadata }
        .collect { it.backends }
    List<String> backendGroupNames = serviceBackends.flatten()
        .findAll { serverGroup.region == Utils.getRegionFromGroupUrl(it.serverGroupUrl) }
        .collect { GCEUtil.getLocalName(it.serverGroupUrl) }

    return loadBalancer.name in httpLoadBalancersFromMetadata && !(serverGroup.name in backendGroupNames)
  }

  static String decorateXpnResourceIdIfNeeded(String managedProjectId, String xpnResource) {
    if (!xpnResource) {
      return xpnResource
    }
    def xpnResourceProject = GCEUtil.deriveProjectId(xpnResource)
    def xpnResourceId = GCEUtil.getLocalName(xpnResource)

    if (xpnResourceProject != managedProjectId) {
      xpnResourceId = "$xpnResourceProject/$xpnResourceId"
    }

    return xpnResourceId
  }

  static boolean determineInternalLoadBalancerDisabledState(GoogleInternalLoadBalancer loadBalancer,
                                                            GoogleServerGroup serverGroup) {
    def regionalLoadBalancersFromMetadata = serverGroup.asg.get(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES)

    if (loadBalancer.backendService == null) {
      log.warn("Malformed internal load balancer encountered: ${loadBalancer}")
    }
    List<GoogleLoadBalancedBackend> serviceBackends = loadBalancer?.backendService?.backends
    List<String> backendGroupNames = serviceBackends
      .findAll { serverGroup.region == Utils.getRegionFromGroupUrl(it.serverGroupUrl) }
      .collect { GCEUtil.getLocalName(it.serverGroupUrl) }
    return loadBalancer.name in regionalLoadBalancersFromMetadata && !(serverGroup.name in backendGroupNames)
  }

  static boolean determineSslLoadBalancerDisabledState(GoogleSslLoadBalancer loadBalancer,
                                                       GoogleServerGroup serverGroup) {
    def globalLoadBalancersFromMetadata = serverGroup.asg.get(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)

    if (loadBalancer.backendService == null) {
      log.warn("Malformed ssl load balancer encountered: ${loadBalancer}")
    }
    List<GoogleLoadBalancedBackend> serviceBackends = loadBalancer?.backendService?.backends
    List<String> backendGroupNames = serviceBackends
      .findAll { serverGroup.region == Utils.getRegionFromGroupUrl(it.serverGroupUrl) }
      .collect { GCEUtil.getLocalName(it.serverGroupUrl) }
    return loadBalancer.name in globalLoadBalancersFromMetadata && !(serverGroup.name in backendGroupNames)
  }

  static boolean determineTcpLoadBalancerDisabledState(GoogleTcpLoadBalancer loadBalancer,
                                                       GoogleServerGroup serverGroup) {
    def globalLoadBalancersFromMetadata = serverGroup.asg.get(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)

    if (loadBalancer.backendService == null) {
      log.warn("Malformed tcp load balancer encountered: ${loadBalancer}")
    }
    List<GoogleLoadBalancedBackend> serviceBackends = loadBalancer?.backendService?.backends
    List<String> backendGroupNames = serviceBackends
      .findAll { serverGroup.region == Utils.getRegionFromGroupUrl(it.serverGroupUrl) }
      .collect { GCEUtil.getLocalName(it.serverGroupUrl) }
    return loadBalancer.name in globalLoadBalancersFromMetadata && !(serverGroup.name in backendGroupNames)
  }

  /**
   * Merge relationships to prevent overwrites from onDemand cache data.
   *
   * @param onDemandRelationships onDemand cache relationships.
   * @param existingRelationships existing relationships (from the cache result builder).
   * @return Map of merged relationships.
   */
  static Map<String, Collection<String>> mergeOnDemandCacheRelationships(Map<String, Collection<String>> onDemandRelationships, Map<String, Collection<String>> existingRelationships) {
    log.debug("Merging onDemand relationships: ${onDemandRelationships} with existing relationships: ${existingRelationships}")
    Set<String> relationshipKeys = existingRelationships.keySet() + onDemandRelationships.keySet()
    return relationshipKeys.collectEntries { String key ->
      if (onDemandRelationships.containsKey(key) && existingRelationships.containsKey(key)) {
        Set<String> ret = new HashSet<>()
        ret.addAll(onDemandRelationships.getOrDefault(key, []))
        ret.addAll(existingRelationships.getOrDefault(key, []))
        return new MapEntry(key, ret)
      } else if (onDemandRelationships.containsKey(key)) {
        return new MapEntry(key, onDemandRelationships.getOrDefault(key, []))
      } else if (existingRelationships.containsKey(key)) {
        return new MapEntry(key, existingRelationships.getOrDefault(key, []))
      }
      log.warn("Attempted to merge relationship key ${key} that neither exists in onDemand cache data nor existing cache data.")
      return null
    }
  }
}
