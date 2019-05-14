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

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.SHARE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.matches;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ShareCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShareCloudFoundryServiceAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private ShareCloudFoundryServiceDescription desc = new ShareCloudFoundryServiceDescription();

  @Test
  void printsTwoStatusesWhenSharingSucceeds() {
    desc.setRegion("org > space");
    desc.setClient(client);
    desc.setServiceInstanceName("service-instance-name");
    Set<String> sharedToRegions = new HashSet<>();
    sharedToRegions.add("org1 > region1");
    sharedToRegions.add("org2 > region2");
    sharedToRegions.add("org3 > region3");
    desc.setShareToRegions(sharedToRegions);

    ServiceInstanceResponse serviceInstanceResponse =
        new ServiceInstanceResponse()
            .setServiceInstanceName("some-service-name")
            .setType(SHARE)
            .setState(SUCCEEDED);
    when(client.getServiceInstances().shareServiceInstance(any(), any(), any()))
        .thenReturn(serviceInstanceResponse);

    ShareCloudFoundryServiceAtomicOperation op = new ShareCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);

    verify(client.getServiceInstances(), times(1))
        .shareServiceInstance(
            matches("org > space"), matches("service-instance-name"), same(sharedToRegions));
    assertThat(task.getHistory())
        .has(
            statusStartsWith(
                "Sharing service instance 'service-instance-name' from 'org > space' into '"),
            atIndex(1));
    assertThat(task.getHistory())
        .has(status("Finished sharing service instance 'service-instance-name'"), atIndex(2));
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(ServiceInstanceResponse.class);
    ServiceInstanceResponse response = (ServiceInstanceResponse) o;
    assertThat(response).isEqualToComparingFieldByFieldRecursively(serviceInstanceResponse);
  }

  @Test
  void printsOnlyOneStatusWhenSharingFails() {
    desc.setRegion("org > space");
    desc.setClient(client);
    desc.setServiceInstanceName("service-instance-name");
    Set<String> sharedToRegions = new HashSet<>();
    sharedToRegions.add("org1 > region1");
    sharedToRegions.add("org2 > region2");
    sharedToRegions.add("org3 > region3");
    desc.setShareToRegions(sharedToRegions);

    when(client.getServiceInstances().shareServiceInstance(any(), any(), any()))
        .thenThrow(new CloudFoundryApiException("Much fail"));

    ShareCloudFoundryServiceAtomicOperation op = new ShareCloudFoundryServiceAtomicOperation(desc);

    Task task = runOperation(op);

    verify(client.getServiceInstances(), times(1))
        .shareServiceInstance(
            matches("org > space"), matches("service-instance-name"), same(sharedToRegions));
    assertThat(task.getHistory().size()).isEqualTo(2);
    assertThat(task.getHistory())
        .has(
            statusStartsWith(
                "Sharing service instance 'service-instance-name' from 'org > space' into '"),
            atIndex(1));
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isNotInstanceOf(ServiceInstanceResponse.class);
  }
}
