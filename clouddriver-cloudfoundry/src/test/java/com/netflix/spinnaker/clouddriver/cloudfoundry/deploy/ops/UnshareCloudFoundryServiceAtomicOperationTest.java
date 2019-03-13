/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.UnshareCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.UNSHARE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.matches;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

class UnshareCloudFoundryServiceAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private UnshareCloudFoundryServiceDescription desc = new UnshareCloudFoundryServiceDescription();

  @Test
  void deployService() {
    desc.setRegion("org > space");
    desc.setClient(client);
    desc.setServiceInstanceName("service-instance-name");
    Set<String> unsharedFromRegions = new HashSet<>();
    unsharedFromRegions.add("org1 > region1");
    unsharedFromRegions.add("org2 > region2");
    unsharedFromRegions.add("org3 > region3");
    desc.setUnshareFromRegions(unsharedFromRegions);

    ServiceInstanceResponse serviceInstanceResponse = new ServiceInstanceResponse()
      .setServiceInstanceName("some-service-name")
      .setType(UNSHARE)
      .setState(SUCCEEDED);
    when(client.getServiceInstances().unshareServiceInstance(any(), any()))
      .thenReturn(serviceInstanceResponse);

    UnshareCloudFoundryServiceAtomicOperation op = new UnshareCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);

    verify(client.getServiceInstances(), times(1))
      .unshareServiceInstance(matches("service-instance-name"), same(unsharedFromRegions));
    assertThat(task.getHistory())
      .has(statusStartsWith("Unsharing service instance 'service-instance-name' from '"), atIndex(1));
    assertThat(task.getHistory())
      .has(status("Finished unsharing service instance 'service-instance-name'"), atIndex(2));
    List<Object> resultObjects = task.getResultObjects();
    assertThat(1).isEqualTo(resultObjects.size());
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceInstanceResponse.class);
    ServiceInstanceResponse response = (ServiceInstanceResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceInstanceResponse);
  }

  @Test
  void printsOnlyOneStatusWhenUnsharingFails() {
    desc.setRegion("org > space");
    desc.setClient(client);
    desc.setServiceInstanceName("service-instance-name");
    Set<String> unshareFromRegions = new HashSet<>();
    unshareFromRegions.add("org1 > region1");
    unshareFromRegions.add("org2 > region2");
    unshareFromRegions.add("org3 > region3");
    desc.setUnshareFromRegions(unshareFromRegions);

    when(client.getServiceInstances().unshareServiceInstance(any(), any()))
      .thenThrow(new CloudFoundryApiException("Much fail"));

    UnshareCloudFoundryServiceAtomicOperation op = new UnshareCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);

    verify(client.getServiceInstances(), times(1))
      .unshareServiceInstance(matches("service-instance-name"), same(unshareFromRegions));
    assertThat(task.getHistory().size()).isEqualTo(2);
    assertThat(task.getHistory())
      .has(statusStartsWith("Unsharing service instance 'service-instance-name' from '"), atIndex(1));
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isNotInstanceOf(ServiceInstanceResponse.class);
  }
}
