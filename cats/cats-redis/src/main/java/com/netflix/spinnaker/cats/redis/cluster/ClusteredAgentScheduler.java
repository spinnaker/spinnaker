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

import static com.netflix.spinnaker.cats.agent.ExecutionInstrumentation.elapsedTimeMs;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentLock;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeIdentity;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.params.SetParams;

public class ClusteredAgentScheduler extends CatsModuleAware
    implements AgentScheduler<AgentLock>, Runnable {
  private enum Status {
    SUCCESS,
    FAILURE
  }

  private static final Logger logger = LoggerFactory.getLogger(ClusteredAgentScheduler.class);

  private final RedisClientDelegate redisClientDelegate;
  private final NodeIdentity nodeIdentity;
  private final AgentIntervalProvider intervalProvider;
  private final ExecutorService agentExecutionPool;
  private final Pattern enabledAgentPattern;

  /**
   * this contains all the known agents (from all cloud providers) that are candidates for execution
   */
  @Getter // visible for tests
  private final Map<String, AgentExecutionAction> agents = new ConcurrentHashMap<>();

  /** This contains all the agents that are currently scheduled for execution */
  @Getter // visible for tests
  private final Map<String, NextAttempt> activeAgents = new ConcurrentHashMap<>();

  private final NodeStatusProvider nodeStatusProvider;
  private final DynamicConfigService dynamicConfigService;
  private final ShardingFilter shardingFilter;

  private static final long MIN_TTL_THRESHOLD = 500L;
  private static final String SET_IF_NOT_EXIST = "NX";
  private static final String SET_EXPIRE_TIME_MILLIS = "PX";
  private static final String SUCCESS_RESPONSE = "OK";
  private static final Long DEL_SUCCESS = 1L;

  @Getter // visible for tests
  private static final String DELETE_LOCK_KEY =
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

  @Getter // visible for tests
  private static final String TTL_LOCK_KEY =
      "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'XX') else return nil end";

  public ClusteredAgentScheduler(
      RedisClientDelegate redisClientDelegate,
      NodeIdentity nodeIdentity,
      AgentIntervalProvider intervalProvider,
      NodeStatusProvider nodeStatusProvider,
      String enabledAgentPattern,
      Integer agentLockAcquisitionIntervalSeconds,
      DynamicConfigService dynamicConfigService,
      ShardingFilter shardingFilter) {
    this(
        redisClientDelegate,
        nodeIdentity,
        intervalProvider,
        nodeStatusProvider,
        Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder()
                .setNameFormat(ClusteredAgentScheduler.class.getSimpleName() + "-%d")
                .build()),
        Executors.newCachedThreadPool(
            new ThreadFactoryBuilder()
                .setNameFormat(AgentExecutionAction.class.getSimpleName() + "-%d")
                .build()),
        enabledAgentPattern,
        agentLockAcquisitionIntervalSeconds,
        dynamicConfigService,
        shardingFilter);
  }

  public ClusteredAgentScheduler(
      RedisClientDelegate redisClientDelegate,
      NodeIdentity nodeIdentity,
      AgentIntervalProvider intervalProvider,
      NodeStatusProvider nodeStatusProvider,
      ScheduledExecutorService lockPollingScheduler,
      ExecutorService agentExecutionPool,
      String enabledAgentPattern,
      Integer agentLockAcquisitionIntervalSeconds,
      DynamicConfigService dynamicConfigService,
      ShardingFilter shardingFilter) {
    this.redisClientDelegate = redisClientDelegate;
    this.nodeIdentity = nodeIdentity;
    this.intervalProvider = intervalProvider;
    this.nodeStatusProvider = nodeStatusProvider;
    this.agentExecutionPool = agentExecutionPool;
    this.enabledAgentPattern = Pattern.compile(enabledAgentPattern);
    this.dynamicConfigService = dynamicConfigService;
    this.shardingFilter = shardingFilter;
    Integer lockInterval =
        agentLockAcquisitionIntervalSeconds == null ? 1 : agentLockAcquisitionIntervalSeconds;

    lockPollingScheduler.scheduleAtFixedRate(this, 0, lockInterval, TimeUnit.SECONDS);
  }

  private Map<String, NextAttempt> acquire() {
    Set<String> skip = new HashSet<>(activeAgents.keySet());
    Integer maxConcurrentAgents =
        dynamicConfigService.getConfig(Integer.class, "redis.agent.max-concurrent-agents", 1000);
    Integer availableAgents = maxConcurrentAgents - skip.size();
    if (availableAgents <= 0) {
      logger.debug(
          "Not acquiring more locks (maxConcurrentAgents: {} activeAgents: {}, runningAgents: {})",
          maxConcurrentAgents,
          skip.size(),
          skip.stream().sorted().collect(Collectors.joining(",")));
      return Collections.emptyMap();
    }
    Map<String, NextAttempt> acquired = new HashMap<>(agents.size());
    // Shuffle the list before grabbing so that we don't favor some agents accidentally
    List<Map.Entry<String, AgentExecutionAction>> agentsEntrySet =
        new ArrayList<>(agents.entrySet());
    Collections.shuffle(agentsEntrySet);
    for (Map.Entry<String, AgentExecutionAction> agent : agentsEntrySet) {
      if (shardingFilter.filter(agent.getValue().getAgent()) && !skip.contains(agent.getKey())) {
        final String agentType = agent.getKey();
        AgentIntervalProvider.Interval interval =
            intervalProvider.getInterval(agent.getValue().getAgent());
        if (acquireRunKey(agentType, interval.getTimeout())) {
          acquired.put(
              agentType,
              new NextAttempt(
                  System.currentTimeMillis(),
                  interval.getInterval(),
                  interval.getErrorInterval(),
                  interval.getTimeout()));
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
      pruneActiveAgents();
      runAgents();
    } catch (Throwable t) {
      logger.error("Unable to run agents", t);
    }
  }

  /**
   * this method removes agents from the {@link #activeAgents} map based on the following criteria:
   *
   * <p>- each agent has a max timeout interval associated with it. If it is present in the {@link
   * #activeAgents} map for longer than this timeout value, then it is removed from this map.
   *
   * <p>NOTE: This same timeout interval is used when {@link #acquireRunKey(String, long)} is
   * invoked from {@link #acquire()}.
   *
   * <p>The motivation for actively cleaning such entries from the map is to ensure that no agent is
   * in such a bad state that it can't be rescheduled again. In a normal workflow, the agent is
   * removed from the map when {@link #agentCompleted(String, long)} is called from {@link #run()}
   * method after its execution. But, if for some reason, that thread is killed, and the {@link
   * #agentCompleted(String, long)} is not called, then this agent stays in the {@link
   * #activeAgents} map, which means it won't be rescheduled again. So by actively doing something
   * like this, we enable it to be rescheduled.
   */
  private void pruneActiveAgents() {
    final long currentTime = System.currentTimeMillis();
    int count = 0;
    for (final Map.Entry<String, NextAttempt> activeAgent : activeAgents.entrySet()) {
      // max time upto which an agent can remain active
      long removalTime = activeAgent.getValue().currentTime + activeAgent.getValue().timeout;

      // atleast allow an agent to be active for MIN_TTL_THRESHOLD ms.
      // this is the same threshold used in the releaseRunKey() as well
      if (removalTime + MIN_TTL_THRESHOLD < currentTime) {
        logger.info(
            "removing agent: {} from the active agents map as its max execution time"
                + " has elapsed",
            activeAgent.getKey());
        activeAgents.remove(activeAgent.getKey());
        count++;
      }
    }

    if (count > 0) {
      logger.info(
          "removed {} accounts from the active agents map as their max execution times have elapsed",
          count);
    }
  }

  private void runAgents() {
    Map<String, NextAttempt> thisRun = acquire();
    activeAgents.putAll(thisRun);
    logger.debug(
        "scheduling {} new agents, total number of active agents: {}",
        thisRun.size(),
        activeAgents.size());
    for (final Map.Entry<String, NextAttempt> toRun : thisRun.entrySet()) {
      final AgentExecutionAction exec = agents.get(toRun.getKey());
      agentExecutionPool.submit(new AgentJob(toRun.getValue(), exec, this));
    }
  }

  private boolean acquireRunKey(String agentType, long timeout) {
    return redisClientDelegate.withCommandsClient(
        client -> {
          String response =
              client.set(
                  agentType,
                  nodeIdentity.getNodeIdentity(),
                  SetParams.setParams().nx().px(timeout));
          return SUCCESS_RESPONSE.equals(response);
        });
  }

  private boolean deleteLock(String agentType) {
    return redisClientDelegate.withScriptingClient(
        client -> {
          Object response =
              client.eval(
                  DELETE_LOCK_KEY,
                  Arrays.asList(agentType),
                  Arrays.asList(nodeIdentity.getNodeIdentity()));
          return DEL_SUCCESS.equals(response);
        });
  }

  private boolean ttlLock(String agentType, long newTtl) {
    return redisClientDelegate.withScriptingClient(
        client -> {
          Object response =
              client.eval(
                  TTL_LOCK_KEY,
                  Arrays.asList(agentType),
                  Arrays.asList(nodeIdentity.getNodeIdentity(), Long.toString(newTtl)));
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
  public void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    if (!enabledAgentPattern.matcher(agent.getAgentType().toLowerCase()).matches()) {
      logger.debug(
          "Agent is not enabled (agent: {}, agentType: {}, pattern: {})",
          agent.getClass().getSimpleName(),
          agent.getAgentType(),
          enabledAgentPattern.pattern());
      return;
    }

    if (agent instanceof AgentSchedulerAware) {
      ((AgentSchedulerAware) agent).setAgentScheduler(this);
    }

    AgentExecutionAction agentExecutionAction =
        new AgentExecutionAction(agent, agentExecution, executionInstrumentation);
    agents.put(agent.getAgentType(), agentExecutionAction);
  }

  /**
   *
   *
   * <pre>
   * Removes an agent from redis, {@link #agents} and {@link #activeAgents} maps.
   *
   * NOTE: we are explicitly removing the agent from the {@link #activeAgents} map. Normally, the agent is
   * removed from it when {@link #agentCompleted(String, long)} is called after it executes via
   * {@link AgentJob#run()}. But if for some reason that thread is killed before
   * {@link #agentCompleted(String, long)} is executed, then this agent is not removed from the
   * {@link #activeAgents} map, and that means it won't be executed again if this agent is scheduled
   * again in future.
   *
   * PS: if accounts are not updated/deleted dynamically, this method will not be invoked, so the
   *     agent can still remain in the {@link #activeAgents} map
   * </pre>
   *
   * @param agent agent under consideration
   */
  @Override
  public void unschedule(Agent agent) {
    try {
      releaseRunKey(agent.getAgentType(), 0); // Delete lock key now.
    } finally {
      agents.remove(agent.getAgentType());
      // explicitly remove it from the active agents map
      activeAgents.remove(agent.getAgentType());
    }
  }

  private static class NextAttempt {
    private final long currentTime;
    private final long successInterval;
    private final long errorInterval;
    private final long timeout;

    public NextAttempt(long currentTime, long successInterval, long errorInterval, long timeout) {
      this.currentTime = currentTime;
      this.successInterval = successInterval;
      this.errorInterval = errorInterval;
      this.timeout = timeout;
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

    public AgentJob(
        NextAttempt times, AgentExecutionAction action, ClusteredAgentScheduler scheduler) {
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
        scheduler.agentCompleted(
            action.getAgent().getAgentType(), lockReleaseTime.getNextTime(status));
      }
    }
  }

  private static class AgentExecutionAction {
    private final Agent agent;
    private final AgentExecution agentExecution;
    private final ExecutionInstrumentation executionInstrumentation;

    public AgentExecutionAction(
        Agent agent,
        AgentExecution agentExecution,
        ExecutionInstrumentation executionInstrumentation) {
      this.agent = agent;
      this.agentExecution = agentExecution;
      this.executionInstrumentation = executionInstrumentation;
    }

    public Agent getAgent() {
      return agent;
    }

    Status execute() {
      long startTimeMs = System.currentTimeMillis();
      try {
        executionInstrumentation.executionStarted(agent);
        agentExecution.executeAgent(agent);
        executionInstrumentation.executionCompleted(agent, elapsedTimeMs(startTimeMs));
        return Status.SUCCESS;
      } catch (Throwable cause) {
        executionInstrumentation.executionFailed(agent, cause, elapsedTimeMs(startTimeMs));
        return Status.FAILURE;
      }
    }
  }
}
