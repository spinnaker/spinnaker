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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ApplicationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.RouteService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Application;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Route;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import retrofit2.Response;
import retrofit2.mock.Calls;

class CloudFoundryClientUtilsTest {

  @Test
  void collectPagesIteratesOverOnePage() {
    ApplicationService applicationService = mock(ApplicationService.class);
    Application applicationOne = new Application().setName("app-name-one");
    List pageOneResources = Collections.singletonList(applicationOne);
    Pagination<Application> pageOne = new Pagination<>();
    pageOne.setPagination(new Pagination.Details().setTotalPages(1));
    pageOne.setResources(pageOneResources);

    when(applicationService.all(null, null, null, null))
        .thenReturn(Calls.response(Response.success(pageOne)));

    List results =
        CloudFoundryClientUtils.collectPages(
            "applications", page -> applicationService.all(page, null, null, null));

    assertThat(results).containsExactly(applicationOne);
  }

  @Test
  void collectPagesIteratesOverMultiplePages() {
    ApplicationService applicationService = mock(ApplicationService.class);
    Application applicationOne = new Application().setName("app-name-one");
    List pageOneResources = Collections.singletonList(applicationOne);
    Pagination<Application> pageOne = new Pagination<>();
    pageOne.setPagination(new Pagination.Details().setTotalPages(2));
    pageOne.setResources(pageOneResources);
    Application applicationTwo = new Application().setName("app-name-two");
    List pageTwoResources = Collections.singletonList(applicationTwo);
    Pagination<Application> pageTwo = new Pagination<>();
    pageTwo.setPagination(new Pagination.Details().setTotalPages(2));
    pageTwo.setResources(pageTwoResources);

    when(applicationService.all(null, null, null, null))
        .thenReturn(Calls.response(Response.success(pageOne)));
    when(applicationService.all(2, null, null, null))
        .thenReturn(Calls.response(Response.success(pageTwo)));

    List results =
        CloudFoundryClientUtils.collectPages(
            "applications", page -> applicationService.all(page, null, null, null));

    assertThat(results).containsExactly(applicationOne, applicationTwo);
  }

  @Test
  void collectPagesForRoutesIteratesOverOnePage() {
    RouteService routeService = mock(RouteService.class);
    Route routeOne = new Route();
    routeOne.setHost("route-name-one");
    routeOne.setGuid("route-one-guid");
    Pagination<Route> pageOne = new Pagination<>();
    pageOne.setPagination(new Pagination.Details().setTotalPages(1));
    pageOne.setResources(Collections.singletonList(routeOne));

    when(routeService.all(null, null, null, null, null, null, null, null))
        .thenReturn(Calls.response(Response.success(pageOne)));

    List results =
        CloudFoundryClientUtils.collectPages(
            "routes", page -> routeService.all(page, null, null, null, null, null, null, null));

    assertThat(results).containsExactly(routeOne);
  }

  @Test
  void collectPagesForRoutesIteratesOverMultiplePages() {
    RouteService routeService = mock(RouteService.class);
    Route routeOne = new Route();
    routeOne.setHost("route-name-one");
    routeOne.setGuid("route-one-guid");
    Pagination<Route> pageOne = new Pagination<>();
    pageOne.setPagination(new Pagination.Details().setTotalPages(2));
    pageOne.setResources(Collections.singletonList(routeOne));

    Route routeTwo = new Route();
    routeTwo.setHost("route-name-two");
    routeTwo.setGuid("route-two-guid");
    Pagination<Route> pageTwo = new Pagination<>();
    pageTwo.setPagination(new Pagination.Details().setTotalPages(2));
    pageTwo.setResources(Collections.singletonList(routeTwo));

    when(routeService.all(null, null, null, null, null, null, null, null))
        .thenReturn(Calls.response(Response.success(pageOne)));
    when(routeService.all(2, null, null, null, null, null, null, null))
        .thenReturn(Calls.response(Response.success(pageTwo)));

    List results =
        CloudFoundryClientUtils.collectPages(
            "routes", page -> routeService.all(page, null, null, null, null, null, null, null));

    assertThat(results).containsExactly(routeOne, routeTwo);
  }
}
