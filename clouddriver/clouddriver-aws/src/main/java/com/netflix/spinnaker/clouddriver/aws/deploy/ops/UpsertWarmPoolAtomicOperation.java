/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.aws.services.AsgService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;

public class UpsertWarmPoolAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "UPSERT_WARM_POOL";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final UpsertWarmPoolDescription description;

  @Autowired RegionScopedProviderFactory regionScopedProviderFactory;

  public UpsertWarmPoolAtomicOperation(UpsertWarmPoolDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    String descriptor =
        description.getAsgs().stream()
            .map(AsgDescription::toString)
            .collect(Collectors.joining(", "));
    getTask()
        .updateStatus(
            BASE_PHASE, "Initializing Upsert Warm Pool operation for " + descriptor + "...");
    for (AsgDescription asg : description.getAsgs()) {
      upsertWarmPool(asg.getServerGroupName(), asg.getRegion());
    }
    getTask()
        .updateStatus(BASE_PHASE, "Finished Upsert Warm Pool operation for " + descriptor + ".");
    return null;
  }

  private void upsertWarmPool(String asgName, String region) {
    try {
      RegionScopedProviderFactory.RegionScopedProvider regionScopedProvider =
          regionScopedProviderFactory.forRegion(description.getCredentials(), region);
      AsgService asgService = regionScopedProvider.getAsgService();
      AutoScalingGroup asg = asgService.getAutoScalingGroup(asgName);
      if (asg == null) {
        getTask()
            .updateStatus(BASE_PHASE, "No ASG named '" + asgName + "' found in " + region + ".");
        return;
      }
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Upserting warm pool (minSize: "
                  + description.getMinSize()
                  + ", maxGroupPreparedCapacity: "
                  + description.getMaxGroupPreparedCapacity()
                  + ", poolState: "
                  + description.getPoolState()
                  + ") for "
                  + asgName
                  + " in "
                  + region
                  + "...");
      asgService.putWarmPool(
          asgName,
          description.getMinSize(),
          description.getMaxGroupPreparedCapacity(),
          description.getPoolState(),
          description.getReuseOnScaleIn());
    } catch (Exception e) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Could not upsert warm pool for ASG '"
                  + asgName
                  + "' in region "
                  + region
                  + "! Reason: "
                  + e.getMessage());
    }
  }
}
