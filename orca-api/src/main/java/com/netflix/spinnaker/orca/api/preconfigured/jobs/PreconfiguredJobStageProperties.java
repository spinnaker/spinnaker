/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.api.preconfigured.jobs;

import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Holds configurable properties for a PreconfiguredJobStage. */
@Data
@NoArgsConstructor
public abstract class PreconfiguredJobStageProperties {

  /** If enabled, a pipeline can be configured to use this configuration for running a job. */
  private boolean enabled = true;

  /** Label to use on the Spinnaker UI while configuring pipeline stages. */
  private String label;

  /** Description to use on the Spinnaker UI while configuring pipeline stages. */
  private String description;

  /** Represents stage type. */
  private String type;

  /** List of stage parameters that a user can set values on. */
  private List<PreconfiguredJobStageParameter> parameters;

  /**
   * Indicates pipeline execution to wait for job completion if set to true otherwise pipeline
   * proceeds to next stage.
   */
  private boolean waitForCompletion = true;

  /** Cloud provider with which this job interacts. */
  private String cloudProvider;

  /** Credentials to use while interacting with Cloud provider. */
  private String credentials;

  /** Region in which the cloud resources will be launched. */
  private String region;

  private String propertyFile;

  public enum PreconfiguredJobUIType {
    BASIC,
    CUSTOM
  };

  /** "BASIC" to auto-generate a UI. "CUSTOM" if a custom UI is provided for Deck */
  private PreconfiguredJobUIType uiType = PreconfiguredJobUIType.BASIC;

  /** Indicates whether this job produces any artifacts. */
  private boolean producesArtifacts = false;

  PreconfiguredJobStageProperties(String label, String type, String cloudProvider) {
    this.label = label;
    this.type = type;
    this.cloudProvider = cloudProvider;
  }

  /**
   * Indicates what fields can be overridden with configured values if not present in the pipeline
   * stage context
   *
   * @return list of attributes that are overridable.
   */
  public List<String> getOverridableFields() {
    return Arrays.asList(
        "cloudProvider", "credentials", "region", "propertyFile", "waitForCompletion");
  }

  /**
   * If returned false, the stage will not be available for execution.
   *
   * @return true if meets validation criteria otherwise false.
   */
  public boolean isValid() {
    return this.label != null
        && !this.label.isEmpty()
        && this.cloudProvider != null
        && !this.cloudProvider.isEmpty()
        && this.type != null
        && !this.type.isEmpty();
  }
}
