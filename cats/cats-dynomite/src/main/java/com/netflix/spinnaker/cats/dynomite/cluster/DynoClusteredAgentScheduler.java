/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cats.dynomite.cluster;

import com.netflix.dyno.connectionpool.exception.DynoException;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Temporary clustered agent scheduler while we're waiting for Dyno client support of evalsha and loadscript.
 *
 * Shares a similar strategy as ClusteredAgentScheduler, but doesn't use Lua, is slower and less safe. Dynomite
 * support for Lua is in-progress, so this class is rather temporary, then we can move to ClusteredSortAgentScheduler.
 */
public class DynoClusteredAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock>, Runnable {

  private final static Logger log = LoggerFactory.getLogger(DynoClusteredAgentScheduler.class);

  private final static RetryPolicy ACQUIRE_LOCK_RETRY_POLICY = new RetryPolicy()
    .retryOn(Arrays.asList(DynoException.class, JedisException.class))
    .withMaxRetries(3)
    .withDelay(25, TimeUnit.MILLISECONDS);

  private static enum Status {
    SUCCESS,
    FAILURE
  }

  private final DynomiteClientDelegate redisClientDelegate;
  private final NodeIdentity nodeIdentity;
  private final AgentIntervalProvider intervalProvider;
  private final ExecutorService agentExecutionPool;
  private final Map<String, AgentExecutionAction> agents = new ConcurrentHashMap<>();
  private final Map<String, NextAttempt> activeAgents = new ConcurrentHashMap<>();
  private final NodeStatusProvider nodeStatusProvider;

