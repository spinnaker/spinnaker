/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.UpsertCloudFoundryLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class UpsertCloudFoundryLoadBalancerAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private final UpsertCloudFoundryLoadBalancerDescription desc;
  private final Routes routes;

  {
    desc = new UpsertCloudFoundryLoadBalancerDescription();
    desc.setClient(client);
    desc.setRegion("org>space");
    desc.setSpace(CloudFoundrySpace.fromRegion("org>space"));
    desc.setHost("some-host");
    desc.setPath("some-path");
    desc.setPort(8080);
    desc.setDomain(CloudFoundryDomain.builder().build());
    routes = client.getRoutes();
  }

  @Test
  void operateSuccessfullyCreatedLoadBalancer() {
    when(routes.createRoute(any(), any())).thenReturn(CloudFoundryLoadBalancer.builder().build());

    UpsertCloudFoundryLoadBalancerAtomicOperation op = new UpsertCloudFoundryLoadBalancerAtomicOperation(desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Creating load balancer in 'org>space'"), atIndex(1))
      .has(status("Done creating load balancer"), atIndex(2));
  }

  @Test
  void operateThrowCloudFoundryApiExceptionWhenRouteExists() {
    when(routes.createRoute(any(), any())).thenReturn(null);

    UpsertCloudFoundryLoadBalancerAtomicOperation op = new UpsertCloudFoundryLoadBalancerAtomicOperation(desc);

    Task task = runOperation(op);
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(Map.class);
    Object ex = ((Map) o).get("EXCEPTION");
    assertThat(ex).isInstanceOf(CloudFoundryApiException.class);
    assertThat(((CloudFoundryApiException) ex).getMessage()).contains("Load balancer already exists in another organization and space");
  }
}
