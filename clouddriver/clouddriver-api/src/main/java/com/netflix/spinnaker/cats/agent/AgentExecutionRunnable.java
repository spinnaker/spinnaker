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

import static com.netflix.spinnaker.cats.agent.ExecutionInstrumentation.elapsedTimeMs;

public class AgentExecutionRunnable implements Runnable {
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
    long startTimeMs = System.currentTimeMillis();
    try {
      executionInstrumentation.executionStarted(agent);
      execution.executeAgent(agent);
      executionInstrumentation.executionCompleted(agent, elapsedTimeMs(startTimeMs));
    } catch (Throwable t) {
      executionInstrumentation.executionFailed(agent, t, elapsedTimeMs(startTimeMs));
    }
  }

  public AgentExecution getExecution() {
    return this.execution;
  }

  public Agent getAgent() {
    return this.agent;
  }
}
