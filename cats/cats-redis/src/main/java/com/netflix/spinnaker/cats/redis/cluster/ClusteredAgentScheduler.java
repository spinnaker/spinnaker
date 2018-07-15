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

package com.netflix.spinnaker.cats.redis.cluster;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressFBWarnings
public class ClusteredAgentScheduler extends CatsModuleAware implements AgentScheduler<AgentLock>, Runnable {
  private static enum Status {
    SUCCESS,
    FAILURE
  }

  private static final Logger logger = LoggerFactory.getLogger(ClusteredAgentScheduler.class);

  private final RedisClientDelegate redisClientDelegate;
  private final NodeIdentity nodeIdentity;
  private final AgentIntervalProvider intervalProvider;
  private final ExecutorService agentExecutionPool;
  private final Pattern enabledAgentPattern;

  private final Map<String, AgentExecutionAction> agents = new ConcurrentHashMap<>();
  private final Map<String, NextAttempt> activeAgents = new ConcurrentHashMap<>();
  private final NodeStatusProvider nodeStatusProvider;
  private final DynamicConfigService dynamicConfigService;

  public ClusteredAgentScheduler(RedisClientDelegate redisClientDelegate,
                                 NodeIdentity nodeIdentity,
                                 AgentIntervalProvider intervalProvider,
                                 NodeStatusProvider nodeStatusProvider,
                                 String enabledAgentPattern,
                                 Integer agentLockAcquisitionIntervalSeconds,
                                 DynamicConfigService dynamicConfigService) {
    this(
      redisClientDelegate,
      nodeIdentity,
      intervalProvider,
      nodeStatusProvider,
      Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(ClusteredAgentScheduler.class.getSimpleName())),
      Executors.newCachedThreadPool(new NamedThreadFactory(AgentExecutionAction.class.getSimpleName())),
      enabledAgentPattern,
      agentLockAcquisitionIntervalSeconds,
      dynamicConfigService
    );
  }

  public ClusteredAgentScheduler(RedisClientDelegate redisClientDelegate,
                                 NodeIdentity nodeIdentity,
                                 AgentIntervalProvider intervalProvider,
                                 NodeStatusProvider nodeStatusProvider,
                                 ScheduledExecutorService lockPollingScheduler,
                                 ExecutorService agentExecutionPool,
                                 String enabledAgentPattern,
                                 Integer agentLockAcquisitionIntervalSeconds,
                                 DynamicConfigService dynamicConfigService) {
    this.redisClientDelegate = redisClientDelegate;
    this.nodeIdentity = nodeIdentity;
    this.intervalProvider = intervalProvider;
    this.nodeStatusProvider = nodeStatusProvider;
    this.agentExecutionPool = agentExecutionPool;
    this.enabledAgentPattern = Pattern.compile(enabledAgentPattern);
    this.dynamicConfigService = dynamicConfigService;
    Integer lockInterval = agentLockAcquisitionIntervalSeconds == null ? 1 : agentLockAcquisitionIntervalSeconds;

    lockPollingScheduler.scheduleAtFixedRate(this, 0, lockInterval, TimeUnit.SECONDS);
  }

  private Map<String, NextAttempt> acquire() {
    Set<String> skip = new HashSet<>(activeAgents.keySet());
    Integer maxConcurrentAgents = dynamicConfigService.getConfig(Integer.class, "redis.agent.maxConcurrentAgents", 1000);
    Integer availableAgents = maxConcurrentAgents - skip.size();
    if (availableAgents <= 0) {
      logger.debug("Not acquiring more locks (maxConcurrentAgents: {} activeAgents: {}, runningAgents: {})",
        maxConcurrentAgents,
        skip.size(),
        skip.stream().sorted().collect(Collectors.joining(","))
      );
      return Collections.emptyMap();
    }
    Map<String, NextAttempt> acquired = new HashMap<>(agents.size());
    // Shuffle the list before grabbing so that we don't favor some agents accidentally
    List<Map.Entry<String, AgentExecutionAction>> agentsEntrySet = new ArrayList<>(agents.entrySet());
    Collections.shuffle(agentsEntrySet);
    for (Map.Entry<String, AgentExecutionAction> agent : agentsEntrySet) {
      if (!skip.contains(agent.getKey())) {
        final String agentType = agent.getKey();
        AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent.getValue().getAgent());
        if (acquireRunKey(agentType, interval.getTimeout())) {
          acquired.put(agentType, new NextAttempt(System.currentTimeMillis(), interval.getInterval(), interval.getErrorInterval()));
        }
      }
      if (acquired.size() >= availableAgents) {
        return acquired;
      }
    }
    return acquired;
  }

  @Override
  public void run() {
    if (!nodeStatusProvider.isNodeEnabled()) {
      return;
    }
    try {
      runAgents();
    } catch (Throwable t) {
      logger.error("Unable to run agents", t);
    }
  }

  private void runAgents() {
    Map<String, NextAttempt> thisRun = acquire();
    activeAgents.putAll(thisRun);
    for (final Map.Entry<String, NextAttempt> toRun : thisRun.entrySet()) {
      final AgentExecutionAction exec = agents.get(toRun.getKey());
      agentExecutionPool.submit(new AgentJob(toRun.getValue(), exec, this));
    }
  }

  private static final long MIN_TTL_THRESHOLD = 500L;
  private static final String SET_IF_NOT_EXIST = "NX";
  private static final String SET_EXPIRE_TIME_MILLIS = "PX";
  private static final String SUCCESS_RESPONSE = "OK";
  private static final Long DEL_SUCCESS = 1L;

  private static final String DELETE_LOCK_KEY = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
  private static final String TTL_LOCK_KEY = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'XX') else return nil end";

  private boolean acquireRunKey(String agentType, long timeout) {
    return redisClientDelegate.withCommandsClient(client -> {
      String response = client.set(agentType, nodeIdentity.getNodeIdentity(), SET_IF_NOT_EXIST, SET_EXPIRE_TIME_MILLIS, timeout);
      return SUCCESS_RESPONSE.equals(response);
    });
  }

  private boolean deleteLock(String agentType) {
    return redisClientDelegate.withScriptingClient(client -> {
      Object response = client.eval(DELETE_LOCK_KEY, Arrays.asList(agentType), Arrays.asList(nodeIdentity.getNodeIdentity()));
      return DEL_SUCCESS.equals(response);
    });
  }

  private boolean ttlLock(String agentType, long newTtl) {
    return redisClientDelegate.withScriptingClient(client -> {
      Object response = client.eval(TTL_LOCK_KEY, Arrays.asList(agentType), Arrays.asList(nodeIdentity.getNodeIdentity(), Long.toString(newTtl)));
      return SUCCESS_RESPONSE.equals(response);
    });
  }

  private void releaseRunKey(String agentType, long when) {
    final long newTtl = when - System.currentTimeMillis();
    final boolean delete = newTtl < MIN_TTL_THRESHOLD;

    if (delete) {
      boolean success = deleteLock(agentType);
      if (!success) {
        logger.debug("Delete lock was unsuccessful for " + agentType);
      }
    } else {
      boolean success = ttlLock(agentType, newTtl);
      if (!success) {
        logger.debug("Ttl lock was unsuccessful for " + agentType);
      }
    }
  }

  private void agentCompleted(String agentType, long nextExecutionTime) {
    try {
      releaseRunKey(agentType, nextExecutionTime);
    } finally {
      activeAgents.remove(agentType);
    }
  }

  @Override
  public void schedule(Agent agent,
                       AgentExecution agentExecution,
                       ExecutionInstrumentation executionInstrumentation) {
    if (!enabledAgentPattern.matcher(agent.getAgentType().toLowerCase()).matches()) {
      logger.debug(
        "Agent is not enabled (agent: {}, agentType: {}, pattern: {})",
        agent.getClass().getSimpleName(),
        agent.getAgentType(),
        enabledAgentPattern.pattern()
      );
      return;
    }

    if (agent instanceof AgentSchedulerAware) {
      ((AgentSchedulerAware)agent).setAgentScheduler(this);
    }

    AgentExecutionAction agentExecutionAction = new AgentExecutionAction(
      agent, agentExecution, executionInstrumentation
    );
    agents.put(agent.getAgentType(), agentExecutionAction);
  }

  @Override
  public void unschedule(Agent agent) {
    releaseRunKey(agent.getAgentType(), 0); // Delete lock key now.
    agents.remove(agent.getAgentType());
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
    private final ClusteredAgentScheduler scheduler;

    public AgentJob(NextAttempt times, AgentExecutionAction action, ClusteredAgentScheduler scheduler) {
      this.lockReleaseTime = times;
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

    Status execute() {
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
