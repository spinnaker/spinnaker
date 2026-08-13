/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.orca.front50.multiplepipelines;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class App {
  @JsonProperty("arguments")
  private Map<String, Object> arguments = new HashMap<>();

  @JsonProperty("child_pipeline")
  private String childPipeline;

  @EqualsAndHashCode.Exclude
  @JsonProperty("depends_on")
  private List<String> dependsOn;

  @EqualsAndHashCode.Exclude private String yamlIdentifier;
}
