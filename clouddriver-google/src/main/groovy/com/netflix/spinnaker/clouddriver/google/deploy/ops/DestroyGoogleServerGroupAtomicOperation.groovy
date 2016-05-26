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
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.DestroyGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DestroyGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DestroyGoogleServerGroupDescription description

  @Autowired
  GoogleOperationPoller googleOperationPoller

  @Autowired
  GoogleClusterProvider googleClusterProvider

  DestroyGoogleServerGroupAtomicOperation(DestroyGoogleServerGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "destroyServerGroup": { "serverGroupName": "myapp-dev-v000", "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing destruction of server group $description.serverGroupName in " +
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

    // We create a new instance template for each managed instance group. We need to delete it here.
    def instanceTemplateName = serverGroup.launchConfig.instanceTemplate.name

    task.updateStatus BASE_PHASE, "Identified instance template."

    task.updateStatus BASE_PHASE, "Checking for autoscaler..."

    if (serverGroup.autoscalingPolicy) {
      if (isRegional) {
        def autoscalerDeleteOperation = compute.regionAutoscalers().delete(project, region, serverGroupName).execute()
        def autoscalerDeleteOperationName = autoscalerDeleteOperation.getName()

        task.updateStatus BASE_PHASE, "Waiting on delete operation for autoscaler..."

        // We must make sure the autoscaler is deleted before deleting the managed instance group.
        googleOperationPoller.waitForRegionalOperation(compute, project, region, autoscalerDeleteOperationName, null, task,
            "regional autoscaler $serverGroupName", BASE_PHASE)
      } else {
        def autoscalerDeleteOperation = compute.autoscalers().delete(project, zone, serverGroupName).execute()
        def autoscalerDeleteOperationName = autoscalerDeleteOperation.getName()

        task.updateStatus BASE_PHASE, "Waiting on delete operation for autoscaler..."

        // We must make sure the autoscaler is deleted before deleting the managed instance group.
        googleOperationPoller.waitForZonalOperation(compute, project, zone, autoscalerDeleteOperationName, null, task,
            "zonal autoscaler $serverGroupName", BASE_PHASE)
      }
    }

    def instanceGroupManagerDeleteOperation =
        isRegional
        ? compute.regionInstanceGroupManagers().delete(project, region, serverGroupName).execute()
        : compute.instanceGroupManagers().delete(project, zone, serverGroupName).execute()
    def instanceGroupOperationName = instanceGroupManagerDeleteOperation.getName()

    task.updateStatus BASE_PHASE, "Waiting on delete operation for managed instance group..."

    // We must make sure the managed instance group is deleted before deleting the instance template.
    if (isRegional) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, instanceGroupOperationName, null, task,
          "regional instance group $serverGroupName", BASE_PHASE)
    } else {
      googleOperationPoller.waitForZonalOperation(compute, project, zone, instanceGroupOperationName, null, task,
          "zonal instance group $serverGroupName", BASE_PHASE)
    }

    task.updateStatus BASE_PHASE, "Deleted instance group."

    compute.instanceTemplates().delete(project, instanceTemplateName).execute()

    task.updateStatus BASE_PHASE, "Deleted instance template."

    task.updateStatus BASE_PHASE, "Done destroying server group $serverGroupName in $region."
    null
  }
}
