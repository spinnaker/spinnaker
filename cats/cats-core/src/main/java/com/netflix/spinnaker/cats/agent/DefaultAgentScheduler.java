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

import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

  private final ScheduledExecutorService scheduledExecutorService;
  private final long interval;
  private final TimeUnit timeUnit;
  private final Map<Agent, Future> agentFutures = new ConcurrentHashMap<Agent, Future>();

  public DefaultAgentScheduler() {
    this(DEFAULT_INTERVAL);
  }

  public DefaultAgentScheduler(long interval) {
    this(interval, TimeUnit.MILLISECONDS);
  }

  public DefaultAgentScheduler(long interval, TimeUnit unit) {
    this(
        Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new NamedThreadFactory(DefaultAgentScheduler.class.getSimpleName())),
        interval,
        unit);
  }

  public DefaultAgentScheduler(
      ScheduledExecutorService scheduledExecutorService, long interval, TimeUnit timeUnit) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.interval = interval;
    this.timeUnit = timeUnit;
  }

  @Override
  public void schedule(
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

  private static class AgentExecutionRunnable implements Runnable {
    private final Agent agent;
    private final AgentExecution execution;
    private final ExecutionInstrumentation executionInstrumentation;

    public AgentExecutionRunnable(
        Agent agent, AgentExecution execution, ExecutionInstrumentation executionInstrumentation) {
      this.agent = agent;
      this.execution = execution;
      this.executionInstrumentation = executionInstrumentation;
    }

    public void run() {
      try {
        executionInstrumentation.executionStarted(agent);
        long startTime = System.nanoTime();
        execution.executeAgent(agent);
        executionInstrumentation.executionCompleted(
            agent, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
      } catch (Throwable t) {
        executionInstrumentation.executionFailed(agent, t);
      }
    }
  }
}
