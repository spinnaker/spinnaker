/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for a Lambda function as returned by clouddriver's cache. Spinnaker-specific fields are
 * explicit Lombok fields. FunctionConfiguration fields (functionName, functionArn, revisionId,
 * state, etc.) come from clouddriver's v2-serialised JSON and are stored in a flat catch-all map so
 * the shape is stable regardless of SDK version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LambdaDefinition {

  private String cloudProvider;
  private String account;
  private String region;

  private Map<String, String> revisions;

  @SuppressWarnings("rawtypes")
  private List<Map<String, Object>> aliasConfigurations;

  @SuppressWarnings("rawtypes")
  private List<Map<String, Object>> eventSourceMappings;

  private Map<String, Object> code;
  private Map<String, String> tags;
  private List<String> targetGroups;

  @Builder.Default private Map<String, Object> attributes = new HashMap<>();

  @JsonAnySetter
  public void setAttribute(String key, Object value) {
    if (attributes == null) {
      attributes = new HashMap<>();
    }
    attributes.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  // ---- FunctionConfiguration fields from clouddriver cache --------------------

  public String getFunctionName() {
    return (String) attributes.get("functionName");
  }

  public String getFunctionArn() {
    return (String) attributes.get("functionArn");
  }

  public String getRevisionId() {
    return (String) attributes.get("revisionId");
  }

  public String getState() {
    return (String) attributes.get("state");
  }

  public String getStateReason() {
    return (String) attributes.get("stateReason");
  }

  public String getStateReasonCode() {
    return (String) attributes.get("stateReasonCode");
  }
}
