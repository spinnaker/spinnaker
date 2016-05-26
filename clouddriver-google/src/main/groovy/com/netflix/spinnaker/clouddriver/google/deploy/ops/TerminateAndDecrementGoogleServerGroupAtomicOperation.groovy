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

import com.google.api.services.compute.model.InstanceGroupManagersDeleteInstancesRequest
import com.google.api.services.compute.model.RegionInstanceGroupManagersDeleteInstancesRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * Terminate and delete instances from a managed instance group, and decrement the size of the managed instance group.
 *
 * This operation explicitly deletes and removes specific instances from a managed instance group, decreasing the size
 * of the group by the number of instances removed. The basic {@link TerminateGoogleInstancesAtomicOperation} will
 * delete the instances as this does, however since the managed instance group was not changed, it will create new
 * instances to satisfy its size requirements.
 *
 * This is an alternative to {@link TerminateGoogleInstancesAtomicOperation} using the API described in
 * {@link https://cloud.google.com/compute/docs/reference/latest/instanceGroupManagers/deleteInstances}.
 *
 * This is also an alternative to {@link AbandonAndDecrementGoogleServerGroupAtomicOperation} where the instances are
 * also deleted once removed from the managed instance group.
 *
 * @see TerminateGoogleInstancesAtomicOperation
 */
class TerminateAndDecrementGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_AND_DEC_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateAndDecrementGoogleServerGroupDescription description

  @Autowired
  GoogleClusterProvider googleClusterProvider

  TerminateAndDecrementGoogleServerGroupAtomicOperation(TerminateAndDecrementGoogleServerGroupDescription description) {
    this.description = description
  }

  /**
   * Attempt to terminate the specified instances and remove them from the specified managed instance group.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstanceAndDecrementServerGroup": { "serverGroupName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing terminate and decrement of instances " +
      "(${description.instanceIds.join(", ")}) from server group $description.serverGroupName in $description.region..."

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
    def instanceIds = description.instanceIds
    def instanceUrls = GCEUtil.collectInstanceUrls(serverGroup, instanceIds)

    if (isRegional) {
      def instanceGroupManagers = compute.regionInstanceGroupManagers()
      def deleteRequest = new RegionInstanceGroupManagersDeleteInstancesRequest().setInstances(instanceUrls)

      instanceGroupManagers.deleteInstances(project, region, serverGroupName, deleteRequest).execute()
    } else {
      def instanceGroupManagers = compute.instanceGroupManagers()
      def deleteRequest = new InstanceGroupManagersDeleteInstancesRequest().setInstances(instanceUrls)

      instanceGroupManagers.deleteInstances(project, zone, serverGroupName, deleteRequest).execute()
    }

    task.updateStatus BASE_PHASE, "Done terminating and decrementing instances " +
      "(${description.instanceIds.join(", ")}) from server group $serverGroupName in $region."
    null
  }
}
