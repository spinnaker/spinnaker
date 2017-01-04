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

import com.netflix.spinnaker.clouddriver.appengine.deploy.AppEngineSafeRetry
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class EnableAppEngineAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ENABLE_SERVER_GROUP";

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final EnableDisableAppEngineDescription description

  @Autowired
  AppEngineLoadBalancerProvider appEngineLoadBalancerProvider

  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  @Autowired
  AppEngineSafeRetry safeRetry

  EnableAppEngineAtomicOperation(EnableDisableAppEngineDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "enableServerGroup": { "serverGroupName": "app-stack-detail-v000", "credentials": "my-appengine-account" }} ]' localhost:7002/appengine/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing enable server group operation" +
      " for $description.serverGroupName in $description.credentials.region..."

    def credentials = description.credentials
    def serverGroupName = description.serverGroupName

    task.updateStatus BASE_PHASE, "Looking up server group $serverGroupName..."
    def serverGroup = appEngineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def loadBalancerName = serverGroup?.loadBalancers?.first()

    def upsertLoadBalancerDescription = new UpsertAppEngineLoadBalancerDescription(
      credentials: credentials,
      loadBalancerName: loadBalancerName,
      split: new AppEngineTrafficSplit(allocations: [(serverGroupName): 1]),
      migrateTraffic: description.migrateTraffic
    )

    task.updateStatus BASE_PHASE, "Updating load balancer $loadBalancerName..."
    def upsertLoadBalancerOperation = new UpsertAppEngineLoadBalancerAtomicOperation(upsertLoadBalancerDescription)
    upsertLoadBalancerOperation.appEngineLoadBalancerProvider = appEngineLoadBalancerProvider
    upsertLoadBalancerOperation.safeRetry = safeRetry
    upsertLoadBalancerOperation.operate(priorOutputs)

    return null
  }
}
