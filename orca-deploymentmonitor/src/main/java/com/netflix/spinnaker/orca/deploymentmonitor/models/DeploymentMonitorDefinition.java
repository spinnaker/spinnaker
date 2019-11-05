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

import lombok.Data;

/** Model class used by capabilities controller to describe available deployment monitors to deck */
@Data
public class DeploymentMonitorDefinition {
  /** Id of the deployment monitor - this is what shows up in JSON */
  private String id;

  /** Friendly name of the deployment monitor - this is what shows up in the UI */
  private String name;

  /**
   * Support contact info for this deployment monitor Deck will show/link to this from the execution
   * view
   */
  private String supportContact;

  /**
   * Indicates if the monitor is considered stable For example, a monitor developer might want to
   * list s version running in test and prod environments. Unstable monitors will not show up in the
   * UI picker
   */
  private boolean isStable;

  public DeploymentMonitorDefinition() {}

  public DeploymentMonitorDefinition(
      com.netflix.spinnaker.config.DeploymentMonitorDefinition definition) {
    id = definition.getId();
    name = definition.getName();
    supportContact = definition.getSupportContact();
    isStable = definition.isStable();
  }
}
