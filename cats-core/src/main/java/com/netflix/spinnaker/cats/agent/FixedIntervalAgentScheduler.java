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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An AgentScheduler that executes on a fixed interval.
 *
 * This AgentScheduler will capture any exceptions thrown by the AgentExecution and
 * report them to the provided ExecutionInstrumentation.
 *
 * An exception thrown while reporting executionFailure will abort the schedule for
 * the CachingAgent.
 */
public class FixedIntervalAgentScheduler implements AgentScheduler {
    public static final long DEFAULT_INTERVAL_SECONDS = 60;
    private final long interval;
    private final TimeUnit unit;
    private final ScheduledExecutorService executorService;

    public FixedIntervalAgentScheduler() {
        this(DEFAULT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public FixedIntervalAgentScheduler(long interval, TimeUnit unit) {
        this.interval = interval;
        this.unit = unit;
        executorService = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new AgentThreadFactory());
    }

    @Override
    public void schedule(CachingAgent agent, AgentExecution agentExecution, ExecutionInstrumentation executionInstrumentation) {
        executorService.scheduleAtFixedRate(new AgentExecutionRunnable(agent, agentExecution, executionInstrumentation), 0, interval, unit);
    }

    public void shutdown() {
        executorService.shutdown();
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

    private static class AgentThreadFactory implements ThreadFactory {
        private final AtomicLong threadNumber = new AtomicLong();
        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(FixedIntervalAgentScheduler.class.getSimpleName() + "-" + threadNumber.incrementAndGet());
            return t;
        }
    }
}
