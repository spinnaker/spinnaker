/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Routes;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.LoadBalancersDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import io.vavr.collection.List;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnmapLoadBalancersAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest{
  private final LoadBalancersDescription desc;
  private final CloudFoundrySpace space;

  UnmapLoadBalancersAtomicOperationTest() {
    desc = new LoadBalancersDescription();
    desc.setClient(client);
    desc.setServerGroupName("myapp");
    space = CloudFoundrySpace.fromRegion("org>space");
    Routes routes = client.getRoutes();
    desc.setSpace(space);
    when(routes.toRouteId(anyString())).thenReturn(mock(RouteId.class));
  }

  @Test
  void operateWithNullRoutes() {
    UnmapLoadBalancersAtomicOperation op = new UnmapLoadBalancersAtomicOperation(desc);

    Exception exception = null;
    try {
      runOperation(op);
    } catch (CloudFoundryApiException cloudFoundryApiException) {
      exception = cloudFoundryApiException;
    }
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).isEqualTo("Cloud Foundry API returned with error(s): No load balancer specified");
  }

  @Test
  void operateWithEmptyRoutes() {
    desc.setRoutes(Collections.emptyList());
    UnmapLoadBalancersAtomicOperation op = new UnmapLoadBalancersAtomicOperation(desc);

    Exception exception = null;
    try {
      runOperation(op);
    } catch (CloudFoundryApiException cloudFoundryApiException) {
      exception = cloudFoundryApiException;
    }
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).isEqualTo("Cloud Foundry API returned with error(s): No load balancer specified");
  }

  @Test
  void operateWithMultipleBadRoutes() {
    desc.setRoutes(List.of(
      "bad.route-1.example.com",
      "bad.route 2.example.com"
    ).asJava());
    UnmapLoadBalancersAtomicOperation op = new UnmapLoadBalancersAtomicOperation(desc);
    Exception exception = null;
    try {
      runOperation(op);
    } catch (CloudFoundryApiException cloudFoundryApiException) {
      exception = cloudFoundryApiException;
    }
    assertThat(exception).isNotNull();
    assertThat(exception.getMessage()).isEqualTo("Cloud Foundry API returned with error(s): Load balancer 'bad.route-1.example.com' does not exist");
  }

  @Test
  void operateWithGoodRoutes() {
    desc.setRoutes(List.of(
      "good.route-1.example.com",
      "good.route-2.example.com"
    ).asJava());
    CloudFoundryDomain domain = CloudFoundryDomain.builder()
      .id("domain-id")
      .name("domain.com")
      .build();
    CloudFoundryLoadBalancer lb1 = CloudFoundryLoadBalancer.builder()
      .account("account")
      .id("lb1-id")
      .host("host1")
      .space(space)
      .domain(domain)
      .build();
    CloudFoundryLoadBalancer lb2 = CloudFoundryLoadBalancer.builder()
      .account("account")
      .id("lb2-id")
      .host("host2")
      .space(space)
      .domain(domain)
      .build();
    when(client.getRoutes().find(any(), any()))
      .thenReturn(lb1)
      .thenReturn(lb2);
    UnmapLoadBalancersAtomicOperation op = new UnmapLoadBalancersAtomicOperation(desc);
    assertThat(runOperation(op).getHistory())
      .has(status("Unmapping 'myapp' from load balancer(s)."), atIndex(1))
      .has(status("Unmapping load balancer 'good.route-1.example.com'") , atIndex(2))
      .has(status("Unmapped load balancer 'good.route-1.example.com'"), atIndex(3))
      .has(status("Unmapping load balancer 'good.route-2.example.com'"), atIndex(4))
      .has(status("Unmapped load balancer 'good.route-2.example.com'"), atIndex(5));
  }

}