  public DynoClusteredAgentScheduler(DynomiteClientDelegate redisClientDelegate, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider, NodeStatusProvider nodeStatusProvider) {
    this(redisClientDelegate, nodeIdentity, intervalProvider, nodeStatusProvider, Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(DynoClusteredAgentScheduler.class.getSimpleName())), Executors.newCachedThreadPool(new NamedThreadFactory(AgentExecutionAction.class.getSimpleName())));
  }

  public DynoClusteredAgentScheduler(DynomiteClientDelegate redisClientDelegate, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider, NodeStatusProvider nodeStatusProvider, ScheduledExecutorService lockPollingScheduler, ExecutorService agentExecutionPool) {
    this.redisClientDelegate = redisClientDelegate;
    this.nodeIdentity = nodeIdentity;
    this.intervalProvider = intervalProvider;
    this.nodeStatusProvider = nodeStatusProvider;
    this.agentExecutionPool = agentExecutionPool;
    lockPollingScheduler.scheduleAtFixedRate(this, 0, 5, TimeUnit.SECONDS);
  }

  @Override
  public void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
    if (agent instanceof AgentSchedulerAware) {
      ((AgentSchedulerAware) agent).setAgentScheduler(this);
    }

    final AgentExecutionAction agentExecutionAction = new AgentExecutionAction(agent, agentExecution, executionInstrumentation);
    agents.put(agent.getAgentType(), agentExecutionAction);
  }

  @Override
  public void unschedule(Agent agent) {
    releaseRunKey(agent.getAgentType(), 0);
    agents.remove(agent.getAgentType());
  }

  @Override
  public void run() {
    if (!nodeStatusProvider.isNodeEnabled()) {
      return;
    }
    try {
      runAgents();
    } catch (Throwable t) {
      log.error("Failed running cache agents", t);
    }
  }

  private Map<String, NextAttempt> acquire() {
    Map<String, NextAttempt> acquired = new HashMap<>(agents.size());
    Set<String> skip = new HashSet<>(activeAgents.keySet());
    agents.entrySet().stream()
      .filter(a -> !skip.contains(a.getKey()))
      .forEach(a -> {
        final String agentType = a.getKey();
        AgentIntervalProvider.Interval interval = intervalProvider.getInterval(a.getValue().getAgent());
        if (acquireRunKey(agentType, interval.getTimeout())) {
          acquired.put(agentType, new NextAttempt(System.currentTimeMillis(), interval.getInterval(), interval.getErrorInterval()));
        }
      });
    return acquired;
  }

  private boolean acquireRunKey(String agentType, long timeout) {
    // This isn't as safe as the vanilla Redis impl because the call isn't atomic, but it's the best we can do until
    // dynomite adds support for `String set(String key, String value, String nxxx, String expx, long time)` (which
    // they are working on).
    String identity = nodeIdentity.getNodeIdentity();
    return redisClientDelegate.withCommandsClient(client -> {
      return Failsafe
        .with(ACQUIRE_LOCK_RETRY_POLICY)
        .get(() -> {
          String response = client.get(agentType);
          if (response == null && client.setnx(agentType, identity) == 1) {
            client.pexpireAt(agentType, System.currentTimeMillis() + timeout);
            return true;
          }

          if (client.ttl(agentType) == -1) {
            log.warn("Detected potential deadlocked agent, removing lock key: " + agentType);
            client.del(agentType);
          }
          return false;
        });
    });
  }

  private void runAgents() {
    Map<String, NextAttempt> thisRun = acquire();
    activeAgents.putAll(thisRun);
    for (final Map.Entry<String, NextAttempt> toRun : thisRun.entrySet()) {
      final AgentExecutionAction exec = agents.get(toRun.getKey());
      agentExecutionPool.submit(new AgentJob(toRun.getValue(), exec, this));
    }
  }

  private void agentCompleted(String agentType, long nextExecutionTime) {
    try {
      releaseRunKey(agentType, nextExecutionTime);
    } finally {
      activeAgents.remove(agentType);
    }
  }

  private void releaseRunKey(String agentType, long when) {
    final long newTtl = when - System.currentTimeMillis();
    final boolean delete = newTtl < 2500L;
    redisClientDelegate.withCommandsClient(client -> {
      if (delete) {
        deleteLock(client, agentType);
      } else {
        ttlLock(client, agentType, newTtl);
      }
    });
  }

  private void deleteLock(JedisCommands client, String agentType) {
    client.del(agentType);
  }

  private void ttlLock(JedisCommands client, String agentType, long newTtl) {
    String response = client.get(agentType);
    if (nodeIdentity.getNodeIdentity().equals(response)) {
      client.pexpireAt(agentType, System.currentTimeMillis() + newTtl);
    }
  }

  private static class NextAttempt {
    private final long currentTime;
    private final long successInterval;
    private final long errorInterval;

    public NextAttempt(long currentTime, long successInterval, long errorInterval) {
      this.currentTime = currentTime;
      this.successInterval = successInterval;
      this.errorInterval = errorInterval;
    }

    public long getNextTime(Status status) {
      if (status == Status.SUCCESS) {
        return currentTime + successInterval;
      }

      return currentTime + errorInterval;
    }
  }

  private static class AgentJob implements Runnable {
    private final NextAttempt lockReleaseTime;
    private final AgentExecutionAction action;
    private final DynoClusteredAgentScheduler scheduler;

    public AgentJob(NextAttempt lockReleaseTime, AgentExecutionAction action, DynoClusteredAgentScheduler scheduler) {
      this.lockReleaseTime = lockReleaseTime;
      this.action = action;
      this.scheduler = scheduler;
    }

    @Override
    public void run() {
      Status status = Status.FAILURE;
      try {
        status = action.execute();
      } finally {
        scheduler.agentCompleted(action.getAgent().getAgentType(), lockReleaseTime.getNextTime(status));
      }
    }
  }

  private static class AgentExecutionAction {
    private final Agent agent;
    private final AgentExecution agentExecution;
    private final ExecutionInstrumentation executionInstrumentation;

    public AgentExecutionAction(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
      this.agent = agent;
      this.agentExecution = agentExecution;
      this.executionInstrumentation = executionInstrumentation;
    }

    public Agent getAgent() {
      return agent;
    }

    public Status execute() {
      try {
        executionInstrumentation.executionStarted(agent);
        long startTime = System.nanoTime();
        agentExecution.executeAgent(agent);
        executionInstrumentation.executionCompleted(agent, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
        return Status.SUCCESS;
      } catch (Throwable cause) {
        executionInstrumentation.executionFailed(agent, cause);
        return Status.FAILURE;
      }
    }

  }
}
