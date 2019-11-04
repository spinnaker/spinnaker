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

import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.Map;
import lombok.Data;

@Data
public class RequestBase {
  /** Name of the application which is being deployed */
  private String application;

  /**
   * The execution ID of this deployment This ID will stay the same through the execution of this
   * pipeline (e.g. {@link DeploymentMonitorService#notifyStarting}, {@link
   * DeploymentMonitorService#notifyCompleted}, and {@link DeploymentMonitorService#evaluateHealth})
   * will all receive the same executionId For example, if there are 3 clusters deployed in a given
   * pipeline with the monitored strategy all will get the same execution ID
   */
  private String executionId;

  /**
   * Stage ID for this request -this ID is always unique. A call to {@link
   * DeploymentMonitorService#notifyStarting} and {@link DeploymentMonitorService#evaluateHealth}
   * for the same cluster deployment will have a different stageId HOWEVER, it will stay the same
   * for multiple calls to {@link DeploymentMonitorService#evaluateHealth} for the same
   * percentage/step of the same deployment
   */
  private String stageId;

  /**
   * ID of this deployment This ID will stay the same across all calls to the monitor for a given
   * cluster deployment
   */
  private String deploymentId;

  /** Previous server group name */
  private String oldServerGroup;

  /** New server group name */
  private String newServerGroup;

  /** Account the new server group is being deployed to */
  private String account;

  /** Region/location the new server group is being deployed to */
  private String region;

  /** Cloud provider used to deploy the new server group */
  private String cloudProvider;

  /**
   * Parameters, as specified by the user in their pipeline configuration This is an opaque map -
   * Spinnaker doesn't use this information
   */
  private Map<String, Object> parameters;

  public RequestBase() {}

  public RequestBase(Stage stage) {
    fromStage(stage);
  }

  protected void fromStage(Stage stage) {
    application = stage.getExecution().getApplication();
    executionId = stage.getExecution().getId();
    stageId = stage.getId();
    deploymentId = stage.getParentStageId();

    MonitoredDeployInternalStageData stageData =
        stage.mapTo(MonitoredDeployInternalStageData.class);
    newServerGroup = stageData.getNewServerGroup();
    oldServerGroup = stageData.getOldServerGroup();
    account = stageData.getAccount();
    region = stageData.getRegion();
    cloudProvider = stageData.getCloudProvider();
    parameters = stageData.getDeploymentMonitor().getParameters();
  }
}
