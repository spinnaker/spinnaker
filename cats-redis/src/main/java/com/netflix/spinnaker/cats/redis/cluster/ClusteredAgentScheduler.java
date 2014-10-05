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
import com.netflix.spinnaker.cats.redis.JedisSource;
import redis.clients.jedis.Jedis;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class ClusteredAgentScheduler implements AgentScheduler {
    final JedisSource jedisSource;
    final NodeIdentity nodeIdentity;
    final AgentIntervalProvider intervalProvider;
    final RunnableScheduler runnableScheduler;

    public ClusteredAgentScheduler(JedisSource jedisSource, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider) {
        this(jedisSource, nodeIdentity, intervalProvider, new FixedIntervalRunnableScheduler(ClusteredAgentScheduler.class.getSimpleName(), 1, TimeUnit.SECONDS));
    }

    public ClusteredAgentScheduler(JedisSource jedisSource, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider, RunnableScheduler runnableScheduler) {
        this.jedisSource = jedisSource;
        this.nodeIdentity = nodeIdentity;
        this.intervalProvider = intervalProvider;
        this.runnableScheduler = runnableScheduler;
    }

    @Override
    public void schedule(CachingAgent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        final Runnable runnable = new AgentExecutionRunnable(jedisSource, agent, agentExecution, executionInstrumentation, nodeIdentity, intervalProvider);
        runnableScheduler.schedule(runnable);
    }

    private static class AgentExecutionRunnable implements Runnable {
        private final JedisSource jedisSource;
        private final CachingAgent agent;
        private final AgentExecution agentExecution;
        private final ExecutionInstrumentation executionInstrumentation;
        private final NodeIdentity nodeIdentity;
        private final AgentIntervalProvider intervalProvider;


        public AgentExecutionRunnable(JedisSource jedisSource, CachingAgent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation, NodeIdentity nodeIdentity, AgentIntervalProvider intervalProvider) {
            this.jedisSource = jedisSource;
            this.agent = agent;
            this.agentExecution = agentExecution;
            this.executionInstrumentation = executionInstrumentation;
            this.nodeIdentity = nodeIdentity;
            this.intervalProvider = intervalProvider;
        }

        @Override
        public void run() {
            try {
                AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
                long nextExec = System.currentTimeMillis() + interval.getInterval();
                if (!acquireRunKey(interval.getTimeout())) {
                    return;
                }
                try {
                    executionInstrumentation.executionStarted(agent);
                    agentExecution.executeAgent(agent);
                    executionInstrumentation.executionCompleted(agent);
                } catch (Throwable cause) {
                    executionInstrumentation.executionFailed(agent, cause);
                } finally {
                    releaseRunKey(nextExec);
                }
            } catch (Throwable cause) {
                executionInstrumentation.executionFailed(agent, cause);
            }
        }

        private static final long MIN_TTL_THRESHOLD = 500L;
        private static final String SET_IF_NOT_EXIST = "NX";
        private static final String SET_EXPIRE_TIME_MILLIS = "PX";
        private static final String SUCCESS_RESPONSE = "OK";
        private static final Integer DEL_SUCCESS = 1;

        private static final String DELETE_LOCK_KEY = "eval \"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end\"";
        private static final String TTL_LOCK_KEY = "eval \"if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'XX') else return nil end\"";

        private boolean acquireRunKey(long timeout) {
            try (Jedis jedis = jedisSource.getJedis()) {
                String response = jedis.set(agent.getAgentType(), nodeIdentity.getNodeIdentity(), SET_IF_NOT_EXIST, SET_EXPIRE_TIME_MILLIS, timeout);
                return SUCCESS_RESPONSE.equals(response);
            }
        }

        private boolean deleteLock(Jedis jedis) {
            Object response = jedis.eval(DELETE_LOCK_KEY, Arrays.asList(agent.getAgentType()), Arrays.asList(nodeIdentity.getNodeIdentity()));
            return DEL_SUCCESS.equals(response);
        }

        private boolean ttlLock(Jedis jedis, long newTtl) {
            Object response = jedis.eval(TTL_LOCK_KEY, Arrays.asList(agent.getAgentType()), Arrays.asList(nodeIdentity.getNodeIdentity(), Long.toString(newTtl)));
            return SUCCESS_RESPONSE.equals(response);
        }

        private void releaseRunKey(long when) {
            final long newTtl = when - System.currentTimeMillis();
            final boolean delete = newTtl < MIN_TTL_THRESHOLD;
            try (Jedis jedis = jedisSource.getJedis()) {
                if (delete) {
                    deleteLock(jedis);
                } else {
                    ttlLock(jedis, newTtl);
                }
            }
        }
    }
}
