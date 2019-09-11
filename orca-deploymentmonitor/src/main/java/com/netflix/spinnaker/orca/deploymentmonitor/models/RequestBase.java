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

import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Map;
import lombok.Data;

@Data
public class RequestBase {
  private String application;
  private String executionId;
  private String stageId;
  private String oldServerGroup;
  private String newServerGroup;
  private String account;
  private String region;
  private String cloudProvider;
  private Map<String, Object> parameters;

  public RequestBase() {}

  public RequestBase(Stage stage) {
    fromStage(stage);
  }

  protected void fromStage(Stage stage) {
    application = stage.getExecution().getApplication();
    executionId = stage.getExecution().getId();
    stageId = stage.getId();

    MonitoredDeployInternalStageData stageData =
        stage.mapTo(MonitoredDeployInternalStageData.class);
    newServerGroup = stageData.getNewServerGroup();
    oldServerGroup = stageData.getOldServerGroup();
    account = stageData.getAccount();
    region = stageData.getRegion();
    cloudProvider = stageData.getCloudProvider();
    parameters = stageData.getParameters();
  }
}
