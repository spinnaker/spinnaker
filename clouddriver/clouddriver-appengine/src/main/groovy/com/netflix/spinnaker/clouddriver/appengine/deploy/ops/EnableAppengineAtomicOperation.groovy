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

import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineSafeRetry
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class EnableAppengineAtomicOperation extends AppengineAtomicOperation<Void> {
  private static final String BASE_PHASE = "ENABLE_SERVER_GROUP";

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final EnableDisableAppengineDescription description

  @Autowired
  AppengineLoadBalancerProvider appengineLoadBalancerProvider

  @Autowired
  AppengineClusterProvider appengineClusterProvider

  @Autowired
  AppengineSafeRetry safeRetry

  EnableAppengineAtomicOperation(EnableDisableAppengineDescription description) {
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
    def serverGroup = appengineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def loadBalancerName = serverGroup?.loadBalancers?.first()

    def upsertLoadBalancerDescription = new UpsertAppengineLoadBalancerDescription(
      credentials: credentials,
      loadBalancerName: loadBalancerName,
      split: new AppengineTrafficSplit(allocations: [(serverGroupName): 1]),
      migrateTraffic: description.migrateTraffic
    )

    task.updateStatus BASE_PHASE, "Updating load balancer $loadBalancerName..."
    def upsertLoadBalancerOperation = new UpsertAppengineLoadBalancerAtomicOperation(upsertLoadBalancerDescription)
    upsertLoadBalancerOperation.appengineLoadBalancerProvider = appengineLoadBalancerProvider
    upsertLoadBalancerOperation.safeRetry = safeRetry
    upsertLoadBalancerOperation.registry = registry
    upsertLoadBalancerOperation.operate(priorOutputs)

    return null
  }
}
