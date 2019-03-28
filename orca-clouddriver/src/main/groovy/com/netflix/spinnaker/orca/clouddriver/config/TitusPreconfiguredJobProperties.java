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
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Array;
import java.util.*;

@Data
@EqualsAndHashCode(callSuper = true)
public class TitusPreconfiguredJobProperties extends PreconfiguredJobStageProperties {

  private Map<String, Object> cluster = new HashMap<>();

  public List<String> getOverridableFields() {
    List<String> overrideableFields = new ArrayList<>(Arrays.asList("cluster"));
    overrideableFields.addAll(super.getOverridableFields());
    return overrideableFields;
  }
}
