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
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit2.Response

import java.time.Duration
import java.util.concurrent.TimeUnit

@Slf4j
@Component
public class UpsertLoadBalancerForceRefreshTask implements CloudProviderAware, RetryableTask {
  static final String REFRESH_TYPE = "LoadBalancer"

  static final int MAX_CHECK_FOR_PENDING = 3

  /**
   * Regional external passthrough NLBs are the only upsert whose success we cannot infer. Their
   * caching agent reports no pending on-demand requests, so the pending protocol never confirms the
   * write, and they are named by their forwarding rule -- the same name the cache is keyed by.
   * An HTTP upsert names its URL map instead, which the load balancer cache never holds, so reading
   * the provider for one would wait out the timeout on a refresh that had already succeeded.
   */
  static final String VISIBILITY_CHECKED_LOAD_BALANCER_TYPE = "REGIONAL_EXTERNAL_NETWORK"

  private final CloudDriverCacheService cacheService
  private final CloudDriverCacheStatusService cacheStatusService
  private final ObjectMapper mapper
  private final RetrySupport retrySupport
  private final OortService oortService

  @Autowired
  UpsertLoadBalancerForceRefreshTask(CloudDriverCacheService cacheService,
                                     CloudDriverCacheStatusService cacheStatusService,
                                     ObjectMapper mapper,
                                     RetrySupport retrySupport,
                                     OortService oortService) {
    this.cacheService = cacheService
    this.cacheStatusService = cacheStatusService
    this.mapper = mapper
    this.retrySupport = retrySupport
    this.oortService = oortService
  }

  @Override
  TaskResult execute(StageExecution stage) {
    LBUpsertContext context = stage.mapTo(LBUpsertContext.class)
    String cloudProvider = getCloudProvider(stage)

    if (!context.refreshState.hasRequested) {
      return requestCacheUpdates(stage, context, cloudProvider)
    }

    if (context.refreshState.allAreComplete) {
      return succeedWhenReady(stage, context, cloudProvider)
    }

    if (!context.refreshState.seenPendingCacheUpdates && context.refreshState.attempt >= MAX_CHECK_FOR_PENDING) {
      log.info("Failed to see pending cache updates in {} attempts, short circuiting", MAX_CHECK_FOR_PENDING)
      return succeedWhenReady(stage, context, cloudProvider)
    }

    checkPending(stage, context, cloudProvider)
    if (context.refreshState.allAreComplete) {
      return succeedWhenReady(stage, context, cloudProvider)
    }
    return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
  }

  @Override
  long getTimeout() {
    return TimeUnit.MINUTES.toMillis(10)
  }

