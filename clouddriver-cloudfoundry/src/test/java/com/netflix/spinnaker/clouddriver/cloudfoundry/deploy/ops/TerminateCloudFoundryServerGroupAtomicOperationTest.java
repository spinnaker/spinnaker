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

import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.TerminateCloudFoundryInstancesDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TerminateCloudFoundryServerGroupAtomicOperationTest extends AbstractCloudFoundryAtomicOperationTest{
  private TerminateCloudFoundryInstancesDescription desc = new TerminateCloudFoundryInstancesDescription();

  TerminateCloudFoundryServerGroupAtomicOperationTest() {
    super();
  }

  @BeforeEach
  void before() {
    desc.setClient(client);
    desc.setInstanceIds(new String[] { "123-0", "123-1" });
  }

  @Test
  void terminate() {
    TerminateCloudFoundryInstancesAtomicOperation op = new TerminateCloudFoundryInstancesAtomicOperation(desc);

    assertThat(runOperation(op).getHistory())
      .has(status("Terminating application instances ['123-0', '123-1']"), atIndex(1))
      .has(status("Terminated application instances ['123-0', '123-1']"), atIndex(2));

    verify(applications, times(2)).deleteAppInstance(eq("123"), anyString());
  }
}