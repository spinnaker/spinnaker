/*
 * Copyright 2014 Google, Inc.
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

import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEOperationUtil
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class CreateGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "CREATE_NETWORK_LOAD_BALANCER"
  private static final String HEALTH_CHECK_NAME_PREFIX = "health-check"
  private static final String IP_PROTOCOL = "TCP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateGoogleNetworkLoadBalancerDescription description

  CreateGoogleNetworkLoadBalancerAtomicOperation(CreateGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createGoogleNetworkLoadBalancerDescription": { "region": "us-central1", "credentials" : "my-account-name", "networkLoadBalancerName" : "testlb" }} ]' localhost:8501/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing create of network load balancer $description.networkLoadBalancerName " +
      "in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region

    def httpHealthCheckResourceOperation
    def httpHealthCheckResourceLink

    def httpHealthChecksResourceLinks = new ArrayList<String>()
    if (description.healthCheck) {
      def health_check_name = String.format("%s-%s-%d", description.networkLoadBalancerName, HEALTH_CHECK_NAME_PREFIX,
          System.currentTimeMillis())
      task.updateStatus BASE_PHASE, "Creating health check $health_check_name..."
      def httpHealthCheck = GCEUtil.buildHttpHealthCheck(health_check_name, description.healthCheck)
      httpHealthCheckResourceOperation =
          compute.httpHealthChecks().insert(project, httpHealthCheck).execute()
      httpHealthCheckResourceLink = httpHealthCheckResourceOperation.getTargetLink()
      httpHealthChecksResourceLinks.add(httpHealthCheckResourceLink)
    }

    def target_pool_name = String.format("%s-%s-%d", description.networkLoadBalancerName, GCEUtil.TARGET_POOL_NAME_PREFIX,
        System.currentTimeMillis())
    task.updateStatus BASE_PHASE, "Creating target pool $target_pool_name in $region..."

    def targetPool = new TargetPool(
        name: target_pool_name,
        healthChecks: httpHealthChecksResourceLinks,
        // TODO(odedmeri): We expect the instances in the description to be URLs but we should accept local names
        // and query them to get the URLs.
        instances: description.instances
    )

    if (description.healthCheck) {
      // Before building the target pool we must check and wait until the health check is built.
      GCEOperationUtil.waitForGlobalOperation(compute, project, httpHealthCheckResourceOperation.getName(),
          null, task, "health check " + GCEUtil.getLocalName(httpHealthCheckResourceLink), BASE_PHASE)
    }

    def targetPoolResourceOperation = compute.targetPools().insert(project, region, targetPool).execute()
    def targetPoolResourceLink = targetPoolResourceOperation.getTargetLink()

    task.updateStatus BASE_PHASE, "Creating forwarding rule $description.networkLoadBalancerName to " +
        "$target_pool_name in $region..."

    def forwarding_rule = new ForwardingRule(name: description.networkLoadBalancerName,
                                             target: targetPoolResourceLink,
                                             IPProtocol: IP_PROTOCOL,
                                             IPAddress: description.ipAddress,
                                             portRange: description.portRange)


    // Before building the forwarding rule we must check and wait until the target pool is built.
    GCEOperationUtil.waitForRegionalOperation(compute, project, region, targetPoolResourceOperation.getName(),
        null, task, "target pool " + GCEUtil.getLocalName(targetPoolResourceLink), BASE_PHASE)

    compute.forwardingRules().insert(project, region, forwarding_rule).execute()

    task.updateStatus BASE_PHASE, "Done creating network load balancer $description.networkLoadBalancerName in $region."
    null
  }
}
