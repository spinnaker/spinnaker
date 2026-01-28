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

import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent filtering configuration properties for Redis priority scheduler.
 *
 * <p>This class caches agent-related configuration values to avoid dynamic config calls.
 * Configuration changes are applied through Spring Boot's configuration refresh mechanism.
 */
@Component
@ConfigurationProperties(prefix = "redis.agent")
@Getter
@Setter
public class PriorityAgentProperties {

  /** Regex pattern for enabled agents. Only agents matching this pattern will be scheduled. */
  private String enabledPattern = ".*";

  /**
   * Regex pattern for disabled agents. Agents matching this pattern will be disabled. More flexible
   * than disabledAgents list for complex filtering rules. Example: "(aws|gcp)-(test|dev)-.*"
   * disables all test/dev agents across clouds. Empty string means no pattern-based disabling.
   */
  private String disabledPattern = "";

  /** Maximum number of agents that can run concurrently. */
  private int maxConcurrentAgents = 100;

  @PostConstruct
  void validate() {
    // Validate enabled/disabled regex eagerly for fast fail at startup
    try {
      Pattern.compile(enabledPattern);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "redis.agent.enabledPattern is invalid: " + enabledPattern, e);
    }

    if (disabledPattern != null && !disabledPattern.isEmpty()) {
      try {
        Pattern.compile(disabledPattern);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "redis.agent.disabledPattern is invalid: " + disabledPattern, e);
      }
    }
    // maxConcurrentAgents: allow <=0 for unbounded as documented; no check here
  }
}
