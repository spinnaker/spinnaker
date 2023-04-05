/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;

class MetricInstrumentation implements ExecutionInstrumentation {

  private final Registry registry;
  private final Id timingId;

  @Autowired
  MetricInstrumentation(Registry registry) {
    this.registry = registry;
    timingId =
        registry
            .createId("executionTime")
            .withTag("className", MetricInstrumentation.class.getSimpleName());
  }

  private static String stripPackageName(String className) {
    return className.substring(className.lastIndexOf('.') + 1);
  }

  private static String agentName(Agent agent) {
    String simpleProviderName = stripPackageName(agent.getProviderName());
    return String.format("%s/%s", simpleProviderName, agent.getAgentType());
  }

  @Override
  public void executionStarted(Agent agent) {
    // do nothing
  }

  @Override
  public void executionCompleted(Agent agent, long elapsedMs) {
    registry
        .timer(timingId.withTag("agent", agentName(agent)).withTag("success", "true"))
        .record(elapsedMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void executionFailed(Agent agent, Throwable cause, long elapsedMs) {
    registry
        .timer(timingId.withTag("agent", agentName(agent)).withTag("success", "false"))
        .record(elapsedMs, TimeUnit.MILLISECONDS);
  }
}
