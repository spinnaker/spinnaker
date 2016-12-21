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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppEngineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppEngineIllegalArgumentExeception
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppEngineResourceNotFoundException
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppEngineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppEngineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import java.math.MathContext
import java.math.RoundingMode

class DisableAppEngineAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DISABLE_SERVER_GROUP";

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final EnableDisableAppEngineDescription description

  @Autowired
  AppEngineLoadBalancerProvider appEngineLoadBalancerProvider

  @Autowired
  AppEngineClusterProvider appEngineClusterProvider

  DisableAppEngineAtomicOperation(EnableDisableAppEngineDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "disableServerGroup": { "serverGroupName": "app-stack-detail-v000", "credentials": "my-appengine-account" }} ]' localhost:7002/appengine/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing disable server group operation for $description.serverGroupName in $description.credentials.region..."

    def credentials = description.credentials
    def serverGroupName = description.serverGroupName

    task.updateStatus BASE_PHASE, "Looking up server group $serverGroupName..."
    def serverGroup = appEngineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def loadBalancerName = serverGroup?.loadBalancers?.first()

    def loadBalancer = appEngineLoadBalancerProvider.getLoadBalancer(credentials.name, loadBalancerName)

    if (!loadBalancer.split.allocations.containsKey(serverGroupName)) {
      task.updateStatus BASE_PHASE, "Server group $serverGroupName does not receive traffic from load balancer $loadBalancer.name," +
        " ending operation..."
      return null
    }

    def split = buildTrafficSplitWithoutServerGroup(loadBalancer.split, serverGroupName)

    def upsertLoadBalancerDescription = new UpsertAppEngineLoadBalancerDescription(
      credentials: credentials,
      loadBalancerName: loadBalancerName,
      split: split,
      migrateTraffic: description.migrateTraffic
    )

    task.updateStatus BASE_PHASE, "Updating load balancer $loadBalancerName..."
    def upsertLoadBalancerOperation = new UpsertAppEngineLoadBalancerAtomicOperation(upsertLoadBalancerDescription)
    upsertLoadBalancerOperation.appEngineLoadBalancerProvider = appEngineLoadBalancerProvider
    upsertLoadBalancerOperation.operate(priorOutputs)

    return null
  }

  // https://cloud.google.com/appengine/docs/admin-api/reference/rest/v1/apps.services#TrafficSplit
  private static final COOKIE_SPLIT_PRECISION = 3
  private static final IP_SPLIT_PRECISION = 2

  static AppEngineTrafficSplit buildTrafficSplitWithoutServerGroup(AppEngineTrafficSplit oldSplit, String serverGroupName) {
    AppEngineTrafficSplit newSplit = oldSplit.clone()

    Map<String, BigDecimal> newAllocations = newSplit
      .allocations.collectEntries { k, v -> [(k): new BigDecimal(v)] } as Map<String, BigDecimal>

    // The validator ensured that the server group we're disabling doesn't have an allocation of 1, which would be bad.
    BigDecimal denominator = (new BigDecimal("1")).subtract(newAllocations.get(serverGroupName))
    newAllocations.remove(serverGroupName)

    MathContext context = new MathContext(
      newSplit.shardBy == ShardBy.COOKIE ? COOKIE_SPLIT_PRECISION : IP_SPLIT_PRECISION,
      RoundingMode.DOWN
    )

    newAllocations = newAllocations.collectEntries { name, allocation ->
      BigDecimal newAllocation = allocation.divide(denominator, context)
      return [(name): newAllocation]
    } as Map<String, BigDecimal>

    /*
    * We rounded down, so the error will be >= 0
    * but <= (the epsilon for the shard type (0.01 or 0.001) times the number of server groups in the allocation)
    * and a multiple of the epsilon.
    * */
    BigDecimal sum = newAllocations.inject(new BigDecimal("0"), { BigDecimal partialSum, String name, BigDecimal allocation ->
      partialSum.add(allocation)
    })
    BigDecimal error = (new BigDecimal("1")).subtract(sum)
    BigDecimal epsilon = newSplit.shardBy == ShardBy.COOKIE ? new BigDecimal("0.001") : new BigDecimal("0.01")
    BigDecimal numberToDistributeAmong = error.divide(epsilon)

    // Sort the server group names (so that the process is predictable), then distribute the excess evenly among them.
    def sortedServerGroupNames = newAllocations.keySet().sort()
    for (def i = 0; i < numberToDistributeAmong; i++) {
      def name = sortedServerGroupNames[i]
      newAllocations[name] = newAllocations[name].add(epsilon)
    }

    newSplit.allocations = newAllocations.collectEntries { k, v -> [(k): v.doubleValue()] } as Map<String, Double>

    return newSplit
  }
}
