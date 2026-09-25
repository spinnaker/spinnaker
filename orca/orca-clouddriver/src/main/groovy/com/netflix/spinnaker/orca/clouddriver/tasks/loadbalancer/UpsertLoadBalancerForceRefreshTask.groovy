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

  static final Set<String> VISIBILITY_CHECKED_LOAD_BALANCER_TYPES = [
    "REGIONAL_EXTERNAL_NETWORK",
    "EXTERNAL_MANAGED",
  ] as Set<String>

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

    if (usesTargetRefreshState(stage, cloudProvider)) {
      return executeWithTargetRefreshState(stage, context, cloudProvider)
    }

    return executeLegacy(stage, context, cloudProvider)
  }

  private TaskResult executeLegacy(StageExecution stage, LBUpsertContext context, String cloudProvider) {
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

  private static boolean usesTargetRefreshState(StageExecution stage, String cloudProvider) {
    if (!isGce(cloudProvider)) {
      return false
    }
    if (isVisibilityCheckedType(stage.context.loadBalancerType as String)) {
      return true
    }
    return ((stage.context.targets as List<Map>) ?: []).any { Map target ->
      isVisibilityCheckedType((target.loadBalancerType ?: stage.context.loadBalancerType) as String)
    }
  }

  private TaskResult executeWithTargetRefreshState(StageExecution stage,
                                                   LBUpsertContext context,
                                                   String cloudProvider) {
    List<TargetRefreshState> targetStates = initializeTargetStates(stage, context)

    if (targetStates.any { !it.hasRequested }) {
      return requestTargetCacheUpdates(context, targetStates, cloudProvider)
    }

    if (targetStates.every { it.allAreComplete }) {
      return succeedTargetRefreshWhenVisible(context, targetStates)
    }

    checkTargetPending(context, targetStates, cloudProvider)
    if (targetStates.every { it.allAreComplete }) {
      return succeedTargetRefreshWhenVisible(context, targetStates)
    }
    return targetResult(ExecutionStatus.RUNNING, context)
  }

  private List<TargetRefreshState> initializeTargetStates(StageExecution stage, LBUpsertContext context) {
    List<Map> targets = stage.context.targets as List<Map>
    if (!targets) {
      throw new IllegalArgumentException("Force cache refresh requires at least one load balancer target")
    }

    List<TargetRefreshState> existingStates = context.refreshState.targetStates ?: []
    boolean migrateLegacyState = existingStates.isEmpty() && context.refreshState.hasRequested
    List<TargetRefreshState> initializedStates = []

    targets.eachWithIndex { Map target, int targetIndex ->
      String account = (target.account ?: target.credentials ?: stage.context.account ?: getCredentials(stage)) as String
      String loadBalancerName = (target.loadBalancerName ?: target.name) as String
      String loadBalancerType = (target.loadBalancerType ?: stage.context.loadBalancerType) as String
      LinkedHashSet<String> regions = new LinkedHashSet<>()
      if (target.region) {
        regions.add(target.region as String)
      }
      if (target.availabilityZones instanceof Map) {
        regions.addAll((target.availabilityZones as Map).keySet().findAll { it }*.toString())
      }

      if (!account || !loadBalancerName || !regions) {
        throw new IllegalArgumentException(
          "Load balancer target ${targetIndex} requires an account, region, and concrete listener name"
        )
      }

      regions.each { String region ->
        String key = "${targetIndex}|${account}|${region}|${loadBalancerType ?: ''}|${loadBalancerName}"
        TargetRefreshState state = existingStates.find { it.key == key }
        if (!state) {
          state = new TargetRefreshState()
          if (migrateLegacyState) {
            state.hasRequested = context.refreshState.hasRequested
            state.seenPendingCacheUpdates = context.refreshState.seenPendingCacheUpdates
            state.attempt = context.refreshState.attempt
            state.allAreComplete = context.refreshState.allAreComplete ||
              (!context.refreshState.seenPendingCacheUpdates &&
                context.refreshState.attempt >= MAX_CHECK_FOR_PENDING)
            state.refreshIds = new ArrayList<>(context.refreshState.refreshIds ?: [])
            if (state.seenPendingCacheUpdates) {
              state.observedRefreshIds = new ArrayList<>(state.refreshIds)
            }
          }
        }
        state.key = key
        state.account = account
        state.region = region
        state.loadBalancerType = loadBalancerType
        state.loadBalancerName = loadBalancerName
        state.attempt = state.attempt ?: 0
        state.refreshIds = new ArrayList<>(state.refreshIds ?: [])
        state.observedRefreshIds = new ArrayList<>(state.observedRefreshIds ?: [])
        initializedStates.add(state)
      }
    }

    context.refreshState.targetStates = initializedStates
    updateLegacySummary(context)
    return initializedStates
  }

  private TaskResult requestTargetCacheUpdates(LBUpsertContext context,
                                               List<TargetRefreshState> targetStates,
                                               String cloudProvider) {
    for (TargetRefreshState targetState : targetStates.findAll { !it.hasRequested }) {
      Map model = [
        loadBalancerName: targetState.loadBalancerName,
        region            : targetState.region,
        account           : targetState.account,
        loadBalancerType  : targetState.loadBalancerType,
      ] as Map

      Response response
      try {
        response = retrySupport.retry({
          Retrofit2SyncCall.executeCall(cacheService.forceCacheUpdate(cloudProvider, REFRESH_TYPE, model))
        }, 3, 1000, false)
      } catch (SpinnakerNetworkException | IOException e) {
        return targetResult(ExecutionStatus.RUNNING, context)
      }

      int statusCode = response.code()
      if (statusCode == HttpURLConnection.HTTP_OK) {
        targetState.hasRequested = true
        targetState.allAreComplete = true
      } else if (statusCode == HttpURLConnection.HTTP_ACCEPTED) {
        List<String> refreshIds = extractRefreshIds(response)
        if (refreshIds.isEmpty()) {
          return targetResult(ExecutionStatus.RUNNING, context)
        }
        targetState.hasRequested = true
        targetState.refreshIds = refreshIds
      } else if (statusCode == 429 || statusCode >= 500) {
        return targetResult(ExecutionStatus.RUNNING, context)
      } else {
        throw new IllegalStateException(
          "Force cache update for load balancer '${targetState.loadBalancerName}' in " +
            "${targetState.region} (${targetState.account}) failed with status ${statusCode}"
        )
      }
    }

    if (targetStates.every { it.allAreComplete }) {
      return succeedTargetRefreshWhenVisible(context, targetStates)
    }
    return targetResult(ExecutionStatus.RUNNING, context)
  }

  private void checkTargetPending(LBUpsertContext context,
                                  List<TargetRefreshState> targetStates,
                                  String cloudProvider) {
    Collection<Map> pendingCacheUpdates = retrySupport.retry({
      Retrofit2SyncCall.execute(cacheStatusService.pendingForceCacheUpdates(cloudProvider, REFRESH_TYPE))
    }, 3, 1000, false) ?: []

    targetStates.findAll { it.hasRequested && !it.allAreComplete }.each { TargetRefreshState targetState ->
      List<String> visibleRefreshIds = targetState.refreshIds.findAll { String refreshId ->
        pendingCacheUpdates.any { pendingUpdateMatchesRefreshId(it, refreshId) }
      }
      targetState.observedRefreshIds.addAll(visibleRefreshIds)
      targetState.observedRefreshIds = targetState.observedRefreshIds.unique()
      targetState.seenPendingCacheUpdates =
        targetState.refreshIds.every { targetState.observedRefreshIds.contains(it) }

      if (targetState.seenPendingCacheUpdates && visibleRefreshIds.isEmpty()) {
        targetState.allAreComplete = true
      } else if (!targetState.seenPendingCacheUpdates) {
        targetState.attempt++
        if (targetState.attempt >= MAX_CHECK_FOR_PENDING) {
          // The accepted identifiers never became observable. New regional families still have to
          // pass exact Oort verification before this task can report success.
          targetState.allAreComplete = true
        }
      }
    }
    updateLegacySummary(context)
  }

  private TaskResult succeedTargetRefreshWhenVisible(LBUpsertContext context,
                                                     List<TargetRefreshState> targetStates) {
    if (!targetStates.findAll { isVisibilityCheckedType(it.loadBalancerType) }.every {
      isLoadBalancerVisible(it)
    }) {
      return targetResult(ExecutionStatus.RUNNING, context)
    }
    return targetResult(ExecutionStatus.SUCCEEDED, context)
  }

  private boolean isLoadBalancerVisible(TargetRefreshState targetState) {
    try {
      List<Map> details = Retrofit2SyncCall.execute(
        oortService.getLoadBalancerDetails(
          "gce",
          targetState.account,
          targetState.region,
          targetState.loadBalancerName
        )
      )
      return details?.any { Map detail ->
        String detailName = (detail.loadBalancerName ?: detail.name) as String
        detailName == targetState.loadBalancerName &&
          detail.loadBalancerType?.toString()?.equalsIgnoreCase(targetState.loadBalancerType) &&
          detail.account == targetState.account &&
          detail.region == targetState.region
      } ?: false
    } catch (SpinnakerHttpException e) {
      if (e.responseCode == 429 || e.responseCode >= 500) {
        return false
      }
      throw new IllegalStateException(
        "Failed to verify load balancer '${targetState.loadBalancerName}' in " +
          "${targetState.region} (${targetState.account}) with status ${e.responseCode}",
        e
      )
    } catch (SpinnakerNetworkException | SpinnakerServerException e) {
      return false
    }
  }

  private static boolean isVisibilityCheckedType(String loadBalancerType) {
    return VISIBILITY_CHECKED_LOAD_BALANCER_TYPES.any {
      it.equalsIgnoreCase(loadBalancerType)
    }
  }

  private TaskResult targetResult(ExecutionStatus status, LBUpsertContext context) {
    updateLegacySummary(context)
    return TaskResult.builder(status).context(getOutput(context)).build()
  }

  private static void updateLegacySummary(LBUpsertContext context) {
    List<TargetRefreshState> targetStates = context.refreshState.targetStates ?: []
    context.refreshState.hasRequested = !targetStates.isEmpty() && targetStates.every { it.hasRequested }
    context.refreshState.seenPendingCacheUpdates = targetStates.any { it.seenPendingCacheUpdates }
    context.refreshState.attempt = targetStates.collect { it.attempt ?: 0 }.max() ?: 0
    context.refreshState.allAreComplete = !targetStates.isEmpty() && targetStates.every { it.allAreComplete }
    context.refreshState.refreshIds = targetStates.collectMany { it.refreshIds ?: [] }.unique()
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
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(getOutput(context)).build()
  }

  private static boolean isGce(String cloudProvider) {
    return "gce".equalsIgnoreCase(cloudProvider)
  }

  /**
   * Cats normally returns the cache identifier as {@code id}. Some non-atomic pending rows expose
   * only their request details, so the fallback reconstructs the documented
   * provider:type:account:region:name identity emitted by Clouddriver. Keep this fallback in sync
   * with the force-cache response contract rather than importing provider cache-key code into Orca.
   */
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
    List<TargetRefreshState> targetStates = new ArrayList<>()
  }

  private static class TargetRefreshState {
    String key
    String account
    String region
    String loadBalancerType
    String loadBalancerName
    Boolean hasRequested = false
    Boolean seenPendingCacheUpdates = false
    Integer attempt = 0
    Boolean allAreComplete = false
    List<String> refreshIds = new ArrayList<>()
    List<String> observedRefreshIds = new ArrayList<>()
  }

  private static class LBUpsertContext {
    CacheRefreshState refreshState = new CacheRefreshState()
  }
}
