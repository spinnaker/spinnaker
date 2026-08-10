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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.RouteService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Relationship;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Route;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ToOneRelationship;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

class RoutesTest {
  @Test
  void toRouteId() {
    CloudFoundryDomain domain =
        CloudFoundryDomain.builder().id("domainGuid").name("apps.calabasas.cf-app.com").build();

    Domains domains = mock(Domains.class);
    when(domains.findById(eq("domainGuid"))).thenReturn(domain);
    when(domains.findByName(eq("apps.calabasas.cf-app.com"))).thenReturn(Optional.of(domain));

    Spaces spaces = mock(Spaces.class);
    when(spaces.findById(any())).thenReturn(CloudFoundrySpace.fromRegion("myorg > dev"));

    RouteService routeService = mock(RouteService.class);

    Routes routes =
        new Routes("pws", routeService, null, domains, spaces, 500, ForkJoinPool.commonPool());
    RouteId routeId = routes.toRouteId("demo1-prod.apps.calabasas.cf-app.com/path/v1.0");
    assertThat(routeId).isNotNull();
    assertThat(routeId.getHost()).isEqualTo("demo1-prod");
    assertThat(routeId.getDomainGuid()).isEqualTo("domainGuid");
    assertThat(routeId.getPath()).isEqualTo("/path/v1.0");
  }

  @Test
  void toRouteIdReturnsNullForInvalidRoute() {
    Routes routes = new Routes(null, null, null, null, null, 500, ForkJoinPool.commonPool());
    assertNull(routes.toRouteId("demo1-pro cf-app.com/path"));
  }

  @Test
  void findShouldFilterCorrectlyOnMultipleResults() {
    CloudFoundryDomain domain =
        CloudFoundryDomain.builder().id("domain-guid").name("apps.calabasas.cf-app.com").build();

    Domains domains = mock(Domains.class);

    when(domains.findById(eq("domain-guid"))).thenReturn(domain);
    when(domains.findByName(eq("apps.calabasas.cf-app.com"))).thenReturn(Optional.of(domain));

    Route hostOnly =
        createV3Route("route-guid-1", "somehost", "", null, "space-guid", "domain-guid");
    Route withPath1 =
        createV3Route("route-guid-2", "somehost", "/person", null, "space-guid", "domain-guid");
    Route withPath2 =
        createV3Route("route-guid-3", "somehost", "/account", null, "space-guid", "domain-guid");
    Route withPathAndPort =
        createV3Route("route-guid-4", "somehost", "/account", 8888, "space-guid", "domain-guid");

    Pagination<Route> routePage = new Pagination<>();
    Pagination.Details details = new Pagination.Details();
    details.setTotalPages(1);
    routePage.setPagination(details);
    routePage.setResources(Arrays.asList(withPath2, withPath1, hostOnly, withPathAndPort));

    Spaces spaces = mock(Spaces.class);
    CloudFoundryOrganization org =
        CloudFoundryOrganization.builder().id("org-id").name("org-name").build();
    CloudFoundrySpace space =
        CloudFoundrySpace.builder().organization(org).name("space-name").id("space-guid").build();
    RouteService routeService = mock(RouteService.class);
    Applications applications = mock(Applications.class);

    when(spaces.findById("space-guid")).thenReturn(space);
    when(routeService.all(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> Calls.response(Response.success(routePage)));
    Routes routes =
        new Routes(
            "pws", routeService, applications, domains, spaces, 500, ForkJoinPool.commonPool());

    CloudFoundryLoadBalancer loadBalancer =
        routes.find(new RouteId().setHost("somehost").setDomainGuid("domain-guid"), "space-guid");
    assertThat(loadBalancer).isNotNull();
    assertThat(loadBalancer.getHost()).isEqualTo("somehost");
    assertThat(loadBalancer.getDomain().getId()).isEqualTo("domain-guid");
    assertThat(loadBalancer.getPath()).isNullOrEmpty();
    assertThat(loadBalancer.getPort()).isNull();

    routePage.setResources(Arrays.asList(withPathAndPort, withPath2));

    loadBalancer =
        routes.find(
            new RouteId().setHost("somehost").setDomainGuid("domain-guid").setPath("/account"),
            "space-guid");
    assertThat(loadBalancer).isNotNull();
    assertThat(loadBalancer.getHost()).isEqualTo("somehost");
    assertThat(loadBalancer.getDomain().getId()).isEqualTo("domain-guid");
    assertThat(loadBalancer.getPath()).isEqualTo("/account");
    assertThat(loadBalancer.getPort()).isNull();
  }

  private Route createV3Route(
      String guid, String host, String path, Integer port, String spaceGuid, String domainGuid) {
    Route route = new Route();
    route.setGuid(guid);
    route.setHost(host);
    route.setPath(path);
    route.setPort(port);
    route.setRelationships(
        Map.of(
            "space", new ToOneRelationship(new Relationship(spaceGuid)),
            "domain", new ToOneRelationship(new Relationship(domainGuid))));
    return route;
  }

  @Test
  void validRouteFormatsReturnTrue() {
    assertTrue(Routes.isValidRouteFormat("a.b"));
    assertTrue(Routes.isValidRouteFormat("foo.bar"));
    assertTrue(Routes.isValidRouteFormat("10_bLAh.org:3000"));
    assertTrue(Routes.isValidRouteFormat("unbe-lievable.b_c.gov:9999/fo-o_bar"));
  }

  @Test
  void invalidRouteFormatsReturnFalse() {
    assertFalse(Routes.isValidRouteFormat("abc"));
    assertFalse(Routes.isValidRouteFormat("ab.c d.com"));
    assertFalse(Routes.isValidRouteFormat("ab.cd.com:a5b0"));
    assertFalse(Routes.isValidRouteFormat("EBCDIC.com/DVORAK:a5b0"));
    assertFalse(Routes.isValidRouteFormat("ab.cd.com/fo ba"));
  }
}
