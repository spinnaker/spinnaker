/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.cache.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.netflix.spinnaker.clouddriver.model.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class LambdaFunction implements Function {
  private String cloudProvider;
  private String account;
  private String region;

  @JsonAnySetter private Map<String, Object> attributes = new HashMap<>();

  @JsonAnyGetter
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  public String getFunctionName() {
    return (String) attributes.get("functionName");
  }

  public void setFunctionName(String functionName) {
    attributes.put("functionName", functionName);
  }

  public String getFunctionArn() {
    return (String) attributes.get("functionArn");
  }

  public void setFunctionArn(String functionArn) {
    attributes.put("functionArn", functionArn);
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> getEventSourceMappings() {
    return (List<Map<String, Object>>) attributes.get("eventSourceMappings");
  }

  public void setEventSourceMappings(List<Map<String, Object>> eventSourceMappings) {
    attributes.put("eventSourceMappings", eventSourceMappings);
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> getAliasConfigurations() {
    return (List<Map<String, Object>>) attributes.get("aliasConfigurations");
  }

  public void setAliasConfigurations(List<Map<String, Object>> aliasConfigurations) {
    attributes.put("aliasConfigurations", aliasConfigurations);
  }

  @SuppressWarnings("unchecked")
  public List<String> getTargetGroups() {
    return (List<String>) attributes.get("targetGroups");
  }

  public void setTargetGroups(List<String> targetGroups) {
    attributes.put("targetGroups", targetGroups);
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> getRevisions() {
    return (Map<String, String>) attributes.get("revisions");
  }

  public void setRevisions(Map<String, String> revisions) {
    attributes.put("revisions", revisions);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getCode() {
    return (Map<String, Object>) attributes.get("code");
  }

  public void setCode(Map<String, Object> code) {
    attributes.put("code", code);
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> getTags() {
    return (Map<String, String>) attributes.get("tags");
  }

  public void setTags(Map<String, String> tags) {
    attributes.put("tags", tags);
  }
}
