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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessStats;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ScaleCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScaleCloudFoundryServerGroupAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest {
  private ScaleCloudFoundryServerGroupDescription desc = new ScaleCloudFoundryServerGroupDescription();

  ScaleCloudFoundryServerGroupAtomicOperationTest() {
    super();
  }

  @BeforeEach
  void before() {
    desc.setClient(client);
    desc.setServerGroupName("myapp");
    desc.setInstanceCount(2);
  }

  @Test
  void scale() {
    OperationPoller poller = mock(OperationPoller.class);

    //noinspection unchecked
    when(poller.waitForOperation(any(Supplier.class), any(), anyLong(), any(), any(), any())).thenReturn(ProcessStats.State.RUNNING);

    ScaleCloudFoundryServerGroupAtomicOperation op = new ScaleCloudFoundryServerGroupAtomicOperation(poller, desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Resizing 'myapp'"), atIndex(1))
      .has(status("Resized 'myapp'"), atIndex(2));
  }

  @Test
  void failedToScale() {
    OperationPoller poller = mock(OperationPoller.class);

    //noinspection unchecked
    when(poller.waitForOperation(any(Supplier.class), any(), anyLong(), any(), any(), any())).thenReturn(ProcessStats.State.CRASHED);

    ScaleCloudFoundryServerGroupAtomicOperation op = new ScaleCloudFoundryServerGroupAtomicOperation(poller, desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Resizing 'myapp'"), atIndex(1))
      .has(status("Failed to start 'myapp' which instead crashed"), atIndex(2));
  }
}