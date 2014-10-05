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

import java.util.concurrent.TimeUnit;

/**
 * An AgentScheduler that executes on a fixed interval.
 *
 * This AgentScheduler will capture any exceptions thrown by the AgentExecution and
 * report them to the provided ExecutionInstrumentation.
 *
 * An exception thrown while reporting executionFailure will abort the schedule for
 * the CachingAgent.
 */
public class DefaultAgentScheduler implements AgentScheduler {
    private static final long DEFAULT_INTERVAL = 60000;

    private final RunnableScheduler rs;

    public DefaultAgentScheduler() {
        this(DEFAULT_INTERVAL);
    }

    public DefaultAgentScheduler(long interval) {
        this(interval, TimeUnit.MILLISECONDS);
    }

    public DefaultAgentScheduler(long interval, TimeUnit unit) {
        this(new FixedIntervalRunnableScheduler(DefaultAgentScheduler.class.getSimpleName(), interval, unit));
    }

    public DefaultAgentScheduler(RunnableScheduler rs) {
        this.rs = rs;
    }

    @Override
    public void schedule(CachingAgent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        rs.schedule(new AgentExecutionRunnable(agent, agentExecution, executionInstrumentation));
    }

    private static class AgentExecutionRunnable implements Runnable {
        private final CachingAgent agent;
        private final AgentExecution execution;
        private final ExecutionInstrumentation executionInstrumentation;

        public AgentExecutionRunnable(CachingAgent agent, AgentExecution execution, ExecutionInstrumentation executionInstrumentation) {
            this.agent = agent;
            this.execution = execution;
            this.executionInstrumentation = executionInstrumentation;
        }

        public void run() {
            try {
                executionInstrumentation.executionStarted(agent);
                execution.executeAgent(agent);
                executionInstrumentation.executionCompleted(agent);
            } catch (Throwable t) {
                executionInstrumentation.executionFailed(agent, t);
            }
        }
    }
}
