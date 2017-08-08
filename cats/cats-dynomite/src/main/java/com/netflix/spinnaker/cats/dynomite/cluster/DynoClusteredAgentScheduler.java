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

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.redis.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCommands;

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
 * Shares a similiar strategy as ClusteredAgentScheduler, but doesn't use Lua, is slower and less safe. Dynomite
 * support for Lua is in-progress, so this class is rather temporary, then we can move to ClusteredSortAgentScheduler.
 */
public class DynoClusteredAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock>, Runnable {

  private final static Logger log = LoggerFactory.getLogger(DynoClusteredAgentScheduler.class);

  private final DynomiteClientDelegate redisClientDelegate;
  private final NodeIdentity nodeIdentity;
  private final AgentIntervalProvider intervalProvider;
  private final ExecutorService agentExecutionPool;
  private final Map<String, AgentExecutionAction> agents = new ConcurrentHashMap<>();
  private final Map<String, Long> activeAgents = new ConcurrentHashMap<>();
  private final NodeStatusProvider nodeStatusProvider;

  public DynoClusteredAgentScheduler(DynomiteClientDelegate redisClientDelegate, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider, NodeStatusProvider nodeStatusProvider) {
    this(redisClientDelegate, nodeIdentity, intervalProvider, nodeStatusProvider, Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(ClusteredAgentScheduler.class.getSimpleName())), Executors.newCachedThreadPool(new NamedThreadFactory(AgentExecutionAction.class.getSimpleName())));
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

  private Map<String, Long> acquire() {
    Map<String, Long> acquired = new HashMap<>(agents.size());
    Set<String> skip = new HashSet<>(activeAgents.keySet());
    agents.entrySet().stream()
      .filter(a -> !skip.contains(a.getKey()))
      .forEach(a -> {
        final String agentType = a.getKey();
        AgentIntervalProvider.Interval interval = intervalProvider.getInterval(a.getValue().getAgent());
        if (acquireRunKey(agentType, interval.getTimeout())) {
          acquired.put(agentType, System.currentTimeMillis() + interval.getInterval());
        }
      });
    return acquired;
  }

  private boolean acquireRunKey(String agentType, long timeout) {
    // This isn't as safe as the vanilla Redis impl because the call isn't atomic, but it's the best we can do until
    // dynomite adds support for `String set(String key, String value, String nxxx, String expx, long time)` (which
    // they are working on).
    return redisClientDelegate.withCommandsClient(client -> {
      String response = client.get(agentType);
      if (response == null) {
        // Purges potentially deadlocked agents, which can happen if the conn is interrupted between setnx & pexpireAt.
        boolean purge = true;
        for (int i = 0; i <= 3; i++) {
          if (client.ttl(agentType) > 0) {
            purge = false;
            break;
          }
          try {
            Thread.sleep(25);
          } catch (InterruptedException e) {
            purge = false;
            break;
          }
        }
        if (purge) {
          log.warn("Detected deadlocked agent, removing lock key: " + agentType);
          client.del(agentType);
        }

        if (client.setnx(agentType, nodeIdentity.getNodeIdentity()) == 1) {
          client.pexpireAt(agentType, System.currentTimeMillis() + timeout);
        }
        return true;
      }
      return false;
    });
  }

  private void runAgents() {
    Map<String, Long> thisRun = acquire();
    activeAgents.putAll(thisRun);
    for (final Map.Entry<String, Long> toRun : thisRun.entrySet()) {
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

  private static class AgentJob implements Runnable {
    private final long lockReleaseTime;
    private final AgentExecutionAction action;
    private final DynoClusteredAgentScheduler scheduler;

    public AgentJob(long lockReleaseTime, AgentExecutionAction action, DynoClusteredAgentScheduler scheduler) {
      this.lockReleaseTime = lockReleaseTime;
      this.action = action;
      this.scheduler = scheduler;
    }

    @Override
    public void run() {
      try {
        action.execute();
      } finally {
        scheduler.agentCompleted(action.getAgent().getAgentType(), lockReleaseTime);
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

    public void execute() {
      try {
        executionInstrumentation.executionStarted(agent);
        long startTime = System.nanoTime();
        agentExecution.executeAgent(agent);
        executionInstrumentation.executionCompleted(agent, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
      } catch (Throwable cause) {
        executionInstrumentation.executionFailed(agent, cause);
      }
    }

  }
}
