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
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.servicebroker.AbstractWaitForServiceTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractCloudFoundryWaitForServiceOperationTaskTest<T extends AbstractWaitForServiceTask> {
  private final String operationType;
  private final Function<OortService, T> subjectConstructor;

  AbstractCloudFoundryWaitForServiceOperationTaskTest(
    String operationType,
    Function<OortService, T> subjectConstructor) {
    this.operationType = operationType;
    this.subjectConstructor = subjectConstructor;
  }

  void testOortServiceStatus(ExecutionStatus expectedStatus, @Nullable Map<String, Object> serviceInstance) {
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

    TaskResult result = task.execute(new Stage(
      new Execution(PIPELINE, "orca"),
      operationType,
      context));

    assertThat(result.getStatus()).isEqualTo(expectedStatus);
  }
}
