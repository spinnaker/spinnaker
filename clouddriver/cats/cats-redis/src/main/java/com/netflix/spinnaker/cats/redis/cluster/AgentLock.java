/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.cats.agent.Agent;
import lombok.Getter;

/**
 * Lock holder for an acquired agent with associated Redis scores.
 *
 * <p>Fields:
 *
 * <ul>
 *   <li>{@code acquireScore} - score the agent was acquired with (used to verify ownership on
 *       release)
 *   <li>{@code releaseScore} - score for re-adding to the waiting set after completion
 * </ul>
 */
@Getter
public class AgentLock extends com.netflix.spinnaker.cats.agent.AgentLock {

  /** Score the agent was acquired with (used to verify ownership on release). */
  private final String acquireScore;

  /** Score for re-adding to the waiting set after completion. */
  private final String releaseScore;

  /**
   * Constructs an AgentLock.
   *
   * @param agent the agent associated with this lock
   * @param acquireScore the score the agent was acquired with
   * @param releaseScore the score for re-adding to the waiting set
   */
  public AgentLock(Agent agent, String acquireScore, String releaseScore) {
    super(agent);
    this.acquireScore = acquireScore;
    this.releaseScore = releaseScore;
  }
}
