/*
 * Copyright 2015 Google, Inc.
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

/**
 * This class is used to identify classes (typically Providers or Agents) that are capable of
 * returning the agent scheduler they are associated with.
 */
public abstract class AgentSchedulerAware {
  private AgentScheduler agentScheduler;

  /** Set this object's agent scheduler. */
  public void setAgentScheduler(AgentScheduler agentScheduler) {
    this.agentScheduler = agentScheduler;
  };

  /** Get this object's agent scheduler. */
  public AgentScheduler getAgentScheduler() {
    return agentScheduler;
  };
}
