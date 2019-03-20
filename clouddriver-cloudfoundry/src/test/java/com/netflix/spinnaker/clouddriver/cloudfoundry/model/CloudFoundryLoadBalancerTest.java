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

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

class CloudFoundryLoadBalancerTest {
  private CloudFoundryOrganization org = CloudFoundryOrganization.builder().id("orgId").name("org").build();

  private CloudFoundryLoadBalancer loadBalancer = CloudFoundryLoadBalancer.builder()
    .account("dev")
    .id("id")
    .host("host")
    .path("path")
    .port(8080)
    .space(CloudFoundrySpace.builder().id("spaceId").name("space").organization(org).build())
    .domain(CloudFoundryDomain.builder().id("domainId").name("domain").organization(org).build())
    .mappedApps(singleton(CloudFoundryServerGroup.builder().name("demo-dev-v001").instances(emptySet()).build()))
    .build();

  @Test
  void serialization() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    assertThat(mapper.writeValueAsString(loadBalancer)).doesNotContain("mappedApps");
  }

  @Test
  void getNameWithoutPortWithoutPath() {
    CloudFoundryLoadBalancer testLoadBalancer = CloudFoundryLoadBalancer.builder()
      .host("hostname")
      .domain(CloudFoundryDomain.builder().name("example.com").build())
      .build();

    assertThat(testLoadBalancer.getName()).isEqualToIgnoringCase("hostname.example.com");
  }

  @Test
  void getNameWithoutPortWithPath() {
    CloudFoundryLoadBalancer testLoadBalancer = CloudFoundryLoadBalancer.builder()
      .host("hostname")
      .path("/my-path")
      .domain(CloudFoundryDomain.builder().name("example.com").build())
      .build();

    assertThat(testLoadBalancer.getName()).isEqualToIgnoringCase("hostname.example.com/my-path");
  }

  @Test
  void getNameWithPortWithoutPath() {
    CloudFoundryLoadBalancer testLoadBalancer = CloudFoundryLoadBalancer.builder()
      .host("hostname")
      .port(9999)
      .domain(CloudFoundryDomain.builder().name("example.com").build())
      .build();

    assertThat(testLoadBalancer.getName()).isEqualToIgnoringCase("hostname.example.com-9999");
  }

  @Test
  void getNameWithPortWithPath() {
    CloudFoundryLoadBalancer testLoadBalancer = CloudFoundryLoadBalancer.builder()
      .host("hostname")
      .path("/my-path")
      .port(9999)
      .domain(CloudFoundryDomain.builder().name("example.com").build())
      .build();

    assertThat(testLoadBalancer.getName()).isEqualToIgnoringCase("hostname.example.com-9999/my-path");
  }
}
