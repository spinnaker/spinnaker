/*
 * Copyright 2016 Google, Inc.
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
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteGoogleAutoscalingPolicyAtomicOperation implements AtomicOperation<Void>{

  private static final String BASE_PHASE = "DELETE_SCALING_POLICY"
  private final DeleteGoogleAutoscalingPolicyDescription description

  @Autowired
  private GoogleClusterProvider googleClusterProvider

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteGoogleAutoscalingPolicyAtomicOperation(DeleteGoogleAutoscalingPolicyDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteScalingPolicy": { "serverGroupName": "autoscale-regional", "credentials": "my-google-account", "region": "us-central1" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteScalingPolicy": { "serverGroupName": "autoscale-zonal", "credentials": "my-google-account", "region": "us-central1" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {

    task.updateStatus BASE_PHASE, "Initializing deletion of scaling policy for $description.serverGroupName..."

    def credentials = description.credentials
    def serverGroupName = description.serverGroupName
    def project = credentials.project
    def compute = credentials.compute
    def accountName = description.accountName
    def region = description.region
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def isRegional = serverGroup.regional
    def zone = serverGroup.zone

    if (isRegional) {
      compute.regionAutoscalers().delete(project, region, serverGroupName).execute()
    } else {
      compute.autoscalers().delete(project, zone, serverGroupName).execute()
    }

    task.updateStatus BASE_PHASE, "Done deleting scaling policy for $serverGroupName..."
    null
  }
}
