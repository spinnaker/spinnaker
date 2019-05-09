/*
 * Copyright 2018 Pivotal, Inc.
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ScaleCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import groovy.lang.Closure;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScaleCloudFoundryServerGroupAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private final ScaleCloudFoundryServerGroupDescription desc;

  ScaleCloudFoundryServerGroupAtomicOperationTest() {
    desc = new ScaleCloudFoundryServerGroupDescription();
    desc.setClient(client);
    desc.setServerGroupName("myapp");
    desc.setCapacity(ServerGroup.Capacity.builder().desired(2).build());
  }

  @Test
  void scale() {
    OperationPoller poller = mock(OperationPoller.class);

    //noinspection unchecked
    when(poller.waitForOperation(any(Supplier.class), any(), any(), any(), any(), any()))
      .thenReturn(ProcessStats.State.RUNNING);

    ScaleCloudFoundryServerGroupAtomicOperation op = new ScaleCloudFoundryServerGroupAtomicOperation(poller, desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Resizing 'myapp'"), atIndex(1))
      .has(status("Resized 'myapp'"), atIndex(2));
  }

  @Test
  void failedToScale() {
    OperationPoller poller = mock(OperationPoller.class);

    //noinspection unchecked
    when(poller.waitForOperation(any(Supplier.class), any(), any(), any(), any(), any())).thenReturn(ProcessStats.State.CRASHED);

    ScaleCloudFoundryServerGroupAtomicOperation op = new ScaleCloudFoundryServerGroupAtomicOperation(poller, desc);

    Task task = runOperation(op);
    List<Object> resultObjects = task.getResultObjects();
    assertThat(resultObjects.size()).isEqualTo(1);
    Object o = resultObjects.get(0);
    assertThat(o).isInstanceOf(Map.class);
    Object ex = ((Map) o).get("EXCEPTION");
    assertThat(ex).isInstanceOf(CloudFoundryApiException.class);
    assertThat(((CloudFoundryApiException) ex).getMessage()).isEqualTo("Cloud Foundry API returned with error(s): Failed to start 'myapp' which instead crashed");
  }

  @Test
  void scaleDownToZero() {
    desc.setCapacity(new ServerGroup.Capacity(1, 1, 0));
    OperationPoller poller = mock(OperationPoller.class);

    //noinspection unchecked
    when(poller.waitForOperation(any(Supplier.class), any(), any(), any(), any(), any())).thenReturn(ProcessStats.State.DOWN);

    ScaleCloudFoundryServerGroupAtomicOperation op = new ScaleCloudFoundryServerGroupAtomicOperation(poller, desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Resizing 'myapp'"), atIndex(1))
      .has(status("Resized 'myapp'"), atIndex(2));
  }
}
