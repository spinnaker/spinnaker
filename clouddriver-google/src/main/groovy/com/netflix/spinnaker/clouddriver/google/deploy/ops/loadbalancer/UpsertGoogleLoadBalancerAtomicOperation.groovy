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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HealthCheckReference
import com.google.api.services.compute.model.HttpHealthCheck
import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.TargetPool
import com.google.api.services.compute.model.TargetPoolsAddHealthCheckRequest
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest
import com.google.api.services.compute.model.TargetPoolsRemoveHealthCheckRequest
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSessionAffinity
import org.springframework.beans.factory.annotation.Autowired

class UpsertGoogleLoadBalancerAtomicOperation extends GoogleAtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  @Autowired
  SafeRetry safeRetry

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private final UpsertGoogleLoadBalancerDescription description

  UpsertGoogleLoadBalancerAtomicOperation() {}

  UpsertGoogleLoadBalancerAtomicOperation(UpsertGoogleLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "region": "us-central1", "credentials" : "my-account-name", "loadBalancerName" : "testlb", "loadBalancerType": "NETWORK"}} ]' localhost:7002/gce/ops
   *
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.loadBalancerName " +
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
      description.instances = GCEUtil.queryInstanceUrls(project, region, description.instances, compute, task, BASE_PHASE, this)
    }

    ForwardingRule existingForwardingRule
    TargetPool existingTargetPool
    HttpHealthCheck existingHttpHealthCheck

    // We first devise a plan by setting all of these flags.
    boolean needToUpdateForwardingRule = false
    boolean needToUpdateTargetPool = false
    boolean needToUpdateHttpHealthCheck = false
    boolean needToUpdateSessionAffinity = false
    boolean needToDeleteHttpHealthCheck = false
    boolean needToCreateNewForwardingRule = false
    boolean needToCreateNewTargetPool = false
    boolean needToCreateNewHttpHealthCheck = false


    // Check if there already exists a forwarding rule with the requested name.
    existingForwardingRule =
      GCEUtil.queryRegionalForwardingRule(project, description.loadBalancerName, compute, task, BASE_PHASE, this)

    if (existingForwardingRule) {
      if (description.region != GCEUtil.getLocalName(existingForwardingRule.region)) {
        throw new GoogleOperationException("There is already a network load balancer named " +
          "$description.loadBalancerName (in region ${GCEUtil.getLocalName(existingForwardingRule.region)}). " +
          "Please specify a different name.")
      }

      // If any of these properties are different, we'll need to update the forwarding rule.
      needToUpdateForwardingRule =
        ((description.ipAddress && description.ipAddress != existingForwardingRule.IPAddress)
           || description.ipProtocol != existingForwardingRule.IPProtocol
           || description.portRange != existingForwardingRule.portRange)

      existingTargetPool = GCEUtil.queryTargetPool(
        project, region, GCEUtil.getLocalName(existingForwardingRule.target), compute, task, BASE_PHASE, this)

      if (existingTargetPool) {
        // Existing set of instances is only updated if the instances property is specified on description. We don't
        // want all instances removed from an existing target pool if the instances property is not specified on the
        // description.
        needToUpdateTargetPool =
          (description.instances != null
             || description.healthCheck && !existingTargetPool.healthChecks
             || !description.healthCheck && existingTargetPool.healthChecks)

        needToUpdateSessionAffinity = description.sessionAffinity != GoogleSessionAffinity.valueOf(existingTargetPool.getSessionAffinity());

        if (existingTargetPool.healthChecks) {
          existingHttpHealthCheck = GCEUtil.queryHttpHealthCheck(
            project, GCEUtil.getLocalName(existingTargetPool.healthChecks[0]), compute, task, BASE_PHASE, this)

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
      updateHttpHealthCheck(existingHttpHealthCheck, compute, project)
    } else if (needToCreateNewHttpHealthCheck) {
      createNewHttpHealthCheck(httpHealthChecksResourceLinks, compute, project)
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

      // Handles (1).
      if (description.instances != null) {
        def instancesToAdd =
          (description.instances as Set) - (existingTargetPool.instances as Set)
        def instancesToRemove =
          existingTargetPool.instances ? (existingTargetPool.instances as Set) - (description.instances as Set) : []

        addInstancesToTargetPoolIfNecessary(targetPoolName, instancesToAdd, region, compute, project)

        removeInstancesFromTargetPoolIfNecessary(targetPoolName, instancesToRemove, region, compute, project)
      }

      if (!description.healthCheck && existingTargetPool.healthChecks) {
        // Handles (2).
        targetPoolResourceOperation = removeHttpHealthCheckFromTargetPool(
          targetPoolName, targetPoolResourceOperation, existingTargetPool, existingHttpHealthCheck, region, compute,
          project)
        targetPoolResourceLink = targetPoolResourceOperation.targetLink
      } else if (description.healthCheck && !existingTargetPool.healthChecks) {
        // Handles (3).
        targetPoolResourceOperation = addHttpHealthCheckToTargetPool(
          targetPoolName, targetPoolResourceOperation, httpHealthChecksResourceLinks, region, compute, project)
        targetPoolResourceLink = targetPoolResourceOperation.targetLink
      }
    } else if (needToCreateNewTargetPool) {
      (targetPoolResourceOperation, targetPoolName) = createNewTargetPool(
        targetPoolName, targetPoolResourceOperation, httpHealthChecksResourceLinks, region, compute, project)
      targetPoolResourceLink = targetPoolResourceOperation.targetLink
    } else {
      // We'll need these set just in case a new forwarding rule will be created.
      targetPoolName = existingTargetPool.name
      targetPoolResourceLink = existingTargetPool.selfLink
    }

    // If the target pool was created from scratch or updated we need to wait until that operation completes.
    if (targetPoolResourceOperation) {
      googleOperationPoller.waitForRegionalOperation(compute, project, region, targetPoolResourceOperation.getName(),
        null, task, "target pool " + GCEUtil.getLocalName(targetPoolResourceLink), BASE_PHASE)

      deleteHttpHealthCheckIfNecessary(existingHttpHealthCheck, needToDeleteHttpHealthCheck, compute, project)
    }

    updateForwardingRuleIfNecessary(
      needToUpdateForwardingRule, targetPoolName, targetPoolResourceLink, region, compute, project)

    createNewForwardingRuleIfNecessary(
      needToCreateNewForwardingRule, targetPoolName, targetPoolResourceLink, region, compute, project)

    task.updateStatus BASE_PHASE, "Done upserting load balancer $description.loadBalancerName in $region."
    [loadBalancers: [(region): [name: description.loadBalancerName]]]
  }

  private void updateHttpHealthCheck(HttpHealthCheck existingHttpHealthCheck, Compute compute, String project) {
    def healthCheckName = existingHttpHealthCheck.name
    task.updateStatus BASE_PHASE, "Updating health check $healthCheckName..."

    def httpHealthCheck = GCEUtil.buildHttpHealthCheck(healthCheckName, description.healthCheck)
    // We won't block on this operation since nothing else depends on its completion.
    timeExecute(
        compute.httpHealthChecks().update(project, healthCheckName, httpHealthCheck),
        "compute.httpHealthChecks.update",
        TAG_SCOPE, SCOPE_GLOBAL)
  }

  private void createNewHttpHealthCheck(List<String> httpHealthChecksResourceLinks, Compute compute, String project) {
    def healthCheckName = String.format("%s-%s-%d", description.loadBalancerName,
      Constants.HEALTH_CHECK_NAME_PREFIX, System.currentTimeMillis())
    task.updateStatus BASE_PHASE, "Creating health check $healthCheckName..."

    def httpHealthCheck = GCEUtil.buildHttpHealthCheck(healthCheckName, description.healthCheck)
    def httpHealthCheckResourceOperation = timeExecute(
        compute.httpHealthChecks().insert(project, httpHealthCheck),
        "compute.httpHealthChecks.insert",
        TAG_SCOPE, SCOPE_GLOBAL)
    def httpHealthCheckResourceLink = httpHealthCheckResourceOperation.targetLink

    httpHealthChecksResourceLinks << httpHealthCheckResourceLink

    // If the http health check was created from scratch we need to wait until that operation completes.
    googleOperationPoller.waitForGlobalOperation(compute, project, httpHealthCheckResourceOperation.getName(),
      null, task, "health check " + GCEUtil.getLocalName(httpHealthCheckResourceLink), BASE_PHASE)
  }

  private void addInstancesToTargetPoolIfNecessary(String targetPoolName, Set<String> instancesToAdd, String region,
                                                   Compute compute, String project) {
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
      timeExecute(
          compute.targetPools().addInstance(project, region, targetPoolName, targetPoolsAddInstanceRequest),
          "compute.targetPools.addInstance",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    }
  }

  private void removeInstancesFromTargetPoolIfNecessary(String targetPoolName, Collection instancesToRemove, String region,
                                                        Compute compute, String project) {
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
      timeExecute(
          compute.targetPools().removeInstance(project, region, targetPoolName, targetPoolsRemoveInstanceRequest),
          "compute.targetPools.removeInstance",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    }
  }

  private Operation removeHttpHealthCheckFromTargetPool(String targetPoolName, Operation targetPoolResourceOperation,
                                                        TargetPool existingTargetPool,
                                                        HttpHealthCheck existingHttpHealthCheck, String region,
                                                        Compute compute, String project) {
    task.updateStatus BASE_PHASE, "Removing health check $existingHttpHealthCheck.name..."

    def targetPoolsRemoveHealthCheckRequest = new TargetPoolsRemoveHealthCheckRequest()

    targetPoolsRemoveHealthCheckRequest.healthChecks =
      [new HealthCheckReference(healthCheck: existingTargetPool.healthChecks[0])]

    // The http health check will be deleted down below, once this operation has completed.
    targetPoolResourceOperation = timeExecute(
        compute.targetPools().removeHealthCheck(project, region, targetPoolName, targetPoolsRemoveHealthCheckRequest),
        "compute.targetPools.removeHealthCheck",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)

    targetPoolResourceOperation
  }

  private Operation addHttpHealthCheckToTargetPool(String targetPoolName, Operation targetPoolResourceOperation,
                                                   List<String> httpHealthChecksResourceLinks, String region,
                                                   Compute compute, String project) {
    task.updateStatus BASE_PHASE, "Adding health check ${GCEUtil.getLocalName(httpHealthChecksResourceLinks[0])}..."

    def targetPoolsAddHealthCheckRequest = new TargetPoolsAddHealthCheckRequest()

    targetPoolsAddHealthCheckRequest.healthChecks =
      [new HealthCheckReference(healthCheck: httpHealthChecksResourceLinks[0])]

    targetPoolResourceOperation = timeExecute(
        compute.targetPools().addHealthCheck(project, region, targetPoolName, targetPoolsAddHealthCheckRequest),
        "compute.targetPools.addHealthCheck",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    targetPoolResourceOperation
  }

  private List createNewTargetPool(String targetPoolName, Operation targetPoolResourceOperation,
                                   List<String> httpHealthChecksResourceLinks, String region, Compute compute,
                                   String project) {
    targetPoolName = String.format("%s-%s-%d", description.loadBalancerName, GCEUtil.TARGET_POOL_NAME_PREFIX,
      System.currentTimeMillis())
    task.updateStatus BASE_PHASE, "Creating target pool $targetPoolName in $region..."

    def targetPool = new TargetPool(
      name: targetPoolName,
      healthChecks: httpHealthChecksResourceLinks,
      instances: description.instances,
      sessionAffinity: description.sessionAffinity
    )

    targetPoolResourceOperation = timeExecute(
        compute.targetPools().insert(project, region, targetPool),
        "compute.tagetPools.insert",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    [targetPoolResourceOperation, targetPoolName]
  }

  private void deleteHttpHealthCheckIfNecessary(HttpHealthCheck existingHttpHealthCheck,
                                                boolean needToDeleteHttpHealthCheck, Compute compute, String project) {
    if (needToDeleteHttpHealthCheck) {
      def healthCheckName = existingHttpHealthCheck.name
      task.updateStatus BASE_PHASE, "Deleting health check $healthCheckName..."

      // We won't block on this operation since nothing else depends on its completion.
      timeExecute(compute.httpHealthChecks().delete(project, healthCheckName),
                  "compute.httpHealthChecks.delete",
                  TAG_SCOPE, SCOPE_GLOBAL)
    }
  }

  private void updateForwardingRuleIfNecessary(boolean needToUpdateForwardingRule, String targetPoolName,
                                               String targetPoolResourceLink, String region, Compute compute,
                                               String project) {
    if (needToUpdateForwardingRule) {
      // These properties of the forwarding rule can't be updated, so we must do a delete and create.
      task.updateStatus BASE_PHASE, "Deleting forwarding rule $description.loadBalancerName..."

      def forwardingRuleResourceOperation = timeExecute(
              compute.forwardingRules().delete(project, region, description.loadBalancerName),
              "compute.forwardingRules.delete",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      def forwardingRuleResourceLink = forwardingRuleResourceOperation.targetLink

      googleOperationPoller.waitForRegionalOperation(compute, project, region, forwardingRuleResourceOperation.getName(),
        null, task, "forwarding rule " + GCEUtil.getLocalName(forwardingRuleResourceLink), BASE_PHASE)

      createNewForwardingRuleIfNecessary(true, targetPoolName, targetPoolResourceLink, region, compute, project)
    }
  }

  private void createNewForwardingRuleIfNecessary(boolean needToCreateNewForwardingRule, String targetPoolName,
                                                  String targetPoolResourceLink, String region, Compute compute,
                                                  String project) {
    if (needToCreateNewForwardingRule) {
      task.updateStatus BASE_PHASE, "Creating forwarding rule $description.loadBalancerName to " +
        "$targetPoolName in $region..."

      def forwardingRule = new ForwardingRule(
        name: description.loadBalancerName,
        target: targetPoolResourceLink,
        IPProtocol: description.ipProtocol,
        IPAddress: description.ipAddress,
        portRange: description.portRange
      )

      Operation forwardingRuleOperation = safeRetry.doRetry(
          { timeExecute(
              compute.forwardingRules().insert(project, region, forwardingRule),
              "compute.forwardingRules.insert",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region) },
          "Regional forwarding rule ${description.loadBalancerName}",
          task,
          [400, 403, 412],
          [],
          [action: "insert", phase: BASE_PHASE, operation: "compute.forwardingRules.insert", (TAG_SCOPE): SCOPE_GLOBAL],
          registry
      ) as Operation

      // Orca's orchestration for upserting a Google load balancer does not contain a task
      // to wait for the state of the platform to show that a load balancer was created (for good reason,
      // that would be a complicated operation). Instead, Orca waits for Clouddriver to execute this operation
      // and do a force cache refresh. We should wait for the whole load balancer to be created in the platform
      // before we exit this upsert operation, so we wait for the forwarding rule to be created before continuing
      // so we _know_ the state of the platform when we do a force cache refresh.
      googleOperationPoller.waitForRegionalOperation(compute, project, region, forwardingRuleOperation.getName(),
          null, task, "forwarding rule " + GCEUtil.getLocalName(targetPoolResourceLink), BASE_PHASE)
    }
  }
}
