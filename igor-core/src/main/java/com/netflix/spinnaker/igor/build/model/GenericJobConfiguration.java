/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.build.model;

import java.util.List;
import lombok.Data;

@Data
public class GenericJobConfiguration implements JobConfiguration {
  private String description;
  private String displayName;
  private String name;
  private boolean buildable;
  private String url;
  private boolean concurrentBuild;
  private List<GenericParameterDefinition> parameterDefinitionList;

  public GenericJobConfiguration(
      String description,
      String displayName,
      String name,
      boolean buildable,
      String url,
      boolean concurrentBuild,
      List<GenericParameterDefinition> genericParameterDefinition) {
    this.description = description;
    this.displayName = displayName;
    this.name = name;
    this.buildable = buildable;
    this.url = url;
    this.concurrentBuild = concurrentBuild;
    this.parameterDefinitionList = genericParameterDefinition;
  }

  public GenericJobConfiguration(String description, String name) {
    this.description = description;
    this.name = name;
  }
}
