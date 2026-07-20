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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPages;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static java.util.Collections.emptySet;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.RouteService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateRoute;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Destination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Route;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServerGroup;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Routes {
  private static final Pattern VALID_ROUTE_REGEX =
      Pattern.compile("^([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_.-]+)(:[0-9]+)?([/a-zA-Z0-9_.-]+)?$");

  private final String account;
  private final RouteService api;
  private final Applications applications;
  private final Domains domains;
  private final Spaces spaces;
  private final Integer resultsPerPage;

  private final ForkJoinPool forkJoinPool;

  public Routes(
      String account,
      RouteService api,
      Applications applications,
      Domains domains,
      Spaces spaces,
      Integer resultsPerPage,
      ForkJoinPool forkJoinPool) {
    this.account = account;
    this.api = api;
    this.applications = applications;
    this.domains = domains;
    this.spaces = spaces;
    this.resultsPerPage = resultsPerPage;
    this.forkJoinPool = forkJoinPool;
  }

  private CloudFoundryLoadBalancer map(Route route) throws CloudFoundryApiException {
    Set<CloudFoundryServerGroup> mappedApps = emptySet();
    List<Destination> destinations = route.getDestinations();
    if (destinations != null && !destinations.isEmpty()) {
      mappedApps =
          destinations.stream()
              .map(
                  d -> {
                    try {
                      return applications.findById(d.getApp().getGuid());
                    } catch (Exception e) {
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
    }

    return CloudFoundryLoadBalancer.builder()
        .account(account)
        .id(route.getGuid())
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

    return collectPages(
            "routes",
            pg ->
                api.all(
                    pg,
                    1,
                    routeId.getHost(),
                    orgId,
                    routeId.getDomainGuid(),
                    routeId.getPath(),
                    routeId.getPort() != null ? String.valueOf(routeId.getPort()) : null,
                    null))
        .stream()
        .filter(
            route ->
                (routeId.getPath() != null || route.getPath() == null || route.getPath().isEmpty())
                    && (routeId.getPort() != null || route.getPort() == null))
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

  public List<CloudFoundryLoadBalancer> all(List<CloudFoundrySpace> spaces)
      throws CloudFoundryApiException {
    try {
      if (!spaces.isEmpty()) {
        List<String> spaceGuids =
            spaces.stream().map(CloudFoundrySpace::getId).collect(Collectors.toList());
        String orgGuids =
            spaces.stream()
                .map(s -> s.getOrganization().getId())
                .distinct()
                .collect(Collectors.joining(","));
        return forkJoinPool
            .submit(
                () ->
                    collectPages(
                            "routes",
                            pg ->
                                api.all(pg, resultsPerPage, null, orgGuids, null, null, null, null))
                        .parallelStream()
                        .map(this::map)
                        .filter(lb -> spaceGuids.contains(lb.getSpace().getId()))
                        .collect(Collectors.toList()))
            .get();
      } else {
        return forkJoinPool
            .submit(
                () ->
                    collectPages(
                            "routes",
                            pg -> api.all(pg, resultsPerPage, null, null, null, null, null, null))
                        .parallelStream()
                        .map(this::map)
                        .collect(Collectors.toList()))
            .get();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public CloudFoundryLoadBalancer createRoute(RouteId routeId, String spaceId)
      throws CloudFoundryApiException {
    CreateRoute body = CreateRoute.fromRouteId(routeId, spaceId);
    try {
      Route newRoute =
          safelyCall(() -> api.createRoute(body))
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
