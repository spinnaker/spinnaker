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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response
import retrofit.mime.TypedByteArray

import java.time.Duration
import java.util.concurrent.TimeUnit

@Slf4j
@Component
public class UpsertLoadBalancerForceRefreshTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  static final String REFRESH_TYPE = "LoadBalancer"

  static final int MAX_CHECK_FOR_PENDING = 3

  private final CloudDriverCacheService cacheService
  private final CloudDriverCacheStatusService cacheStatusService
  private final ObjectMapper mapper
  private final RetrySupport retrySupport

  @Autowired
  UpsertLoadBalancerForceRefreshTask(CloudDriverCacheService cacheService,
                                     CloudDriverCacheStatusService cacheStatusService,
                                     ObjectMapper mapper,
                                     RetrySupport retrySupport) {
    this.cacheService = cacheService
    this.cacheStatusService = cacheStatusService
    this.mapper = mapper
    this.retrySupport = retrySupport
  }

  @Override
  TaskResult execute(Stage stage) {
    LBUpsertContext context = stage.mapTo(LBUpsertContext.class)

    if (!context.refreshState.hasRequested) {
      return requestCacheUpdates(stage, context)
    }

    if (!context.refreshState.seenPendingCacheUpdates && context.refreshState.attempt >= MAX_CHECK_FOR_PENDING) {
      log.info("Failed to see pending cache updates in {} attempts, short circuiting", MAX_CHECK_FOR_PENDING)
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(getOutput(context)).build()
    }

    checkPending(stage, context)
    if (context.refreshState.allAreComplete) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(getOutput(context)).build()
    }
    TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
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
  long getDynamicBackoffPeriod(Stage stage, Duration taskDuration) {
    LBUpsertContext context = stage.mapTo(LBUpsertContext.class)
    if (context.refreshState.seenPendingCacheUpdates) {
      return getBackoffPeriod()
    } else {
      // Some LB types don't support onDemand updates and we'll never observe a pending update for their keys,
      // this ensures quicker short circuiting in that case.
      return TimeUnit.SECONDS.toMillis(1)
    }
  }

  private TaskResult requestCacheUpdates(Stage stage, LBUpsertContext context) {
    String cloudProvider = getCloudProvider(stage)

    List<Boolean> requestStatuses = new ArrayList<>()

    stage.context.targets.each { Map target ->
      target.availabilityZones.keySet().each { String region ->
        Response response = retrySupport.retry({
          cacheService.forceCacheUpdate(
            cloudProvider,
            REFRESH_TYPE,
            [loadBalancerName: target.name,
             region          : region,
             account         : target.credentials,
             loadBalancerType: stage.context.loadBalancerType]
          )
        }, 3, 1000, false)

        if (response != null && response.status != HttpURLConnection.HTTP_OK) {
          requestStatuses.add(false)

          Map<String, Object> responseBody = mapper.readValue(
            ((TypedByteArray) response.getBody()).getBytes(),
            new TypeReference<Map<String, Object>>() {}
          )

          if (responseBody?.cachedIdentifiersByType?.loadBalancers) {
            context.refreshState.refreshIds.addAll(
              responseBody["cachedIdentifiersByType"]["loadBalancers"] as List<String>
            )
          }
        } else {
          requestStatuses.add(true)
        }
      }
    }

    context.refreshState.hasRequested = true
    if (requestStatuses.every { it } || context.refreshState.refreshIds.isEmpty()) {
      context.refreshState.allAreComplete = true
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(getOutput(context)).build()
    } else {
      return TaskResult.builder(ExecutionStatus.RUNNING).context(getOutput(context)).build()
    }
  }

  private void checkPending(Stage stage, LBUpsertContext context) {
    String cloudProvider = getCloudProvider(stage)

    Collection<Map> pendingCacheUpdates = retrySupport.retry({
      cacheStatusService.pendingForceCacheUpdates(cloudProvider, REFRESH_TYPE)
    }, 3, 1000, false)

    if (!pendingCacheUpdates.isEmpty() && !context.refreshState.seenPendingCacheUpdates) {
      if (context.refreshState.refreshIds.every { refreshId ->
        pendingCacheUpdates.any { it.id as String == refreshId as String}
      }) {
        context.refreshState.seenPendingCacheUpdates = true
      }
    }

    if (context.refreshState.seenPendingCacheUpdates) {
      if (pendingCacheUpdates.isEmpty()) {
        context.refreshState.allAreComplete = true
      } else {
        if (!pendingCacheUpdates.any {
          context.refreshState.refreshIds.contains(it.id as String)
        }) {
          context.refreshState.allAreComplete = true
        }
      }
    } else {
      context.refreshState.attempt++
    }
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
