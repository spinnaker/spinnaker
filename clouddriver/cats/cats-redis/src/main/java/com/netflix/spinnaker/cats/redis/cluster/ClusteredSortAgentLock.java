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
import com.netflix.spinnaker.cats.agent.AgentLock;

public class ClusteredSortAgentLock extends AgentLock {
  // The score the agent was acquired with (Used to ensure we own this agent on release).
  private final String acquireScore;
  // The score the agent was release from the WAITING set with (Used to ensure it is readded to the
  // WAITING set with the right score).
  private final String releaseScore;

  public ClusteredSortAgentLock(Agent agent, String acquireScore, String releaseScore) {
    super(agent);
    this.acquireScore = acquireScore;
    this.releaseScore = releaseScore;
  }

  public String getAcquireScore() {
    return acquireScore;
  }

  public String getReleaseScore() {
    return releaseScore;
  }
}
