/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules {@link MaintenanceAgent}s independently of caching. Each agent gets its own fixed-delay
 * tick (so a pod never runs an agent concurrently with itself). On each tick the agent runs only if
 * the cluster-wide {@link MaintenanceLock} is acquired; afterwards the lock is held until the next
 * run is due, so the cadence applies to the whole cluster rather than to each pod.
 */
public class MaintenanceAgentScheduler {
  public static final String DISABLED_AGENTS_KEY = "maintenance-agents.disabled-agents";

  private static final Logger log = LoggerFactory.getLogger(MaintenanceAgentScheduler.class);

  private final MaintenanceLock lock;
  private final AgentIntervalProvider intervalProvider;
  private final DynamicConfigService dynamicConfigService;
  private final ScheduledExecutorService executor;
  private final long lockPollIntervalMillis;
  private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

  public MaintenanceAgentScheduler(
      MaintenanceLock lock,
      AgentIntervalProvider intervalProvider,
      DynamicConfigService dynamicConfigService,
      ScheduledExecutorService executor,
      long lockPollIntervalMillis) {
    this.lock = lock;
    this.intervalProvider = intervalProvider;
    this.dynamicConfigService = dynamicConfigService;
    this.executor = executor;
    this.lockPollIntervalMillis = lockPollIntervalMillis;
  }

  public void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
    long period = Math.max(1, Math.min(interval.getInterval(), lockPollIntervalMillis));
    ScheduledFuture<?> future =
        executor.scheduleWithFixedDelay(
            () -> tick(agent, agentExecution, executionInstrumentation),
            0,
            period,
            TimeUnit.MILLISECONDS);
    ScheduledFuture<?> previous = futures.put(agent.getAgentType(), future);
    if (previous != null) {
      previous.cancel(false);
    }
  }

  public void unschedule(Agent agent) {
    ScheduledFuture<?> future = futures.remove(agent.getAgentType());
    if (future != null) {
      future.cancel(false);
    }
  }

  /** One scheduling tick for an agent. Never throws, so the scheduled task is never suppressed. */
  void tick(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    String agentType = agent.getAgentType();
    try {
      if (isDisabled(agent)) {
        return;
      }

      AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
      if (!lock.tryAcquire(agentType, interval.getTimeout())) {
        return;
      }

      boolean success = false;
      long start = System.currentTimeMillis();
      try {
        executionInstrumentation.executionStarted(agent);
        agentExecution.executeAgent(agent);
        executionInstrumentation.executionCompleted(agent, System.currentTimeMillis() - start);
        success = true;
      } catch (Throwable t) {
        log.error("Maintenance agent {} failed", agentType, t);
        try {
          executionInstrumentation.executionFailed(agent, t, System.currentTimeMillis() - start);
        } catch (Throwable instrumentationFailure) {
          log.warn(
              "Instrumentation failed for maintenance agent {}", agentType, instrumentationFailure);
        }
      }

      long next =
          System.currentTimeMillis()
              + (success ? interval.getInterval() : interval.getErrorInterval());
      lock.hold(agentType, next);
    } catch (Throwable t) {
      log.error("Unexpected failure scheduling maintenance agent {}", agentType, t);
    }
  }

  private boolean isDisabled(Agent agent) {
    String disabled = dynamicConfigService.getConfig(String.class, DISABLED_AGENTS_KEY, "");
    if (disabled == null || disabled.isBlank()) {
      return false;
    }
    return Arrays.stream(disabled.split(","))
        .map(String::trim)
        .anyMatch(
            name -> name.equals(agent.getAgentType()) || name.equals(agent.getClass().getName()));
  }
}
