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

package com.netflix.spinnaker.cats.agent;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import java.util.Map;
import java.util.concurrent.*;

/**
 * An AgentScheduler that executes on a fixed interval.
 *
 * <p>This AgentScheduler will capture any exceptions thrown by the AgentExecution and report them
 * to the provided ExecutionInstrumentation.
 *
 * <p>An exception thrown while reporting executionFailure will abort the schedule for the
 * CachingAgent.
 */
public class DefaultAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock> {
  private static final long DEFAULT_INTERVAL = 60000;
  private static final long DEFAULT_STOP_TIMEOUT = 60000;

  private final ScheduledExecutorService scheduledExecutorService;
  private final ExecutorService executorService;

  private final long interval;
  private final TimeUnit timeUnit;
  private final long stopTimeout;
  private final Map<Agent, Future> agentFutures = new ConcurrentHashMap<Agent, Future>();
  private final Map<Agent, AgentExecutionRunnable> longRunningExecutionRunnables =
      new ConcurrentHashMap<Agent, AgentExecutionRunnable>();

  public DefaultAgentScheduler() {
    this(DEFAULT_INTERVAL, DEFAULT_STOP_TIMEOUT);
  }

  public DefaultAgentScheduler(long interval, long stopTimeout) {
    this(interval, stopTimeout, TimeUnit.MILLISECONDS);
  }

  public DefaultAgentScheduler(long interval, long stopTimeout, TimeUnit unit) {
    this(
        Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new ThreadFactoryBuilder()
                .setNameFormat(DefaultAgentScheduler.class.getSimpleName() + ":ScheduledAgents-%d")
                .build()),
        interval,
        stopTimeout,
        unit,
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat(
                    DefaultAgentScheduler.class.getSimpleName() + ":LongRunningAgents-%d")
                .build()));
  }

  public DefaultAgentScheduler(
      ScheduledExecutorService scheduledExecutorService,
      long interval,
      long stopTimeout,
      TimeUnit timeUnit,
      ExecutorService executorService) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.interval = interval;
    this.timeUnit = timeUnit;
    this.executorService = executorService;
    scheduledExecutorService.schedule(new LongRunningAgentRescheduleRunnable(), interval, timeUnit);
    this.stopTimeout = stopTimeout;
  }

  @Override
  public void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    if (agentExecution instanceof LongRunningAgentExecution) {
      scheduleLongRunningAgent(
          agent, (LongRunningAgentExecution) agentExecution, executionInstrumentation);
    } else {
      scheduleSimpleAgent(agent, agentExecution, executionInstrumentation);
    }
  }

  private void scheduleLongRunningAgent(
      Agent agent,
      LongRunningAgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    AgentExecutionRunnable runnable =
        new AgentExecutionRunnable(agent, agentExecution, executionInstrumentation);
    longRunningExecutionRunnables.put(agent, runnable);
    executorService.submit(runnable);
  }

  private void scheduleSimpleAgent(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    Long agentInterval = interval;
    TimeUnit agentTimeUnit = timeUnit;
    if (agent instanceof AgentIntervalAware) {
      agentInterval = ((AgentIntervalAware) agent).getAgentInterval();
      agentTimeUnit = TimeUnit.MILLISECONDS;
    }

    Future agentFuture =
        scheduledExecutorService.scheduleAtFixedRate(
            new AgentExecutionRunnable(agent, agentExecution, executionInstrumentation),
            0,
            agentInterval,
            agentTimeUnit);

    agentFutures.put(agent, agentFuture);
  }

  @Override
  public void unschedule(Agent agent) {
    if (agentFutures.containsKey(agent)) {
      agentFutures.get(agent).cancel(false);
      agentFutures.remove(agent);
    } else if (longRunningExecutionRunnables.containsKey(agent)) {
      AgentExecutionRunnable runnable = longRunningExecutionRunnables.get(agent);

      ((LongRunningAgentExecution) runnable.getExecution()).stopExecutingAndCleanup();
      longRunningExecutionRunnables.remove(agent);
    }
  }

  @Override
  public AgentLock tryLock(Agent agent) {
    return null;
  }

  @Override
  public boolean tryRelease(AgentLock lock) {
    return false;
  }

  @Override
  public boolean isAtomic() {
    return false;
  }

  private class LongRunningAgentRescheduleRunnable implements Runnable {
    public void run() {
      for (AgentExecutionRunnable runnable :
          DefaultAgentScheduler.this.longRunningExecutionRunnables.values()) {
        LongRunningAgentExecution execution = (LongRunningAgentExecution) runnable.getExecution();
        if (!execution.isRunning()) {
          execution
              .stopExecutingAndCleanup()
              .orTimeout(stopTimeout, timeUnit)
              .whenComplete((res, ex) -> executorService.submit(runnable));
        }
      }
    }
  }
}
