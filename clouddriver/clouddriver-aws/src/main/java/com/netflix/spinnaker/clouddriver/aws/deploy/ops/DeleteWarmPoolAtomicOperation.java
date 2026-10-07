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
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteWarmPoolDescription;
import com.netflix.spinnaker.clouddriver.aws.services.AsgService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.autoscaling.model.AutoScalingGroup;

public class DeleteWarmPoolAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_WARM_POOL";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private final DeleteWarmPoolDescription description;

  @Autowired RegionScopedProviderFactory regionScopedProviderFactory;

  public DeleteWarmPoolAtomicOperation(DeleteWarmPoolDescription description) {
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
            BASE_PHASE, "Initializing Delete Warm Pool operation for " + descriptor + "...");
    for (AsgDescription asg : description.getAsgs()) {
      deleteWarmPool(asg.getServerGroupName(), asg.getRegion());
    }
    getTask()
        .updateStatus(BASE_PHASE, "Finished Delete Warm Pool operation for " + descriptor + ".");
    return null;
  }

  private void deleteWarmPool(String asgName, String region) {
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
          .updateStatus(BASE_PHASE, "Deleting warm pool for " + asgName + " in " + region + "...");
      asgService.deleteWarmPool(asgName, description.getForceDelete());
    } catch (Exception e) {
      getTask()
          .updateStatus(
              BASE_PHASE,
              "Could not delete warm pool for ASG '"
                  + asgName
                  + "' in region "
                  + region
                  + "! Reason: "
                  + e.getMessage());
    }
  }
}
