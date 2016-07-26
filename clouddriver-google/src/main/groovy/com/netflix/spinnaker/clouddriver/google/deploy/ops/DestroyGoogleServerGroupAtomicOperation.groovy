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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.Metadata
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.DestroyGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationTimedOutException
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import java.util.concurrent.TimeUnit

@Slf4j
class DestroyGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final int MAX_DELETE_RETRIES = 10
  private static final int DELETE_RETRY_INTERVAL_SECONDS = 10
  private static final String BASE_PHASE = "DESTROY_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DestroyGoogleServerGroupDescription description

  @Autowired
  GoogleOperationPoller googleOperationPoller

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

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
      destroy(destroyAutoscaler(compute, serverGroupName, project, region, zone, isRegional), "autoscaler")
      task.updateStatus BASE_PHASE, "Deleted autoscaler"
    }

    task.updateStatus BASE_PHASE, "Checking for associated HTTP(S) load balancer backend services..."

    destroy(destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider), "Http load balancer backend")

    destroy(destroyInstanceGroup(compute, serverGroupName, project, region, zone, isRegional), "instance group")

    task.updateStatus BASE_PHASE, "Deleted instance group."

    destroy(destroyInstanceTemplate(compute, instanceTemplateName, project), "instance template")

    task.updateStatus BASE_PHASE, "Deleted instance template."

    task.updateStatus BASE_PHASE, "Done destroying server group $serverGroupName in $region."
    null
  }

  static Void destroy(Closure operation, String resource) {
    try {
      task.updateStatus BASE_PHASE, "Attempting destroy of $resource..."
      operation()
      // If the operation times out, then try
      // 1. Deleting again ->
      //   a. On failure (404) we treat this as a success.
      //   b. On failure (400 meaning not ready) retry.
      //   d. On failure (anything else) propagate error.
      //   c. On timeout retry.
      //   e. On success exit happily.
    } catch (GoogleOperationTimedOutException _) {
      log.warn "Initial delete of $resource timed out, retrying..."

      int tries = 1
      while (tries < MAX_DELETE_RETRIES) {
        try {
          tries++
          sleep(TimeUnit.SECONDS.toMillis(DELETE_RETRY_INTERVAL_SECONDS))
          log.warn "Delete $resource attempt #$tries..."
          operation()
          log.warn "Delete $resource attempt #$tries succeeded"
          return
        } catch (GoogleJsonResponseException jsonException) {
          if (jsonException.statusCode == 404) {
            log.warn "Retry delete of ${resource} encountered 404, treating as success..."
            return
          } else if (jsonException.statusCode == 400) {
            log.warn "Retry delete of ${resource} encountered 400, trying again..."
          } else {
            throw jsonException
          }
        } catch (GoogleOperationTimedOutException __) {
          log.warn "Retry delete timed out again, trying again..."
        }
      }

      throw new GoogleOperationException("Failed to delete $resource after #$tries")
    }
  }

  Closure destroyInstanceTemplate(Compute compute, String instanceTemplateName, String project) {
    return {
      compute.instanceTemplates().delete(project, instanceTemplateName).execute()
      null
    }
  }

  Closure destroyAutoscaler(Compute compute, String serverGroupName, String project, String region, String zone, Boolean isRegional) {
    return {
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
      null
    }
  }

  Closure destroyHttpLoadBalancerBackends(Compute compute,
                                          String project,
                                          GoogleServerGroup.View serverGroup,
                                          GoogleLoadBalancerProvider googleLoadBalancerProvider) {
    return {
      def serverGroupName = serverGroup.name
      def parsedServerGroupName = Names.parseName(serverGroupName)
      def foundLoadBalancers = GCEUtil.queryAllLoadBalancers(googleLoadBalancerProvider, serverGroup.loadBalancers as List, parsedServerGroupName.app, task, BASE_PHASE)
      def foundHttpLoadBalancers = foundLoadBalancers.findAll { it.loadBalancerType == GoogleLoadBalancerType.HTTP.toString()}

      if (foundHttpLoadBalancers) {
        Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
        Map metadataMap = GCEUtil.buildMapFromMetadata(instanceMetadata)
        List<String> backendServiceNames = metadataMap?.(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)?.split(",")
        if (backendServiceNames) {
          backendServiceNames.each { String backendServiceName ->
            BackendService backendService = compute.backendServices().get(project, backendServiceName).execute()
            backendService.backends.removeAll { Backend backend ->
              GCEUtil.getLocalName(backend.group) == serverGroupName
            }
            compute.backendServices().update(project, backendServiceName, backendService).execute()
            task.updateStatus BASE_PHASE, "Deleted backend for server group ${serverGroupName} from load balancer backend service ${backendServiceName}."
          }
        }
      }
      null
    }
  }

  Closure destroyInstanceGroup(Compute compute, String serverGroupName, String project, String region, String zone, Boolean isRegional) {
    return {
      def instanceGroupManagerDeleteOperation = isRegional ?
        compute.regionInstanceGroupManagers().delete(project, region, serverGroupName).execute() :
        compute.instanceGroupManagers().delete(project, zone, serverGroupName).execute()

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
      null
    }
  }
}
