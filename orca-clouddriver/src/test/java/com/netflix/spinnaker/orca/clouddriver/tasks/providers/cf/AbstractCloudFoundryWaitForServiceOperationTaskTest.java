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
import com.netflix.spinnaker.orca.clouddriver.tasks.servicebroker.AbstractWaitForServiceTask;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

class AbstractCloudFoundryWaitForServiceOperationTaskTest<T extends AbstractWaitForServiceTask> {
  protected final String operationType;
  protected final Function<OortService, T> subjectConstructor;

  AbstractCloudFoundryWaitForServiceOperationTaskTest(
      String operationType, Function<OortService, T> subjectConstructor) {
    this.operationType = operationType;
    this.subjectConstructor = subjectConstructor;
  }

  void testOortServiceStatus(
      ExecutionStatus expectedStatus, @Nullable Map<String, Object> serviceInstance) {
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
        .thenReturn(serviceInstance);

    T task = subjectConstructor.apply(oortService);

    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", cloudProvider);
    context.put("service.account", credentials);
    context.put("service.region", region);
    context.put("service.instance.name", serviceInstanceName);

    TaskResult result =
        task.execute(
            new StageExecutionImpl(
                new PipelineExecutionImpl(PIPELINE, "orca"), operationType, context));

    assertThat(result.getStatus().toString()).isEqualTo(expectedStatus.toString());
  }
}