  @Override
  long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5)
  }

  @Override
  long getDynamicBackoffPeriod(StageExecution stage, Duration taskDuration) {
    LBUpsertContext context = stage.mapTo(LBUpsertContext.class)
    if (context.refreshState.seenPendingCacheUpdates ||
        context.refreshState.allAreComplete ||
        context.refreshState.attempt >= MAX_CHECK_FOR_PENDING) {
      // Either we are polling pending updates, or the pending check is over and every remaining
      // attempt only waits on provider visibility. Neither justifies a one-second poll.
      return getBackoffPeriod()
    }
    // Some LB types don't support onDemand updates and we'll never observe a pending update for their keys,
    // this ensures quicker short circuiting in that case.
    return TimeUnit.SECONDS.toMillis(1)
  }

  private TaskResult requestCacheUpdates(StageExecution stage, LBUpsertContext context, String cloudProvider) {
    List<Boolean> requestStatuses = new ArrayList<>()

    for (Map target : stage.context.targets as List<Map>) {
      for (String region : (target.availabilityZones as Map).keySet()) {
        Map model = [
          loadBalancerName: target.name,
          region            : region,
          account           : target.credentials,
          loadBalancerType  : stage.context.loadBalancerType
        ] as Map

        Response response
        try {
          response = retrySupport.retry({
            Retrofit2SyncCall.executeCall(cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model))
          }, 3, 1000, false)
        } catch (SpinnakerNetworkException | IOException e) {
          return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
        }

        int statusCode = response.code()
        if (statusCode == HttpURLConnection.HTTP_OK) {
          requestStatuses.add(true)
        } else if (statusCode == HttpURLConnection.HTTP_ACCEPTED) {
          List<String> refreshIds = extractRefreshIds(response)
          if (refreshIds.isEmpty()) {
            // Cats atomic-agent lock contention; re-POST on the next task attempt.
            return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
          }
          context.refreshState.refreshIds.addAll(refreshIds)
          requestStatuses.add(false)
        } else if (statusCode == 429 || statusCode >= 500) {
          return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
        } else {
          throw new IllegalStateException(
            "Force cache update for load balancer '${target.name}' in ${region} (${target.credentials}) failed with status ${statusCode}"
          )
        }
      }
    }

    context.refreshState.hasRequested = true
    if (requestStatuses.every { it }) {
      context.refreshState.allAreComplete = true
      return succeedWhenReady(stage, context, cloudProvider)
    }
    return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
  }

  private List<String> extractRefreshIds(Response response) {
    if (!response.body()) {
      return []
    }

    Map<String, Object> responseBody = mapper.readValue(response.body().byteStream(), new TypeReference<Map<String, Object>>() {})
    return (responseBody?.cachedIdentifiersByType?.loadBalancers ?: []) as List<String>
  }

  private void checkPending(StageExecution stage, LBUpsertContext context, String cloudProvider) {
    Collection<Map> pendingCacheUpdates = retrySupport.retry({
      Retrofit2SyncCall.execute(cacheStatusService.pendingForceCacheUpdates(cloudProvider, REFRESH_TYPE))
    }, 3, 1000, false)

    if (!context.refreshState.refreshIds.isEmpty() &&
        !pendingCacheUpdates.isEmpty() &&
        !context.refreshState.seenPendingCacheUpdates) {
      if (context.refreshState.refreshIds.every { refreshId ->
        pendingCacheUpdates.any { pendingUpdateMatchesRefreshId(it, refreshId) }
      }) {
        context.refreshState.seenPendingCacheUpdates = true
      }
    }

    if (context.refreshState.seenPendingCacheUpdates) {
      if (pendingCacheUpdates.isEmpty()) {
        context.refreshState.allAreComplete = true
      } else {
        if (!pendingCacheUpdates.any {
          context.refreshState.refreshIds.any { refreshId ->
            pendingUpdateMatchesRefreshId(it, refreshId)
          }
        }) {
          context.refreshState.allAreComplete = true
        }
      }
    } else {
      context.refreshState.attempt++
    }
  }

  private TaskResult succeedWhenReady(StageExecution stage, LBUpsertContext context, String cloudProvider) {
    if (isGce(cloudProvider) && needsVisibilityCheck(stage) && !allGceLoadBalancersVisible(stage)) {
      return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
    }
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(getOutput(context)).build()
  }

  private static boolean isGce(String cloudProvider) {
    return "gce".equalsIgnoreCase(cloudProvider)
  }

  private static boolean needsVisibilityCheck(StageExecution stage) {
    return VISIBILITY_CHECKED_LOAD_BALANCER_TYPE.equalsIgnoreCase(stage.context.loadBalancerType as String)
  }

  private boolean allGceLoadBalancersVisible(StageExecution stage) {
    for (Map target : stage.context.targets as List<Map>) {
      for (String region : (target.availabilityZones as Map).keySet()) {
        if (!isLoadBalancerVisible(target.credentials as String, region, target.name as String)) {
          return false
        }
      }
    }
    return true
  }

  private boolean isLoadBalancerVisible(String account, String region, String name) {
    try {
      List<Map> details = Retrofit2SyncCall.execute(oortService.getLoadBalancerDetails("gce", account, region, name))
      return details != null && !details.isEmpty()
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 429 || e.responseCode >= 500) {
        return false
      }
      throw new IllegalStateException(
        "Failed to verify load balancer '${name}' in ${region} (${account}) with status ${e.responseCode}",
        e
      )
    } catch (SpinnakerNetworkException | SpinnakerServerException e) {
      return false
    }
  }

  private static boolean pendingUpdateMatchesRefreshId(Map pendingUpdate, String refreshId) {
    if (pendingUpdate.id as String == refreshId) {
      return true
    }

    Map details = pendingUpdate.details as Map
    String name = (details?.name ?: details?.loadBalancer) as String
    if (!details?.provider || !details?.type || !details?.account || !details?.region || !name) {
      return false
    }

    "${details.provider}:${details.type}:${details.account}:${details.region}:${name}" == refreshId
  }

  private Map<String, Object> getOutput(LBUpsertContext context) {
    return mapper.convertValue(context, new TypeReference<Map<String, Object>>() {})
  }

  private static class CacheRefreshState {
    Boolean hasRequested = false
    Boolean seenPendingCacheUpdates = false
    Integer attempt = 0
    Boolean allAreComplete = false
    List<String> refreshIds = new ArrayList<>()
  }

  private static class LBUpsertContext {
    CacheRefreshState refreshState = new CacheRefreshState()
  }
}
