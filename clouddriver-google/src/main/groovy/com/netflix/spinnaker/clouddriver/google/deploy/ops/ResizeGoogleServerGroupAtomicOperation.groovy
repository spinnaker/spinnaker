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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.ResizeGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class ResizeGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESIZE_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final ResizeGoogleServerGroupDescription description

  @Autowired
  GoogleClusterProvider googleClusterProvider

  ResizeGoogleServerGroupAtomicOperation(ResizeGoogleServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "resizeServerGroup": { "serverGroupName": "myapp-dev-v000", "targetSize": 2, "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing resize of server group $description.serverGroupName in " +
      "$description.region..."

    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def region = description.region
    def serverGroupName = description.serverGroupName
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def isRegional = serverGroup.regional
    // Will return null if this is a regional server group.
    def zone = serverGroup.zone
    int targetSize = description.targetSize instanceof Number ? description.targetSize : description.capacity.desired

    if (isRegional) {
      def instanceGroupManagers = compute.regionInstanceGroupManagers()

      instanceGroupManagers.resize(project,
                                   region,
                                   serverGroupName,
                                   targetSize).execute()
    } else {
      def instanceGroupManagers = compute.instanceGroupManagers()

      instanceGroupManagers.resize(project,
                                   zone,
                                   serverGroupName,
                                   targetSize).execute()
    }

    task.updateStatus BASE_PHASE, "Done resizing server group $serverGroupName in $region."
    null
  }
}
