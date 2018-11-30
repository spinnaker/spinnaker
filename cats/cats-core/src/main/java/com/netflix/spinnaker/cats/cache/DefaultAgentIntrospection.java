/*
 * Copyright 2018 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.cats.cache;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.CacheResult;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.text.SimpleDateFormat;
import java.util.Map;

@Data
@NoArgsConstructor
public class DefaultAgentIntrospection implements AgentIntrospection {
  public DefaultAgentIntrospection(Agent agent) {
    this.lastExecutionStartMs = System.currentTimeMillis();
    this.id = agent.getAgentType();
    this.provider = agent.getProviderName();
  }

  public void finishWithError(Throwable error, CacheResult result) {
    lastError = error;
    finish(result);
  }

  public void finish(CacheResult result) {
    lastExecutionDurationMs = System.currentTimeMillis() - lastExecutionStartMs;
    details = result.getIntrospectionDetails();
    totalAdditions = result.getCacheResults().values().stream().reduce(0, (a, b) -> a + b.size(), (a, b) -> a + b);
    totalEvictions = result.getEvictions().values().stream().reduce(0, (a, b) -> a + b.size(), (a, b) -> a + b);
  }

  private String id;
  private String provider;
  private int totalAdditions;
  private int totalEvictions;
  private Map<String, Object> details;
  private Throwable lastError;
  private Long lastExecutionStartMs;
  private Long lastExecutionDurationMs;

  public String getLastExecutionStartDate() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(lastExecutionStartMs);
  }
}
