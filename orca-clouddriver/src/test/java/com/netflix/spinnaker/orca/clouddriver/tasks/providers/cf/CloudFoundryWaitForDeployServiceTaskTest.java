/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf;

import com.netflix.spinnaker.orca.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class CloudFoundryWaitForDeployServiceTaskTest
  extends AbstractCloudFoundryWaitForServiceOperationTaskTest<CloudFoundryWaitForDeployServiceTask> {
  CloudFoundryWaitForDeployServiceTaskTest() {
    super("deployService", CloudFoundryWaitForDeployServiceTask::new);
  }

  @Test
  void isTerminalWhenOortResultIsFailed() {
    testOortServiceStatus(ExecutionStatus.TERMINAL, Collections.singletonMap("status", "FAILED"));
  }

  @Test
  void isSuccessWhenOortResultIsSucceeded() {
    testOortServiceStatus(ExecutionStatus.SUCCEEDED, Collections.singletonMap("status", "SUCCEEDED"));
  }

  @Test
  void isRunningWhenOortResultIsInProgress() {
    testOortServiceStatus(ExecutionStatus.RUNNING, Collections.singletonMap("status", "IN_PROGRESS"));
  }

  @Test
  void isRunningWhenOortResultsAreEmpty() {
    testOortServiceStatus(ExecutionStatus.RUNNING, Collections.emptyMap());
  }

  @Test
  void isTerminalWhenOortResultsAreNull() {
    testOortServiceStatus(ExecutionStatus.TERMINAL, null);
  }
}
