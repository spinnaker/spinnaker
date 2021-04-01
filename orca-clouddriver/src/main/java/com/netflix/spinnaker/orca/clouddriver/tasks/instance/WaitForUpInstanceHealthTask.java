/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.instance;

import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WaitForUpInstanceHealthTask implements OverridableTimeoutRetryableTask {

  private final CloudDriverService cloudDriverService;

  @Autowired
  public WaitForUpInstanceHealthTask(CloudDriverService cloudDriverService) {
    this.cloudDriverService = cloudDriverService;
  }

  @Override
  public TaskResult execute(StageExecution stage) {
    InstanceHealthCheckInputs inputs = stage.mapTo(InstanceHealthCheckInputs.class);
    return process(inputs);
  }

  protected TaskResult process(InstanceHealthCheckInputs inputs) {
    if (inputs.hasEmptyInterestingHealthProviders()) {
      return TaskResult.SUCCEEDED;
    }
    if (!inputs.hasInstanceIds()) {
      log.warn("No instance ids specified to check health on!");
      return TaskResult.TERMINAL;
    }
    // TODO: look at making this parallel
    boolean stillRunning =
        inputs.getInstanceIds().stream()
            .anyMatch(
                it -> {
                  var instance =
                      cloudDriverService.getInstance(inputs.accountToUse(), inputs.getRegion(), it);
                  return !HealthHelper.someAreUpAndNoneAreDownOrStarting(
                      instance, inputs.getInterestingHealthProviderNames());
                });
    return stillRunning ? TaskResult.RUNNING : TaskResult.SUCCEEDED;
  }

  @Override
  public long getBackoffPeriod() {
    return 5000;
  }

  @Override
  public long getTimeout() {
    return 3600000;
  }
}
