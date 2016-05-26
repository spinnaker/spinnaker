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

import com.google.api.services.compute.model.InstanceGroupManagersRecreateInstancesRequest
import com.google.api.services.compute.model.RegionInstanceGroupManagersRecreateRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

/**
 * Terminate and delete instances. If the instances are in a managed instance group they will be recreated.
 *
 * If no managed instance group is specified, this operation only explicitly deletes and removes the instances. However,
 * if the instances are in a managed instance group then the manager will automatically recreate and restart the
 * instances once it sees that they are missing. The net effect is to recreate the instances. More information:
 * {@link https://cloud.google.com/compute/docs/instances/stopping-or-deleting-an-instance}
 *
 * If a managed instance group is specified, this becomes a first-class explicit operation on the managed instance
 * group to terminate and recreate the instances. More information:
 * {@link https://cloud.google.com/compute/docs/reference/latest/instanceGroupManagers/recreateInstances}
 */
class TerminateGoogleInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateGoogleInstancesDescription description

  @Autowired
  GoogleClusterProvider googleClusterProvider

  TerminateGoogleInstancesAtomicOperation(TerminateGoogleInstancesDescription description) {
    this.description = description
  }

  /**
   * Attempt to terminate each of the specified instances.
   *
   * If no managed instance group is specified, this will attempt to terminate each of the instances independent of one
   * another. Should any of them throw an exception, the first one will be propagated from this method, but the other
   * attempts will be allowed to complete first. Currently, if others also throw an exception then those exceptions will
   * be lost (however, their failures will be logged in the status).
   *
   * If a managed instance group is specified, we rely on the manager to terminate and recreate the instances.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "serverGroupName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "region": "us-central1", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of instances (${description.instanceIds.join(", ")}) in " +
      "${description.region ?: description.zone}..."

    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def region = description.region
    def serverGroupName = description.serverGroupName
    def instanceIds = description.instanceIds

    if (serverGroupName) {
      task.updateStatus BASE_PHASE, "Recreating instances (${instanceIds.join(", ")}) in server group " +
        "$serverGroupName in $region..."

      def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
      def isRegional = serverGroup.regional
      // Will return null if this is a regional server group.
      def zone = serverGroup.zone
      def instanceUrls = GCEUtil.collectInstanceUrls(serverGroup, instanceIds)

      if (isRegional) {
        def instanceGroupManagers = compute.regionInstanceGroupManagers()
        def recreateRequest = new RegionInstanceGroupManagersRecreateRequest().setInstances(instanceUrls)

        instanceGroupManagers.recreateInstances(project, region, serverGroupName, recreateRequest).execute()
      } else {
        def instanceGroupManagers = compute.instanceGroupManagers()
        def recreateRequest = new InstanceGroupManagersRecreateInstancesRequest().setInstances(instanceUrls)

        instanceGroupManagers.recreateInstances(project, zone, serverGroupName, recreateRequest).execute()
      }

      task.updateStatus BASE_PHASE, "Done recreating instances (${instanceIds.join(", ")}) in $region."
    } else {
      def zone = description.zone
      def firstFailure
      def okIds = []
      def failedIds = []

      for (def instanceId : instanceIds) {
        task.updateStatus BASE_PHASE, "Terminating instance $instanceId in $zone..."

        try {
          compute.instances().delete(project, zone, instanceId).execute()
          okIds.add(instanceId)
        } catch (Exception e) {
          task.updateStatus BASE_PHASE, "Failed to terminate instance $instanceId in $zone: $e.message."
          failedIds.add(instanceId)

          if (!firstFailure) {
            firstFailure = e
          }
        }
      }

      if (firstFailure) {
        task.updateStatus BASE_PHASE, "Failed to terminate instances (${failedIds.join(", ")}) in $zone, but " +
          "successfully terminated instances (${okIds.join(", ")})."
        throw firstFailure
      }

      task.updateStatus BASE_PHASE, "Done terminating instances (${instanceIds.join(", ")}) in $zone."
    }

    null
  }
}
