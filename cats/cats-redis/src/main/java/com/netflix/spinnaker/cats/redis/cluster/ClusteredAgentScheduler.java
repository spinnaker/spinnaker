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

import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.redis.JedisSource;
import com.netflix.spinnaker.cats.thread.NamedThreadFactory;
import redis.clients.jedis.Jedis;

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

public class ClusteredAgentScheduler extends CatsModuleAware implements AgentScheduler, Runnable {
    private final JedisSource jedisSource;
    private final NodeIdentity nodeIdentity;
    private final AgentIntervalProvider intervalProvider;
    private final ExecutorService agentExecutionPool;
    private final Map<String, AgentExecutionAction> agents = new ConcurrentHashMap<>();
    private final Map<String, Long> activeAgents = new ConcurrentHashMap<>();

    public ClusteredAgentScheduler(JedisSource jedisSource, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider) {
        this(jedisSource, nodeIdentity, intervalProvider, Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(ClusteredAgentScheduler.class.getSimpleName())), Executors.newCachedThreadPool(new NamedThreadFactory(AgentExecutionAction.class.getSimpleName())));
    }

    public ClusteredAgentScheduler(JedisSource jedisSource, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider, ScheduledExecutorService lockPollingScheduler, ExecutorService agentExecutionPool) {
        this.jedisSource = jedisSource;
        this.nodeIdentity = nodeIdentity;
        this.intervalProvider = intervalProvider;
        this.agentExecutionPool = agentExecutionPool;
        lockPollingScheduler.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
    }

    private Map<String, Long> acquire() {
        Map<String, Long> acquired = new HashMap<>(agents.size());
        Set<String> skip = new HashSet<>(activeAgents.keySet());
        for (Map.Entry<String, AgentExecutionAction> agent : agents.entrySet()) {
            if (!skip.contains(agent.getKey())) {
                final String agentType = agent.getKey();
                AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent.getValue().getAgent());
                if (acquireRunKey(agentType, interval.getTimeout())) {
                    acquired.put(agentType, System.currentTimeMillis() + interval.getInterval());
                }
            }
        }
        return acquired;
    }

    @Override
    public void run() {
        try {
            runAgents();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
    }

    private void runAgents() {
        Map<String, Long> thisRun = acquire();
        activeAgents.putAll(thisRun);
        for (final Map.Entry<String, Long> toRun : thisRun.entrySet()) {
            final AgentExecutionAction exec = agents.get(toRun.getKey());
            agentExecutionPool.submit(new AgentJob(toRun.getValue(), exec, this));
        }
    }

    private static final long MIN_TTL_THRESHOLD = 500L;
    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_EXPIRE_TIME_MILLIS = "PX";
    private static final String SUCCESS_RESPONSE = "OK";
    private static final Integer DEL_SUCCESS = 1;

    private static final String DELETE_LOCK_KEY = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private static final String TTL_LOCK_KEY = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'XX') else return nil end";

    private boolean acquireRunKey(String agentType, long timeout) {
        try (Jedis jedis = jedisSource.getJedis()) {
            String response = jedis.set(agentType, nodeIdentity.getNodeIdentity(), SET_IF_NOT_EXIST, SET_EXPIRE_TIME_MILLIS, timeout);
            return SUCCESS_RESPONSE.equals(response);
        }
    }

    private boolean deleteLock(Jedis jedis, String agentType) {
        Object response = jedis.eval(DELETE_LOCK_KEY, Arrays.asList(agentType), Arrays.asList(nodeIdentity.getNodeIdentity()));
        return DEL_SUCCESS.equals(response);
    }

    private boolean ttlLock(Jedis jedis, String agentType, long newTtl) {
        Object response = jedis.eval(TTL_LOCK_KEY, Arrays.asList(agentType), Arrays.asList(nodeIdentity.getNodeIdentity(), Long.toString(newTtl)));
        return SUCCESS_RESPONSE.equals(response);
    }

    private void releaseRunKey(String agentType, long when) {
        final long newTtl = when - System.currentTimeMillis();
        final boolean delete = newTtl < MIN_TTL_THRESHOLD;
        try (Jedis jedis = jedisSource.getJedis()) {
            if (delete) {
                deleteLock(jedis, agentType);
            } else {
                ttlLock(jedis, agentType, newTtl);
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
    public void schedule(Agent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        if (agent instanceof AgentSchedulerAware) {
          ((AgentSchedulerAware)agent).setAgentScheduler(this);
        }

        final AgentExecutionAction agentExecutionAction = new AgentExecutionAction(agent, agentExecution, executionInstrumentation);
        agents.put(agent.getAgentType(), agentExecutionAction);
    }

    @Override
    public void unschedule(Agent agent) {
        agents.remove(agent.getAgentType());
    }

    private static class AgentJob implements Runnable {
        private final long lockReleaseTime;
        private final AgentExecutionAction action;
        private final ClusteredAgentScheduler scheduler;

        public AgentJob(long lockReleaseTime, AgentExecutionAction action, ClusteredAgentScheduler scheduler) {
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
                agentExecution.executeAgent(agent);
                executionInstrumentation.executionCompleted(agent);
            } catch (Throwable cause) {
                executionInstrumentation.executionFailed(agent, cause);
            }
        }

    }
}
