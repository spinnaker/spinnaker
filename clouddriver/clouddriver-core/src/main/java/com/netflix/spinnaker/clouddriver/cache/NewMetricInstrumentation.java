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
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;

class NewMetricInstrumentation implements ExecutionInstrumentation {

  private final Registry registry;
  private final Id executionTimeId;
  private final Id completedExecutionsId;
  private final Id startedExecutionsId;
  private final ConcurrentMap<String, LongTaskTimer> timers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Long> timerIds = new ConcurrentHashMap<>();

  @Autowired
  NewMetricInstrumentation(Registry registry) {
    this.registry = registry;
    executionTimeId =
        registry
            .createId("executionTime")
            .withTag("className", NewMetricInstrumentation.class.getSimpleName());
    completedExecutionsId =
        registry
            .createId("completedExecutions")
            .withTag("className", NewMetricInstrumentation.class.getSimpleName());
    startedExecutionsId =
        registry
            .createId("startedExecutions")
            .withTag("className", NewMetricInstrumentation.class.getSimpleName());
  }

  private static String stripPackageName(String className) {
    // returns class name only from fqdn class name
    // eg.: com.wise.SimpleMetric -> SimpleMetric
    return className.substring(className.lastIndexOf('.') + 1);
  }

  private static String agentName(Agent agent) {
    String simpleProviderName = stripPackageName(agent.getProviderName());
    return String.format("%s/%s", simpleProviderName, agent.getAgentType());
  }

  @Override
  public void executionStarted(Agent agent) {
    var agentName = agentName(agent);
    LongTaskTimer timer =
        timers.computeIfAbsent(
            agentName, k -> registry.longTaskTimer(executionTimeId.withTag("agent", agentName)));
    if (timerIds.containsKey(agentName)) {
      timer.stop(timerIds.get(agentName));
    }
    Long taskId = timer.start();
    timerIds.put(agentName, taskId);
    registry.counter(startedExecutionsId.withTag("agent", agentName)).increment();
  }

  @Override
  public void executionCompleted(Agent agent, long elapsedMs) {
    var agentName = agentName(agent);
    LongTaskTimer timer = timers.get(agentName);
    timer.stop(timerIds.get(agentName));
    registry
        .counter(completedExecutionsId.withTag("agent", agentName).withTag("success", "true"))
        .increment();
    timerIds.remove(agentName);
  }

  @Override
  public void executionFailed(Agent agent, Throwable cause, long elapsedMs) {
    var agentName = agentName(agent);
    LongTaskTimer timer = timers.get(agentName);
    timer.stop(timerIds.get(agentName));
    registry
        .counter(completedExecutionsId.withTag("agent", agentName).withTag("success", "false"))
        .increment();
    timerIds.remove(agentName);
  }
}
