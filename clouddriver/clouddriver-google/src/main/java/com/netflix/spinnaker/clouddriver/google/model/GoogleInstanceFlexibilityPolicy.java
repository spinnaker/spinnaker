/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Instance flexibility policy for specifying multiple machine types with ranked preferences in a
 * MIG. When a preferred machine type is unavailable, the MIG automatically selects another
 * compatible type.
 *
 * @see <a href="https://cloud.google.com/compute/docs/instance-groups/about-instance-flexibility">
 *     GCP Instance Flexibility Documentation</a>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleInstanceFlexibilityPolicy {

  /** Map of selection name to InstanceSelection, defining ranked machine type preferences. */
  Map<String, InstanceSelection> instanceSelections;

  /** A single instance selection entry with a rank and list of acceptable machine types. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InstanceSelection {
    /** Rank of this selection group (lower rank = higher preference). */
    Integer rank;

    /** Machine types acceptable for this selection group. */
    List<String> machineTypes;
  }
}
