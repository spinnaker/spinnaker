/*
 * Copyright 2019 Netflix, Inc.
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
import io.kubernetes.client.models.V1Job;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class KubernetesPreconfiguredJobProperties extends PreconfiguredJobStageProperties {

  private String account;
  private String application;
  private V1Job manifest;

  public KubernetesPreconfiguredJobProperties() {
    this.setProducesArtifacts(true);
  }

  public List<String> getOverridableFields() {
    List<String> overrideableFields = new ArrayList<>(Arrays.asList("account", "manifest", "application"));
    overrideableFields.addAll(super.getOverridableFields());
    return overrideableFields;
  }

}
