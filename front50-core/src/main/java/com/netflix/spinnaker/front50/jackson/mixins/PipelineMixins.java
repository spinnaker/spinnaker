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
import com.netflix.spinnaker.front50.api.model.pipeline.Trigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public abstract class PipelineMixins {
  @JsonAnySetter
  abstract void setAny(String key, Object value);

  @JsonAnyGetter
  abstract Map<String, Object> getAny();

  @JsonInclude(Include.NON_NULL)
  @Setter
  private String id;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String name;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String application;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String type;

  @JsonInclude(Include.NON_NULL)
  @Setter
  private String schema;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Object config;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private List<Trigger> triggers = new ArrayList<>();

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private Integer index;

  @JsonInclude(Include.NON_NULL)
  private String updateTs;

  @JsonInclude(Include.NON_NULL)
  private String createTs;

  @JsonInclude(Include.NON_NULL)
  private String lastModifiedBy;

  @JsonIgnore private String lastModified;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String disabled;

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
  private String keepWaitingPipelines;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String limitConcurrent;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private List<Map<String, Object>> parameterConfig;

  @JsonInclude(Include.NON_NULL)
  @Getter
  @Setter
  private String spelEvaluator;
}
