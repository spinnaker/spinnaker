/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.gce.deploy.description.ResizeGoogleServerGroupDescription

class ResizeGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ResizeGoogleServerGroupDescription description

  ResizeGoogleServerGroupAtomicOperation(ResizeGoogleServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "myapp-dev-v000", "targetSize": 2, "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName in " +
      "$description.zone..."

    def project = description.credentials.project
    def compute = description.credentials.compute
    int targetSize = description.targetSize instanceof Number ? description.targetSize : description.capacity.desired

    compute.instanceGroupManagers().resize(project,
                                           description.zone,
                                           description.serverGroupName,
                                           targetSize).execute()

    task.updateStatus BASE_PHASE, "Done resizing server group $description.serverGroupName in $description.zone."
    null
  }
}
