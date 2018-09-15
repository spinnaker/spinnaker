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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.RouteService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Route;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutesTest {
  @Test
  void toRouteId() {
    CloudFoundryDomain domain = CloudFoundryDomain.builder()
      .id("domainGuid")
      .name("apps.calabasas.cf-app.com")
      .build();

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
}