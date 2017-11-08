/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.stereotype.Component

/**
 * Ensure that external scaling policies do not adversely affect a server group as it is in the process of being deployed.
 *
 * It accomplishes this by:
 * - capturing the source server group 'min' capacity prior to this deploy ({@link CaptureSourceServerGroupCapacityTask})
 * - creating a new server group with min = desired ({@link CaptureSourceServerGroupCapacityTask})
 * - restoring min after the deploy has completed ({@link ApplySourceServerGroupCapacityTask})
 */
@Component
class ApplySourceServerGroupCapacityStage implements StageDefinitionBuilder {
  @Override
  void taskGraph(Stage stage, TaskNode.Builder builder) {
    builder
      .withTask("restoreMinCapacity", ApplySourceServerGroupCapacityTask)
      .withTask("waitForCapacityMatch", MonitorKatoTask)
      .withTask("forceCacheRefresh", ServerGroupCacheForceRefreshTask)
  }
}
