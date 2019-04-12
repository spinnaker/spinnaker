/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config;

import com.google.common.collect.ImmutableList;
import jdk.nashorn.internal.ir.annotations.Immutable;
import lombok.Data;

import java.util.*;

@Data
public abstract class PreconfiguredJobStageProperties {

  private boolean enabled = true;
  private String label;
  private String description;
  private String type;
  private List<PreconfiguredJobStageParameter> parameters;
  private boolean waitForCompletion = true;
  private String cloudProvider;
  private String credentials;
  private String region;
  private String propertyFile;
  private boolean producesArtifacts = false;

  public List<String> getOverridableFields() {
    return Arrays.asList(
      "cloudProvider",
      "credentials",
      "region",
      "propertyFile",
      "waitForCompletion"
    );
  }

}
