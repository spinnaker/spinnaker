/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DisableServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DisableServerGroupTask extends AbstractServerGroupTask {

  @Autowired
  TrafficGuard trafficGuard

  @Override
  protected boolean isAddTargetOpOutputs() {
    true
  }
  String serverGroupAction = DisableServerGroupStage.PIPELINE_CONFIG_TYPE

  @Override
  void validateClusterStatus(Map operation, Moniker moniker) {
    // if any operation has a non-null desiredPercentage that indicates an entire server group is not going away
    // (e.g. in the case of rolling red/black deploy), let's bypass the traffic guard check for now.
    // In the future, we should actually use this percentage to correctly determine if the disable operation is safe
    if (operation.desiredPercentage && operation.desiredPercentage < 100) {
      return
    }

    trafficGuard.verifyTrafficRemoval(operation.serverGroupName as String,
      moniker,
      getCredentials(operation),
      getLocation(operation),
      getCloudProvider(operation), "Disabling")
  }
}
