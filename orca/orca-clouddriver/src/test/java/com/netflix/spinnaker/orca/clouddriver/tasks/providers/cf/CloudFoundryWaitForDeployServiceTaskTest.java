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

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloudFoundryWaitForDeployServiceTaskTest
    extends AbstractCloudFoundryWaitForServiceOperationTaskTest<
        CloudFoundryWaitForDeployServiceTask> {
  CloudFoundryWaitForDeployServiceTaskTest() {
    super("deployService", CloudFoundryWaitForDeployServiceTask::new);
  }

  @Test
  void isTerminalWhenOortResultIsFailed() {
    testOortServiceStatus(ExecutionStatus.TERMINAL, Collections.singletonMap("status", "FAILED"));
  }

  @Test
  void isSuccessWhenOortResultIsSucceeded() {
    testOortServiceStatus(
        ExecutionStatus.SUCCEEDED, Collections.singletonMap("status", "SUCCEEDED"));
  }

  @Test
  void isRunningWhenOortResultIsInProgress() {
    testOortServiceStatus(
        ExecutionStatus.RUNNING, Collections.singletonMap("status", "IN_PROGRESS"));
  }

  @Test
  void addsLastOperationStatusAndDescriptinoWhenOortResultIsFailed() {
    OortService oortService = mock(OortService.class);
    String credentials = "my-account";
    String cloudProvider = "cloud";
    String region = "org > space";
    String serviceInstanceName = "service-instance-name";
    when(oortService.getServiceInstance(
            matches(credentials),
            matches(cloudProvider),
            matches(region),
            matches(serviceInstanceName)))
        .thenReturn(
            Map.of(
                "status", "FAILED",
                "lastOperationDescription", "Custom description"));

    CloudFoundryWaitForDeployServiceTask task = subjectConstructor.apply(oortService);

    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", cloudProvider);
    context.put("service.account", credentials);
    context.put("service.region", region);
    context.put("service.instance.name", serviceInstanceName);

    TaskResult result =
        task.execute(
            new StageExecutionImpl(
                new PipelineExecutionImpl(PIPELINE, "orca"), operationType, context));

    assertThat(result.getStatus().toString()).isEqualTo(ExecutionStatus.TERMINAL.toString());
    assertThat(result.getOutputs().get("lastOperationStatus")).isEqualTo("FAILED");
    assertThat(result.getOutputs().get("lastOperationDescription")).isEqualTo("Custom description");
    assertThat(result.getOutputs().get("failureMessage")).isEqualTo("Custom description");
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
