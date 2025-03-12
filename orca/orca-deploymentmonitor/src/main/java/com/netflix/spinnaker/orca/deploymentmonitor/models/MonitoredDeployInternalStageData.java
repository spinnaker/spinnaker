/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.deploymentmonitor.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.Data;

// Stuff used in intermediate stages
@Data
public class MonitoredDeployInternalStageData {
  private String newServerGroup;
  private String oldServerGroup;
  private String account;
  private String region;
  private String cloudProvider;
  private int currentProgress;
  private boolean hasDeploymentFailed;
  private DeploymentMonitorStageConfig deploymentMonitor;
  private int deployMonitorHttpRetryCount;

  public Map toContextMap() {
    return new ObjectMapper().convertValue(this, Map.class);
  }
}
