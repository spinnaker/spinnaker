/*
 * Copyright 2017 Schibsted ASA.
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

import lombok.NoArgsConstructor;
import lombok.NonNull;

/** Definition of a parameter for the stage that user can provide input for. */
@NoArgsConstructor
public class PreconfiguredStageParameter {

  /** Name of the parameter for which user can provide value */
  @NonNull private String name;

  /** Label to use on the Spinnaker UI. */
  @NonNull private String label;

  /** Default value if not specified by the user. */
  private String defaultValue;

  /** Description to show on the Spinnaker UI. */
  private String description;

  /** Data Type of the parameter. */
  private PreconfiguredJobStageParameter.ParameterType type =
      PreconfiguredJobStageParameter.ParameterType.string;

  /** The order in which this parameter will be displayed on the Spinnaker UI. */
  private int order;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public ParameterType getType() {
    return type;
  }

  public void setType(ParameterType type) {
    this.type = type;
  }

  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public enum ParameterType {
    string
  }
}
