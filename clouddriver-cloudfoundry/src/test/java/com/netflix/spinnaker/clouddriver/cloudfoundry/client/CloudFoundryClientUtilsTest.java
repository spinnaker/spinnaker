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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ApplicationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.DomainService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Domain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Application;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudFoundryClientUtilsTest {

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

    when(applicationService.all(null, null, null)).thenReturn(pageOne);
    when(applicationService.all(2, null, null)).thenReturn(pageTwo);

    List results = CloudFoundryClientUtils.collectPages("applications", page -> applicationService.all(page, null, null));

    assertThat(results).containsExactly(applicationOne, applicationTwo);
  }

  @Test
  void collectPageResourcesIteratesOverMultiplePages() {
    DomainService domainService = mock(DomainService.class);
    Domain domainOne = new Domain().setName("domain-name-one");
    Page pageOne = Page.singleton(domainOne, "domain-one-guid");
    Domain domainTwo = new Domain().setName("domain-name-two");
    Page pageTwo = Page.singleton(domainTwo, "domain-two-guid");

    when(domainService.allShared(null)).thenReturn(pageOne);
    when(domainService.allShared(2)).thenReturn(pageTwo);

    List results = CloudFoundryClientUtils.collectPageResources("shared domains",  domainService::allShared);

    assertThat(results).containsExactly(pageOne.getResources().get(0), pageTwo.getResources().get(0));
  }
}
