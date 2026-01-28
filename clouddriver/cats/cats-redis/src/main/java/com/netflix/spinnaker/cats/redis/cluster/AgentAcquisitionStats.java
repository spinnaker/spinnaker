/*
 * Copyright 2025 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.redis.cluster;

/**
 * Advanced statistics for agent acquisition and execution tracking.
 *
 * <p>Provides detailed metrics for operational monitoring of the agent scheduling system.
 */
@lombok.Getter
public class AgentAcquisitionStats {
  private final long registeredAgents;
  private final long activeAgents;
  private final long agentsAcquired;
  private final long agentsExecuted;
  private final long agentsFailed;
  private final long futuresTracked;

  public AgentAcquisitionStats(
      long registeredAgents,
      long activeAgents,
      long agentsAcquired,
      long agentsExecuted,
      long agentsFailed,
      long futuresTracked) {
    this.registeredAgents = registeredAgents;
    this.activeAgents = activeAgents;
    this.agentsAcquired = agentsAcquired;
    this.agentsExecuted = agentsExecuted;
    this.agentsFailed = agentsFailed;
    this.futuresTracked = futuresTracked;
  }

  /**
   * Calculate success rate as a percentage.
   *
   * @return Success rate (0.0 to 100.0), or 0.0 if no executions
   */
  public double getSuccessRate() {
    long totalExecutions = agentsExecuted + agentsFailed;
    if (totalExecutions == 0) {
      return 0.0;
    }
    return (double) agentsExecuted / totalExecutions * 100.0;
  }

  /**
   * Calculate failure rate as a percentage.
   *
   * @return Failure rate (0.0 to 100.0), or 0.0 if no executions
   */
  public double getFailureRate() {
    long totalExecutions = agentsExecuted + agentsFailed;
    if (totalExecutions == 0) {
      return 0.0;
    }
    return (double) agentsFailed / totalExecutions * 100.0;
  }

  /**
   * Get a string representation of the statistics.
   *
   * @return String representation of the statistics
   */
  @Override
  public String toString() {
    return String.format(
        "AgentAcquisitionStats{registered=%d, active=%d, acquired=%d, executed=%d, failed=%d, futures=%d, success=%.1f%%, failure=%.1f%%}",
        registeredAgents,
        activeAgents,
        agentsAcquired,
        agentsExecuted,
        agentsFailed,
        futuresTracked,
        getSuccessRate(),
        getFailureRate());
  }
}
