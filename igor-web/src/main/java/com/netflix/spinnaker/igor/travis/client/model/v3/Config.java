/*
 * Copyright 2019 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.netflix.spinnaker.igor.build.model.GenericParameterDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.Default;

@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class Config {
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
                    parts.get(0), String.join("=", parts.subList(1, parts.size()))))
        .collect(Collectors.toList());
  }

  @JsonGetter("global_env")
  public List<Object> getGlobalEnv() {
    return globalEnv;
  }

  @JsonSetter("global_env")
  @SuppressWarnings("unchecked")
  public void setGlobalEnv(Object globalEnv) {
    if (globalEnv instanceof String) {
      // Matches space separated KEY=VALUE pairs. See ConfigSpec for matching examples
      Pattern pattern = Pattern.compile("(\\S*?)=(?>(?>[\"'])(.*?)(?>[\"'])|(\\S*))");
      Matcher matcher = pattern.matcher((String) globalEnv);
      List<String> env = new ArrayList<>();
      while (matcher.find()) {
        String key = matcher.group(1);
        String value = matcher.group(2);
        if (StringUtils.isBlank(value)) {
          value = matcher.group(3);
        }
        value = StringUtils.trimToEmpty(value);
        env.add(key + "=" + value);
      }
      this.globalEnv = new ArrayList<>(env);
    } else if (globalEnv instanceof List) {
      this.globalEnv = (List) globalEnv;
    } else {
      log.warn("Unknown type for 'global_env', ignoring: {}", globalEnv);
    }
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
