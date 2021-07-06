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

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.tasks.servicebroker.AbstractWaitForServiceTask;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudFoundryWaitForDeployServiceTask extends AbstractWaitForServiceTask {
  @Autowired
  public CloudFoundryWaitForDeployServiceTask(OortService oortService) {
    super(oortService);
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);
    String account = stage.mapTo("/service.account", String.class);
    String region = stage.mapTo("/service.region", String.class);
    String serviceInstanceName = stage.mapTo("/service.instance.name", String.class);

    Map<String, Object> serviceInstance =
        oortService.getServiceInstance(account, cloudProvider, region, serviceInstanceName);

    ExecutionStatus status = oortStatusToTaskStatus(serviceInstance);

    TaskResult.TaskResultBuilder taskResultBuilder = TaskResult.builder(status);
    Optional.ofNullable(serviceInstance)
        .ifPresent(
            (si) -> {
              String lastOperationDescription =
                  Optional.ofNullable(serviceInstance.get("lastOperationDescription"))
                      .orElse("Failed to get last operation description")
                      .toString();
              taskResultBuilder
                  .output(
                      "lastOperationStatus",
                      Optional.ofNullable(serviceInstance.get("status")).orElse("").toString())
                  .output("lastOperationDescription", lastOperationDescription);
              if (status == ExecutionStatus.TERMINAL) {
                taskResultBuilder.output("failureMessage", lastOperationDescription);
              }
            });

    return taskResultBuilder.build();
  }

  @Override
  protected ExecutionStatus oortStatusToTaskStatus(Map m) {
    return Optional.ofNullable(m)
        .map(
            myMap -> {
              String state = Optional.ofNullable(myMap.get("status")).orElse("").toString();
              switch (state) {
                case "FAILED":
                  return ExecutionStatus.TERMINAL;
                case "SUCCEEDED":
                  return ExecutionStatus.SUCCEEDED;
                case "IN_PROGRESS":
                default:
                  return ExecutionStatus.RUNNING;
              }
            })
        .orElse(ExecutionStatus.TERMINAL);
  }
}
