/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.igor.build.model.GenericParameterDefinition;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.simpleframework.xml.Default;

@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
  @JsonProperty("global_env")
  private List<Object> globalEnv;

  @JsonProperty("merge_mode")
  private String mergeMode = "deep_merge";
  // This is an object because we inject it like env: matrix: "values", but we get env: "values"
  // back from the api:
  private Object env;

  public Config() {}

  public Config(Map<String, String> environmentMap) {
    if (environmentMap == null || environmentMap.isEmpty()) {
      // if there is no environment map settings, just skip it.
      return;
    }

    String matrixEnvironment =
        environmentMap.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(" "));

    LinkedHashMap<String, String> tmpEnv = new LinkedHashMap<>(1);
    tmpEnv.put("matrix", matrixEnvironment);
    env = tmpEnv;
  }

  public List<GenericParameterDefinition> getParameterDefinitionList() {
    if (globalEnv == null) {
      return Collections.emptyList();
    }
    return globalEnv.stream()
        .filter(env -> env instanceof String)
        .map(env -> ((String) env).split("="))
        .map(Arrays::asList)
        .map(
            parts ->
                new GenericParameterDefinition(
                    parts.get(0),
                    parts.subList(1, parts.size()).stream().collect(Collectors.joining("="))))
        .collect(Collectors.toList());
  }

  public List<Object> getGlobalEnv() {
    return globalEnv;
  }

  public void setGlobalEnv(List<Object> globalEnv) {
    this.globalEnv = globalEnv;
  }

  public String getMergeMode() {
    return mergeMode;
  }

  public void setMergeMode(String mergeMode) {
    this.mergeMode = mergeMode;
  }

  public Object getEnv() {
    return env;
  }

  public void setEnv(Object env) {
    this.env = env;
  }
}
