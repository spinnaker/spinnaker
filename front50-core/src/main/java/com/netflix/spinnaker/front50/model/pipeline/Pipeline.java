/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spinnaker.front50.model.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Pipeline extends HashMap<String, Object> implements Timestamped {

  private static ObjectMapper MAPPER = new ObjectMapper();
  public static final String TYPE_TEMPLATED = "templatedPipeline";

  @JsonIgnore
  public String getApplication() {
    return (String) super.get("application");
  }

  @JsonIgnore
  public String getName() {
    return (String) super.get("name");
  }

  public void setName(String name) {
    super.put("name", name);
  }

  @Override
  @JsonIgnore
  public String getId() {
    return (String) super.get("id");
  }

  public void setId(String id) {
    super.put("id", id);
  }

  @Override
  @JsonIgnore
  public Long getLastModified() {
    String updateTs = (String) super.get("updateTs");
    return Strings.isNullOrEmpty(updateTs) ? null : Long.valueOf(updateTs);
  }

  @Override
  public void setLastModified(Long lastModified) {
    super.put("updateTs", lastModified.toString());
  }

  @Override
  public String getLastModifiedBy() {
    return (String) super.get("lastModifiedBy");
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy);
  }

  @JsonIgnore
  public Object getConfig() {
    return super.get("config");
  }

  @JsonIgnore
  public void setConfig(Object config) {
    super.put("config", config);
  }

  @JsonIgnore
  public String getType() {
    return (String) super.get("type");
  }

  @JsonIgnore
  public void setType(String type) {
    super.put("type", type);
  }

  @JsonIgnore
  public Collection<Trigger> getTriggers() {
    return MAPPER.convertValue(
        super.getOrDefault("triggers", new ArrayList<>()), Trigger.COLLECTION_TYPE);
  }

  public void setTriggers(Collection<Trigger> triggers) {
    this.put("triggers", triggers);
  }

  /**
   * Denotes templated pipeline config schema version.
   *
   * @return
   */
  @JsonIgnore
  public String getSchema() {
    final String get = (String) super.get("schema");
    return Strings.isNullOrEmpty(get) ? "1" : get;
  }

  @JsonIgnore
  public void setSchema(String schema) {
    super.put("schema", schema);
  }

  @JsonIgnore
  public Integer getIndex() {
    return (Integer) super.get("index");
  }

  @JsonIgnore
  public void setIndex(Integer index) {
    super.put("index", index);
  }
}
