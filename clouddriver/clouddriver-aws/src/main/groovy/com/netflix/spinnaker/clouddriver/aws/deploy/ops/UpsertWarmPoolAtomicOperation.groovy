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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertWarmPoolDescription
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertWarmPoolAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_WARM_POOL"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertWarmPoolDescription description

  UpsertWarmPoolAtomicOperation(UpsertWarmPoolDescription description) {
    this.description = description
  }

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Override
  Void operate(List priorOutputs) {
    String descriptor = description.asgs.collect { it.toString() }
    task.updateStatus BASE_PHASE, "Initializing Upsert Warm Pool operation for $descriptor..."
    for (asg in description.asgs) {
      upsertWarmPool(asg.serverGroupName, asg.region)
    }
    task.updateStatus BASE_PHASE, "Finished Upsert Warm Pool operation for $descriptor."
    null
  }

  private void upsertWarmPool(String asgName, String region) {
    try {
      def regionScopedProvider = regionScopedProviderFactory.forRegion(description.credentials, region)
      def asgService = regionScopedProvider.asgService
      def asg = asgService.getAutoScalingGroup(asgName)
      if (!asg) {
        task.updateStatus BASE_PHASE, "No ASG named '$asgName' found in $region."
        return
      }
      task.updateStatus BASE_PHASE, "Upserting warm pool (minSize: ${description.minSize}, " +
        "maxGroupPreparedCapacity: ${description.maxGroupPreparedCapacity}, poolState: ${description.poolState}) " +
        "for $asgName in $region..."
      asgService.putWarmPool(
        asgName,
        description.minSize,
        description.maxGroupPreparedCapacity,
        description.poolState,
        description.reuseOnScaleIn
      )
    } catch (e) {
      task.updateStatus BASE_PHASE, "Could not upsert warm pool for ASG '$asgName' in region $region! Reason: $e.message"
    }
  }
}
