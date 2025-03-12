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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.google.api.services.appengine.v1.model.Operation
import com.google.api.services.appengine.v1.model.Service
import com.google.api.services.appengine.v1.model.TrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineSafeRetry
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class UpsertAppengineLoadBalancerAtomicOperation extends AppengineAtomicOperation<Map> {
  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final UpsertAppengineLoadBalancerDescription description

  boolean retryApiCall = true

  @Autowired
  AppengineLoadBalancerProvider appengineLoadBalancerProvider

  @Autowired
  AppengineSafeRetry safeRetry

  UpsertAppengineLoadBalancerAtomicOperation(UpsertAppengineLoadBalancerDescription description, boolean retryApiCall) {
    this(description)
    this.retryApiCall = retryApiCall
  }

  UpsertAppengineLoadBalancerAtomicOperation(UpsertAppengineLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "loadBalancerName": "default", "credentials": "my-appengine-account", "migrateTraffic": false, "split": { "shardBy": "COOKIE" } } } ]' localhost:7002/appengine/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertLoadBalancer": { "loadBalancerName": "default", "credentials": "my-appengine-account", "migrateTraffic": false, "split": { "shardBy": "IP", "allocations": { "app-stack-detail-v000": "0.5", "app-stack-detail-v001": "0.5" } } } } ]' localhost:7002/appengine/ops
   */
  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing upsert of load balancer $description.loadBalancerName " +
      "in $description.credentials.region..."

    def credentials = description.credentials
    def loadBalancerName = description.loadBalancerName
    def updateSplit = description.split
    def ancestorLoadBalancer = appengineLoadBalancerProvider.getLoadBalancer(credentials.name, loadBalancerName)
    def override = copyAndOverrideAncestorSplit(ancestorLoadBalancer.split, updateSplit)

    def service = new Service(
      split: new TrafficSplit(
        allocations: override.allocations,
        shardBy: override.shardBy ? override.shardBy.toString() : null
      )
    )

    def callApiClosure = { callApi(credentials.project, loadBalancerName, service) }
    if (retryApiCall) {
      safeRetry.doRetry(
        callApiClosure,
        "service",
        task,
        [409],
        [action: "Upsert", phase: BASE_PHASE],
        registry
      )
    } else {
      callApiClosure()
    }

    task.updateStatus BASE_PHASE, "Done upserting $loadBalancerName in $description.credentials.region."
    return [loadBalancers: [(credentials.region): [name: loadBalancerName]]]
  }

  Operation callApi(String projectName, String loadBalancerName, Service updatedService) {
    return description.credentials.appengine.apps().services().patch(projectName, loadBalancerName, updatedService)
      .setUpdateMask("split")
      .setMigrateTraffic(description.migrateTraffic)
      .execute()
  }

  static AppengineTrafficSplit copyAndOverrideAncestorSplit(AppengineTrafficSplit ancestor, AppengineTrafficSplit update) {
    AppengineTrafficSplit override = ancestor.clone()

    if (!update) {
      return ancestor
    }

    if (update.allocations) {
      override.allocations = update.allocations.findAll { it.value > 0 }
    }

    if (update.shardBy) {
      override.shardBy = update.shardBy
    }

    return override
  }
}
