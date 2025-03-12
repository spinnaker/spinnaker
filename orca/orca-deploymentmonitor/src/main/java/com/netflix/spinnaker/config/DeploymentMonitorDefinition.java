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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorService;
import lombok.Data;

@Data
public class DeploymentMonitorDefinition {
  public static final int DEFAULT_MAX_ANALYSIS_MINUTES = 30;

  /** Unique ID of this deployment monitor */
  private String id;

  /** Human friendly name of this deployment monitor (displayed in the UI) */
  private String name;

  /** Base URL for this deployment monitor */
  private String baseUrl;

  /** Contact/support information link (displayed in the UI) */
  private String supportContact;

  /**
   * Maximum number of minutes this deployment monitor is allowed to respond to the /starting &
   * /evaluateHealth request. Failure to respond in this time frame will result in deployment
   * failure.
   */
  private int maxAnalysisMinutes = DEFAULT_MAX_ANALYSIS_MINUTES;

  /**
   * true to terminate the deployment if the deployment monitor repeatedly fails with HTTP errors or
   * fails to respond in the allotted time
   */
  private boolean failOnError = true;

  /**
   * For internal use, indicates if this deployment monitor is considered stable or not Unstable
   * monitors will not show up in the UI, but can still be used in JSON
   */
  private boolean stable = false;

  private DeploymentMonitorService service;

  @Override
  public String toString() {
    return String.format("%s(%s)", name, id);
  }
}
