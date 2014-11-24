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

package com.netflix.spinnaker.kato.deploy.gce.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.TargetPool
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.gce.GCEUtil
import com.netflix.spinnaker.kato.deploy.gce.description.CreateGoogleNetworkLBDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class CreateGoogleNetworkLBAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "CREATE_NETWORK_LB"
  private static final String HEALTH_CHECK_NAME_PREFIX = "health-check"
  private static final String TARGET_POOL_NAME_PREFIX = "target-pool"
  private static final String IP_PROTOCOL = "TCP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateGoogleNetworkLBDescription description

  CreateGoogleNetworkLBAtomicOperation(CreateGoogleNetworkLBDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing create of network load balancer $description.networkLBName in " +
      "$description.zone..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def region = GCEUtil.getRegionFromZone(project, zone, compute)

    def health_check_name = String.format("%s-%s-%d", description.networkLBName, HEALTH_CHECK_NAME_PREFIX,
        System.currentTimeMillis())
    task.updateStatus BASE_PHASE, "Creating health check $health_check_name..."

    def httpHealthChecksResourceLinks = new ArrayList<String>()
    if (description.healthCheck) {
      task.updateStatus BASE_PHASE, "Creating health check $health_check_name..."
      def httpHealthCheck = GCEUtil.buildHttpHealthCheck(health_check_name, description.healthCheck)
      def httpHealthCheckResourceLink =
          compute.httpHealthChecks().insert(project, httpHealthCheck).execute().getTargetLink()
      httpHealthChecksResourceLinks.add(httpHealthCheckResourceLink)
    }

    def target_pool_name = String.format("%s-%s-%d", description.networkLBName, TARGET_POOL_NAME_PREFIX,
        System.currentTimeMillis())
    task.updateStatus BASE_PHASE, "Creating target pool $target_pool_name in $region..."

    def targetPool = new TargetPool(
        name: target_pool_name,
        healthChecks: httpHealthChecksResourceLinks,
        // TODO(odedmeri): We expect the instances in the description to be URLs but we should accept local names
        // and query them.to get the URLs.
        instances: description.instances
    )
    def targetPoolResourceLink = compute.targetPools().insert(project, region, targetPool).execute().getTargetLink()

    task.updateStatus BASE_PHASE, "Creating forwarding rule $description.networkLBName to $target_pool_name in " +
        "$region..."

    def forwarding_rule = new ForwardingRule(name: description.networkLBName,
                                             target: targetPoolResourceLink,
                                             IPProtocol: IP_PROTOCOL,
                                             IPAddress: description.ipAddress,
                                             portRange: description.portRange)
    compute.forwardingRules().insert(project, region, forwarding_rule).execute()

    task.updateStatus BASE_PHASE, "Done creating network load balancer $description.networkLBName in $region."
    null
  }
}
