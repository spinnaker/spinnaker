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

package com.netflix.spinnaker.kato.gce.deploy.ops.loadbalancer

import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthCheckReference
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.TargetPool
import com.google.api.services.compute.model.TargetPoolsAddHealthCheckRequest
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest
import com.google.api.services.compute.model.TargetPoolsRemoveHealthCheckRequest
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEOperationUtil
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class UpsertGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_NETWORK_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertGoogleNetworkLoadBalancerDescription description

  UpsertGoogleNetworkLoadBalancerAtomicOperation(UpsertGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertGoogleNetworkLoadBalancerDescription": { "region": "us-central1", "credentials" : "my-account-name", "networkLoadBalancerName" : "testlb" }} ]' localhost:8501/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of network load balancer $description.networkLoadBalancerName " +
      "in $description.region..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def region = description.region

    // Set some default values that will be useful when doing comparisons.
    description.ipProtocol = description.ipProtocol ?: Constants.DEFAULT_IP_PROTOCOL
    description.portRange = description.portRange ?: Constants.DEFAULT_PORT_RANGE

    if (description.healthCheck) {
      description.healthCheck.with {
        checkIntervalSec = checkIntervalSec ?: Constants.DEFAULT_CHECK_INTERVAL_SEC
        healthyThreshold = healthyThreshold ?: Constants.DEFAULT_HEALTHY_THRESHOLD
        unhealthyThreshold = unhealthyThreshold ?: Constants.DEFAULT_UNHEALTHY_THRESHOLD
        port = port ?: Constants.DEFAULT_PORT
        timeoutSec = timeoutSec ?: Constants.DEFAULT_TIMEOUT_SEC
        requestPath = requestPath ?: Constants.DEFAULT_REQUEST_PATH
      }
    }

    // If specified, replace the instance local names with full urls.
    if (description.instances) {
      description.instances = GCEUtil.queryInstanceUrls(project, region, description.instances, compute, task, BASE_PHASE)
    }

    ForwardingRule existingForwardingRule
    TargetPool existingTargetPool
    HttpHealthCheck existingHttpHealthCheck

    // We first devise a plan by setting all of these flags.
    boolean needToUpdateForwardingRule = false
    boolean needToUpdateTargetPool = false
    boolean needToUpdateHttpHealthCheck = false
    boolean needToDeleteHttpHealthCheck = false
    boolean needToCreateNewForwardingRule = false
    boolean needToCreateNewTargetPool = false
    boolean needToCreateNewHttpHealthCheck = false

    // Check if there already exists a forwarding rule with the requested name.
    existingForwardingRule =
      GCEUtil.queryRegionalForwardingRule(project, region, description.networkLoadBalancerName, compute, task, BASE_PHASE)

    if (existingForwardingRule) {
      // If any of these properties are different, we'll need to update the forwarding rule.
      needToUpdateForwardingRule =
        ((description.ipAddress && description.ipAddress != existingForwardingRule.IPAddress)
           || description.ipProtocol != existingForwardingRule.IPProtocol
           || description.portRange != existingForwardingRule.portRange)

      existingTargetPool = GCEUtil.queryTargetPool(
        project, region, GCEUtil.getLocalName(existingForwardingRule.target), compute, task, BASE_PHASE)

      if (existingTargetPool) {
        // Existing set of instances is only updated if the instances property is specified on description. We don't
        // want all instances removed from an existing target pool if the instances property is not specified on the
        // description.
        needToUpdateTargetPool =
          (description.instances != null
             || description.healthCheck && !existingTargetPool.healthChecks
             || !description.healthCheck && existingTargetPool.healthChecks)

        if (existingTargetPool.healthChecks) {
          existingHttpHealthCheck = GCEUtil.queryHttpHealthCheck(
            project, GCEUtil.getLocalName(existingTargetPool.healthChecks[0]), compute, task, BASE_PHASE)

          if (description.healthCheck) {
            // If any of these properties are different, we'll need to update the http health check.
            needToUpdateHttpHealthCheck =
              (description.healthCheck.checkIntervalSec != existingHttpHealthCheck.checkIntervalSec
                 || description.healthCheck.healthyThreshold != existingHttpHealthCheck.healthyThreshold
                 || description.healthCheck.unhealthyThreshold != existingHttpHealthCheck.unhealthyThreshold
                 || description.healthCheck.port != existingHttpHealthCheck.port
                 || description.healthCheck.timeoutSec != existingHttpHealthCheck.timeoutSec
                 || description.healthCheck.requestPath != existingHttpHealthCheck.requestPath)
          } else {
            // If there is an existing http health check, but the description does not specify one, we want to make
            // sure we delete the existing one after disassociating it from the target pool.
            needToDeleteHttpHealthCheck = true
          }

        } else {
          needToCreateNewHttpHealthCheck = description.healthCheck
        }
      } else {
        task.updateStatus BASE_PHASE,
          "Unable to retrieve referenced target pool ${GCEUtil.getLocalName(existingForwardingRule.target)}."

        needToCreateNewTargetPool = true
        needToCreateNewHttpHealthCheck = description.healthCheck
      }
    } else {
      // If there is not an existing forwarding rule, we'll need to create everything from scratch.
      needToCreateNewForwardingRule = true
      needToCreateNewTargetPool = true
      needToCreateNewHttpHealthCheck = description.healthCheck
    }

    def httpHealthChecksResourceLinks = []

    if (needToUpdateHttpHealthCheck) {
      def healthCheckName = existingHttpHealthCheck.name
      task.updateStatus BASE_PHASE, "Updating health check $healthCheckName..."

      def httpHealthCheck = GCEUtil.buildHttpHealthCheck(healthCheckName, description.healthCheck)
      // We won't block on this operation since nothing else depends on its completion.
      compute.httpHealthChecks().update(project, healthCheckName, httpHealthCheck).execute()
    } else if (needToCreateNewHttpHealthCheck) {
      def healthCheckName = String.format("%s-%s-%d", description.networkLoadBalancerName,
        Constants.HEALTH_CHECK_NAME_PREFIX, System.currentTimeMillis())
      task.updateStatus BASE_PHASE, "Creating health check $healthCheckName..."

      def httpHealthCheck = GCEUtil.buildHttpHealthCheck(healthCheckName, description.healthCheck)
      def httpHealthCheckResourceOperation =
        compute.httpHealthChecks().insert(project, httpHealthCheck).execute()
      def httpHealthCheckResourceLink = httpHealthCheckResourceOperation.targetLink

      httpHealthChecksResourceLinks << httpHealthCheckResourceLink

      // If the http health check was created from scratch we need to wait until that operation completes.
      GCEOperationUtil.waitForGlobalOperation(compute, project, httpHealthCheckResourceOperation.getName(),
        null, task, "health check " + GCEUtil.getLocalName(httpHealthCheckResourceLink), BASE_PHASE)
    }

    def targetPoolName
    def targetPoolResourceOperation
    def targetPoolResourceLink

    // There's a chance that the target pool doesn't in fact get updated. If needToUpdateTargetPool was set because
    // the description.instances property was specified, but the specified set matches the existing set of instances,
    // no updates will be made to the target pool.
    if (needToUpdateTargetPool) {
      targetPoolName = existingTargetPool.name
      task.updateStatus BASE_PHASE, "Updating target pool $targetPoolName in $region..."

      // If a target pool requires updating, it means at least one of the following is true:
      //   1) The instances property was specified in the description (may be empty).
      //   2) The target pool has a health check but it should be updated to not have one.
      //   3) The target pool does not have a health check but it should be updated to have one.

      if (description.instances != null) {
        def instancesToAdd = (description.instances as Set) - (existingTargetPool.instances as Set)
        def instancesToRemove =
          existingTargetPool.instances ? (existingTargetPool.instances as Set) - (description.instances as Set) : []

        if (instancesToAdd) {
          def instanceLocalNamesToAdd = instancesToAdd.collect { instanceUrl ->
            GCEUtil.getLocalName(instanceUrl)
          }

          task.updateStatus BASE_PHASE, "Adding instances $instanceLocalNamesToAdd..."

          def targetPoolsAddInstanceRequest = new TargetPoolsAddInstanceRequest()
          targetPoolsAddInstanceRequest.instances = instancesToAdd.collect { instanceUrl ->
            new InstanceReference(instance: instanceUrl)
          }
          // We won't block on this operation since nothing else depends on its completion.
          compute.targetPools().addInstance(project, region, targetPoolName, targetPoolsAddInstanceRequest).execute()
        }

        if (instancesToRemove) {
          def instanceLocalNamesToRemove = instancesToRemove.collect { instanceUrl ->
            GCEUtil.getLocalName(instanceUrl)
          }

          task.updateStatus BASE_PHASE, "Removing instances $instanceLocalNamesToRemove..."

          def targetPoolsRemoveInstanceRequest = new TargetPoolsRemoveInstanceRequest()
          targetPoolsRemoveInstanceRequest.instances = instancesToRemove.collect { instanceUrl ->
            new InstanceReference(instance: instanceUrl)
          }
          // We won't block on this operation since nothing else depends on its completion.
          compute.targetPools().removeInstance(project, region, targetPoolName, targetPoolsRemoveInstanceRequest).execute()
        }
      }

      if (!description.healthCheck && existingTargetPool.healthChecks) {
        task.updateStatus BASE_PHASE, "Removing health check $existingHttpHealthCheck.name..."

        def targetPoolsRemoveHealthCheckRequest = new TargetPoolsRemoveHealthCheckRequest()

        targetPoolsRemoveHealthCheckRequest.healthChecks =
          [new HealthCheckReference(healthCheck: existingTargetPool.healthChecks[0])]

        // The http health check will be deleted down below, once this operation has completed.
        targetPoolResourceOperation =
          compute.targetPools().removeHealthCheck(
            project, region, targetPoolName, targetPoolsRemoveHealthCheckRequest).execute()
      } else if (description.healthCheck && !existingTargetPool.healthChecks) {
        task.updateStatus BASE_PHASE, "Adding health check ${GCEUtil.getLocalName(httpHealthChecksResourceLinks[0])}..."

        def targetPoolsAddHealthCheckRequest = new TargetPoolsAddHealthCheckRequest()

        targetPoolsAddHealthCheckRequest.healthChecks =
          [new HealthCheckReference(healthCheck: httpHealthChecksResourceLinks[0])]

        targetPoolResourceOperation =
          compute.targetPools().addHealthCheck(project, region, targetPoolName, targetPoolsAddHealthCheckRequest).execute()
      }
    } else if (needToCreateNewTargetPool) {
      targetPoolName = String.format("%s-%s-%d", description.networkLoadBalancerName, GCEUtil.TARGET_POOL_NAME_PREFIX,
        System.currentTimeMillis())
      task.updateStatus BASE_PHASE, "Creating target pool $targetPoolName in $region..."

      def targetPool = new TargetPool(
        name: targetPoolName,
        healthChecks: httpHealthChecksResourceLinks,
        instances: description.instances
      )

      targetPoolResourceOperation = compute.targetPools().insert(project, region, targetPool).execute()
    } else {
      // We'll need these set just in case a new forwarding rule will be created.
      targetPoolName = existingTargetPool.name
      targetPoolResourceLink = existingTargetPool.selfLink
    }

    if (targetPoolResourceOperation) {
      // If the target pool was created from scratch or updated we need to wait until that operation completes.
      targetPoolResourceLink = targetPoolResourceOperation.targetLink

      GCEOperationUtil.waitForRegionalOperation(compute, project, region, targetPoolResourceOperation.getName(),
        null, task, "target pool " + GCEUtil.getLocalName(targetPoolResourceLink), BASE_PHASE)

      if (needToDeleteHttpHealthCheck) {
        def healthCheckName = existingHttpHealthCheck.name
        task.updateStatus BASE_PHASE, "Deleting health check $healthCheckName..."

        // We won't block on this operation since nothing else depends on its completion.
        compute.httpHealthChecks().delete(project, healthCheckName).execute()
      }
    }

    if (needToUpdateForwardingRule) {
      // These properties of the forwarding rule can't be updated, so we must do a delete and create.
      task.updateStatus BASE_PHASE, "Deleting forwarding rule $description.networkLoadBalancerName..."

      def forwardingRuleResourceOperation =
        compute.forwardingRules().delete(project, region, description.networkLoadBalancerName).execute()
      def forwardingRuleResourceLink = forwardingRuleResourceOperation.targetLink

      GCEOperationUtil.waitForRegionalOperation(compute, project, region, forwardingRuleResourceOperation.getName(),
        null, task, "forwarding rule " + GCEUtil.getLocalName(forwardingRuleResourceLink), BASE_PHASE)

      needToCreateNewForwardingRule = true
    }

    if (needToCreateNewForwardingRule) {
      task.updateStatus BASE_PHASE, "Creating forwarding rule $description.networkLoadBalancerName to " +
        "$targetPoolName in $region..."

      def forwardingRule = new ForwardingRule(
        name: description.networkLoadBalancerName,
        target: targetPoolResourceLink,
        IPProtocol: description.ipProtocol,
        IPAddress: description.ipAddress,
        portRange: description.portRange
      )

      // We won't block on this operation since nothing else depends on its completion.
      compute.forwardingRules().insert(project, region, forwardingRule).execute()
    }

    task.updateStatus BASE_PHASE, "Done upserting network load balancer $description.networkLoadBalancerName in $region."
    null
  }
}
