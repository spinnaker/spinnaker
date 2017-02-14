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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.DestroyServerGroupStage
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DestroyServerGroupTask extends AbstractServerGroupTask {
  String serverGroupAction = DestroyServerGroupStage.PIPELINE_CONFIG_TYPE

  @Autowired
  TrafficGuard trafficGuard

  @Override
  void validateClusterStatus(Map operation) {
    trafficGuard.verifyTrafficRemoval(operation.serverGroupName as String,
      getCredentials(operation),
      getLocation(operation),
      getCloudProvider(operation), "Destroying")
  }
}
