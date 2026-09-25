/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit2.Response

import javax.annotation.Nonnull
import java.util.concurrent.TimeUnit

@Component
class DeleteLoadBalancerForceRefreshTask implements CloudProviderAware, RetryableTask {
  static final String REFRESH_TYPE = "LoadBalancer"

  @Autowired
  CloudDriverCacheService cacheService

  @Autowired
  ObjectMapper mapper

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    String vpcId = stage.context.vpcId ?: ''
    List<String> regions = (stage.context.regions ?: []) as List<String>
    List<String> names = deletedLoadBalancerNames(stage)
    if (!regions) {
      throw new IllegalArgumentException("Delete load balancer cache refresh requires at least one region")
    }

    Map refreshState = new LinkedHashMap(stage.context.deleteRefreshState as Map ?: [:])
    List<String> completedTargets = new ArrayList<>(refreshState.completedTargets as Collection<String> ?: [])
    refreshState.completedTargets = completedTargets

    for (String name : names) {
      for (String region : regions) {
        String targetKey = "${account}|${region}|${name}"
        if (completedTargets.contains(targetKey)) {
          continue
        }

        def model = [loadBalancerName: name, region: region, account: account, vpcId: vpcId, evict: true] as Map
        Response response
        try {
          response = Retrofit2SyncCall.executeCall(cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model))
        } catch (SpinnakerNetworkException | IOException e) {
          return result(ExecutionStatus.RUNNING, refreshState)
        }

        int statusCode = response.code()
        if (statusCode == HttpURLConnection.HTTP_OK) {
          completedTargets.add(targetKey)
          continue
        }

        if (statusCode == HttpURLConnection.HTTP_ACCEPTED) {
          if (extractRefreshIds(response).isEmpty()) {
            // Cats accepted no identifiers because the refresh did not run; retry this target.
            return result(ExecutionStatus.RUNNING, refreshState)
          }
          // Cats returned identifiers for the stored eviction; do not submit this target again.
          completedTargets.add(targetKey)
          continue
        }

        if (statusCode == 429 || statusCode >= 500) {
          return result(ExecutionStatus.RUNNING, refreshState)
        }

        throw new IllegalStateException(
          "Force cache update for load balancer '${name}' in ${region} (${account}) failed with status ${statusCode}"
        )
      }
    }

    return result(ExecutionStatus.SUCCEEDED, refreshState)
  }

  private List<String> deletedLoadBalancerNames(StageExecution stage) {
    List<Object> candidates = [stage.context.loadBalancerName]
    TaskId lastTaskId = stage.context."kato.last.task.id" as TaskId
    List<Map> katoTasks = stage.context."kato.tasks" as List<Map> ?: []
    Map successfulTask = katoTasks.find { Map katoTask ->
      katoTask.id?.toString() == lastTaskId?.id &&
        katoTask.status?.completed == true &&
        katoTask.status?.failed == false
    }
    (successfulTask?.resultObjects as Collection ?: []).each { Map resultObject ->
      candidates.addAll(resultObject.deletedLoadBalancerNames as Collection ?: [])
    }
    if (candidates.any { !(it instanceof String) || !(it as String).trim() }) {
      throw new IllegalArgumentException("Deleted load balancer names must be concrete non-empty strings")
    }
    return candidates.collect { it as String }.unique()
  }

  private List<String> extractRefreshIds(Response response) {
    if (!response.body()) {
      return []
    }

    Map<String, Object> responseBody = mapper.readValue(response.body().byteStream(), new TypeReference<Map<String, Object>>() {})
    return (responseBody?.cachedIdentifiersByType?.loadBalancers ?: []) as List<String>
  }

  private static TaskResult result(ExecutionStatus status, Map refreshState) {
    return TaskResult.builder(status).context([deleteRefreshState: refreshState]).build()
  }

  @Override
  long getTimeout() {
    return TimeUnit.MINUTES.toMillis(10)
  }

  @Override
  long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5)
  }
}
