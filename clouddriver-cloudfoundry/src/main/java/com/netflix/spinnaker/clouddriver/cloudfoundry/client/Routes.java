/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static java.util.Collections.emptySet;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.RouteService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Route;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.RouteMapping;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class Routes {
  private static final Pattern VALID_ROUTE_REGEX =
      Pattern.compile("^([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_.-]+)(:[0-9]+)?([/a-zA-Z0-9_-]+)?$");

  private final String account;
  private final RouteService api;
  private final Applications applications;
  private final Domains domains;
  private final Spaces spaces;

  private LoadingCache<String, List<RouteMapping>> routeMappings =
      CacheBuilder.newBuilder()
          .expireAfterWrite(1, TimeUnit.SECONDS)
          .build(
              new CacheLoader<String, List<RouteMapping>>() {
                @Override
                public List<RouteMapping> load(@Nonnull String guid)
                    throws CloudFoundryApiException, ResourceNotFoundException {
                  return collectPageResources("route mappings", pg -> api.routeMappings(guid, pg))
                      .stream()
                      .map(Resource::getEntity)
                      .collect(Collectors.toList());
                }
              });

  private CloudFoundryLoadBalancer map(Resource<Route> res) throws CloudFoundryApiException {
    Route route = res.getEntity();

    Set<CloudFoundryServerGroup> mappedApps = emptySet();
    try {
      mappedApps =
          routeMappings.get(res.getMetadata().getGuid()).stream()
              .map(rm -> applications.findById(rm.getAppGuid()))
              .collect(Collectors.toSet());
    } catch (ExecutionException e) {
      if (!(e.getCause() instanceof ResourceNotFoundException))
        throw new CloudFoundryApiException(e.getCause(), "Unable to find route mappings by id");
    }

    return CloudFoundryLoadBalancer.builder()
        .account(account)
        .id(res.getMetadata().getGuid())
        .host(route.getHost())
        .path(route.getPath())
        .port(route.getPort())
        .space(spaces.findById(route.getSpaceGuid()))
        .domain(domains.findById(route.getDomainGuid()))
        .mappedApps(mappedApps)
        .build();
  }

  @Nullable
  public CloudFoundryLoadBalancer find(RouteId routeId, String spaceId)
      throws CloudFoundryApiException {
    CloudFoundrySpace id = spaces.findById(spaceId);
    String orgId = id.getOrganization().getId();

    List<String> queryParams = new ArrayList<>();
    queryParams.add("host:" + routeId.getHost());
    queryParams.add("organization_guid:" + orgId);
    queryParams.add("domain_guid:" + routeId.getDomainGuid());
    if (routeId.getPath() != null) queryParams.add("path:" + routeId.getPath());
    if (routeId.getPort() != null) queryParams.add("port:" + routeId.getPort().toString());

    return collectPageResources("route mappings", pg -> api.all(pg, queryParams)).stream()
        .filter(
            routeResource ->
                (routeId.getPath() != null || routeResource.getEntity().getPath().isEmpty())
                    && (routeId.getPort() != null || routeResource.getEntity().getPort() == null))
        .findFirst()
        .map(this::map)
        .orElse(null);
  }

  @Nullable
  public RouteId toRouteId(String uri) throws CloudFoundryApiException {
    Matcher matcher = VALID_ROUTE_REGEX.matcher(uri);
    if (matcher.find()) {
      CloudFoundryDomain domain = domains.findByName(matcher.group(2)).orElse(null);
      if (domain == null) {
        return null;
      }
      RouteId routeId = new RouteId();
      routeId.setHost(matcher.group(1));
      routeId.setDomainGuid(domain.getId());
      routeId.setPort(
          matcher.group(3) == null ? null : Integer.parseInt(matcher.group(3).substring(1)));
      routeId.setPath(matcher.group(4));
      return routeId;
    } else {
      return null;
    }
  }

  public List<CloudFoundryLoadBalancer> all() throws CloudFoundryApiException {
    List<Resource<Route>> routeResources = collectPageResources("routes", pg -> api.all(pg, null));
    List<CloudFoundryLoadBalancer> loadBalancers = new ArrayList<>(routeResources.size());
    for (Resource<Route> routeResource : routeResources) {
      loadBalancers.add(map(routeResource));
    }
    return loadBalancers;
  }

  public CloudFoundryLoadBalancer createRoute(RouteId routeId, String spaceId)
      throws CloudFoundryApiException {
    Route route = new Route(routeId, spaceId);
    try {
      Resource<Route> newRoute =
          safelyCall(() -> api.createRoute(route))
              .orElseThrow(
                  () ->
                      new CloudFoundryApiException(
                          "Cloud Foundry signaled that route creation succeeded but failed to provide a response."));
      return map(newRoute);
    } catch (CloudFoundryApiException e) {
      if (e.getErrorCode() == null) throw e;

      switch (e.getErrorCode()) {
        case ROUTE_HOST_TAKEN:
        case ROUTE_PATH_TAKEN:
        case ROUTE_PORT_TAKEN:
          return this.find(routeId, spaceId);
        default:
          throw e;
      }
    }
  }

  public void deleteRoute(String loadBalancerGuid) throws CloudFoundryApiException {
    safelyCall(() -> api.deleteRoute(loadBalancerGuid));
  }

  public static boolean isValidRouteFormat(String route) {
    return VALID_ROUTE_REGEX.matcher(route).find();
  }
}
