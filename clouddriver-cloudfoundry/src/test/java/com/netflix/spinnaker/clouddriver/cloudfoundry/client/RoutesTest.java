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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.RouteService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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

    Route route = new Route();
    route.setHost("demo1-prod");
    route.setDomainGuid("domainGuid");
    route.setPath("/path");

    RouteService routeService = mock(RouteService.class);
    when(routeService.all(any(), any())).thenReturn(Page.singleton(route, "abc123"));
    when(routeService.routeMappings(any(), any())).thenReturn(new Page<>());

    Routes routes = new Routes("pws", routeService, null, domains, spaces);
    RouteId routeId = routes.toRouteId("demo1-prod.apps.calabasas.cf-app.com/path");
    assertThat(routeId).isNotNull();
    assertThat(routeId.getHost()).isEqualTo("demo1-prod");
    assertThat(routeId.getDomainGuid()).isEqualTo("domainGuid");
    assertThat(routeId.getPath()).isEqualTo("/path");
  }

  @Test
  void toRouteIdReturnsNullForInvalidRoute() {
    Routes routes = new Routes(null, null, null, null, null);
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
        new Route(
            new RouteId().setHost("somehost").setDomainGuid("domain-guid").setPath(""),
            "space-guid");
    Route withPath1 =
        new Route(
            new RouteId().setHost("somehost").setDomainGuid("domain-guid").setPath("/person"),
            "space-guid");
    Route withPath2 =
        new Route(
            new RouteId().setHost("somehost").setDomainGuid("domain-guid").setPath("/account"),
            "space-guid");
    Route withPathAndPort =
        new Route(
            new Route()
                .setHost("somehost")
                .setDomainGuid("domain-guid")
                .setPath("/account")
                .setPort(8888),
            "space-guid");

    Page<Route> routePage = new Page<>();
    routePage.setTotalPages(1);
    routePage.setTotalResults(4);
    routePage.setResources(
        Arrays.asList(
            createRouteResource(withPath2),
            createRouteResource(withPath1),
            createRouteResource(hostOnly),
            createRouteResource(withPathAndPort)));

    Spaces spaces = mock(Spaces.class);
    CloudFoundryOrganization org =
        CloudFoundryOrganization.builder().id("org-id").name("org-name").build();
    CloudFoundrySpace space =
        CloudFoundrySpace.builder().organization(org).name("space-name").id("space-guid").build();
    RouteService routeService = mock(RouteService.class);

    Page<RouteMapping> routeMappingPage = new Page<>();
    routeMappingPage.setTotalResults(0);
    routeMappingPage.setTotalPages(1);

    when(spaces.findById("space-guid")).thenReturn(space);
    when(routeService.all(any(), any())).thenReturn(routePage);
    when(routeService.routeMappings(any(), any())).thenReturn(routeMappingPage);

    Routes routes = new Routes("pws", routeService, null, domains, spaces);

    CloudFoundryLoadBalancer loadBalancer =
        routes.find(new RouteId().setHost("somehost").setDomainGuid("domain-guid"), "space-guid");
    assertThat(loadBalancer).isNotNull();
    assertThat(loadBalancer.getHost()).isEqualTo("somehost");
    assertThat(loadBalancer.getDomain().getId()).isEqualTo("domain-guid");
    assertThat(loadBalancer.getPath()).isNullOrEmpty();
    assertThat(loadBalancer.getPort()).isNull();

    routePage.setResources(
        Arrays.asList(createRouteResource(withPathAndPort), createRouteResource(withPath2)));

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

  private Resource<Route> createRouteResource(Route route) {
    return new Resource<Route>()
        .setEntity(route)
        .setMetadata(new Resource.Metadata().setGuid("route-guid"));
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
