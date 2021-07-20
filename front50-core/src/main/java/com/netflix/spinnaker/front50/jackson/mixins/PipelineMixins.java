/*
 * Copyright 2021 Armory, Inc.
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
 *
 */
package com.netflix.spinnaker.front50.jackson.mixins;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public abstract class PipelineMixins {
  @JsonAnySetter
  abstract void setAny(String key, Object value);

  @JsonAnyGetter
  abstract Map<String, Object> getAny();

  @JsonIgnore private String lastModified;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Boolean disabled;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String email;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Map<String, Object> template;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private List<String> roles;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String serviceAccount;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String executionEngine;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Integer stageCounter;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private List<Map<String, Object>> stages;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Map<String, Object> constraints;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Map<String, Object> payloadConstraints;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Boolean keepWaitingPipelines;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Boolean limitConcurrent;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private List<Map<String, Object>> parameterConfig;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String spelEvaluator;
}
