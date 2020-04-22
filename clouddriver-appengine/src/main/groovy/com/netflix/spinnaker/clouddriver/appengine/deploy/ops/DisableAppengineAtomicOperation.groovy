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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineSafeRetry
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineUtils
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineModelUtil
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineTrafficSplit
import com.netflix.spinnaker.clouddriver.appengine.model.ShardBy
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import org.springframework.beans.factory.annotation.Autowired

import java.math.RoundingMode

class DisableAppengineAtomicOperation extends AppengineAtomicOperation<Void> {
  private static final String BASE_PHASE = "DISABLE_SERVER_GROUP"

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

  DisableAppengineAtomicOperation(EnableDisableAppengineDescription description) {
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
    def serverGroup = appengineClusterProvider.getServerGroup(credentials.name, credentials.region, serverGroupName)
    def loadBalancerName = serverGroup?.loadBalancers?.first()

    safeRetry.doRetry(
      {
        buildNewLoadBalancerAndCallApi(credentials.project, loadBalancerName, serverGroupName, priorOutputs)
      },
      "version",
      task,
      [409],
      [action: "Disable", phase: BASE_PHASE],
      registry
    )

    return null
  }

  Map buildNewLoadBalancerAndCallApi(String projectName, String loadBalancerName, String serverGroupName, List priorOutputs) {
    // We need to make a live call to make sure we have an up-to-date service, since the new traffic split we build is
    // dependent on the existing service's traffic split.
    def service = AppengineUtils.queryService(projectName, loadBalancerName, description.credentials, task, BASE_PHASE)
    def oldSplit = new ObjectMapper().convertValue(service.getSplit(), AppengineTrafficSplit)

    if (!oldSplit.allocations.containsKey(serverGroupName)) {
      task.updateStatus BASE_PHASE, "Server group $serverGroupName does not receive traffic from load balancer $loadBalancerName," +
        " ending operation..."
      return null
    }

    def newSplit = buildTrafficSplitWithoutServerGroup(oldSplit, serverGroupName)

    def upsertLoadBalancerDescription = new UpsertAppengineLoadBalancerDescription(
      credentials: description.credentials,
      loadBalancerName: loadBalancerName,
      split: newSplit,
      migrateTraffic: description.migrateTraffic
    )

    def upsertLoadBalancerOperation = new UpsertAppengineLoadBalancerAtomicOperation(upsertLoadBalancerDescription, false)
    upsertLoadBalancerOperation.appengineLoadBalancerProvider = appengineLoadBalancerProvider
    return upsertLoadBalancerOperation.operate(priorOutputs)
  }

  static AppengineTrafficSplit buildTrafficSplitWithoutServerGroup(AppengineTrafficSplit oldSplit, String serverGroupName) {
    AppengineTrafficSplit newSplit = oldSplit.clone()

    def decimalPlaces = newSplit.shardBy == ShardBy.COOKIE ?
      AppengineModelUtil.COOKIE_SPLIT_DECIMAL_PLACES :
      AppengineModelUtil.IP_SPLIT_DECIMAL_PLACES

    Map<String, BigDecimal> newAllocations = newSplit
      .allocations
      .collectEntries { k, v -> [(k): new BigDecimal(v).setScale(decimalPlaces, RoundingMode.HALF_UP)] } as Map<String, BigDecimal>

    // The validator ensured that the server group we're disabling doesn't have an allocation of 1, which would be bad.
    BigDecimal denominator = (new BigDecimal("1")).subtract(newAllocations.get(serverGroupName))
    newAllocations.remove(serverGroupName)

    newAllocations = newAllocations.collectEntries { name, allocation ->
      BigDecimal newAllocation = allocation.divide(denominator, decimalPlaces, RoundingMode.DOWN)
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
