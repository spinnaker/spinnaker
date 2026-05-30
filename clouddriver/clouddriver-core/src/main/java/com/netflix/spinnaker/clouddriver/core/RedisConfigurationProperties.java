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

package com.netflix.spinnaker.clouddriver.core;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties("redis")
public class RedisConfigurationProperties {

  @Data
  public static class PollConfiguration {
    private int intervalSeconds = 30;
    private int errorIntervalSeconds = 30;
    private int timeoutSeconds = 300;
  }

  @Data
  public static class AgentConfiguration {
    private String enabledPattern = ".*";
    private String disabledPattern = "";
    private Integer maxConcurrentAgents;
    private Integer agentLockAcquisitionIntervalSeconds;
    private List<String> disabledAgents = new ArrayList<>();
  }

  @Data
  public static class SchedulerProperties {
    private String type = "default";
    private boolean enabled = true;
    private int parallelism = -1;
  }

  @NestedConfigurationProperty private final PollConfiguration poll = new PollConfiguration();

  @NestedConfigurationProperty private final AgentConfiguration agent = new AgentConfiguration();

  @NestedConfigurationProperty
  private final SchedulerProperties scheduler = new SchedulerProperties();

  private String connection = "redis://localhost:6379";
  private String connectionPrevious;

  private int timeout = 2000;
}
