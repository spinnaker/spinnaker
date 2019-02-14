/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.front50.model.pipeline

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.model.Timestamped

class Pipeline extends HashMap<String, Object> implements Timestamped {

  private static ObjectMapper mapper = new ObjectMapper()

  public static final String TYPE_TEMPLATED = "templatedPipeline"

  @JsonIgnore
  String getApplication() {
    return super.get("application")
  }

  @JsonIgnore
  String getName() {
    return super.get("name")
  }

  void setName(String name) {
    super.put("name", name)
  }

  @Override
  @JsonIgnore
  String getId() {
    return super.get("id")
  }

  void setId(String id) {
    super.put("id", id)
  }

  @Override
  @JsonIgnore
  Long getLastModified() {
    def updateTs = super.get("updateTs") as String
    return updateTs ? Long.valueOf(updateTs) : null
  }

  @Override
  void setLastModified(Long lastModified) {
    super.put("updateTs", lastModified.toString())
  }

  @Override
  String getLastModifiedBy() {
    return super.get("lastModifiedBy")
  }

  @Override
  void setLastModifiedBy(String lastModifiedBy) {
    super.put("lastModifiedBy", lastModifiedBy)
  }

  @JsonIgnore
  Object getConfig() {
    return super.get("config")
  }

  @JsonIgnore
  void setConfig(Object config) {
    super.put("config", config)
  }

  @JsonIgnore
  String getType() {
    return super.get("type")
  }

  @JsonIgnore
  void setType(String type) {
    super.put("type", type)
  }

  @JsonIgnore
  Collection<Trigger> getTriggers() {
    return mapper.convertValue(super.getOrDefault("triggers", new ArrayList<>()), Trigger.COLLECTION_TYPE)
  }

  void setTriggers(Collection<Trigger> triggers) {
    this.put("triggers", triggers);
  }

  @JsonIgnore
  String getSchema() {
    return super.get("schema") ?: "1" // NOTE: Denotes templated pipeline config schema version.
  }

  @JsonIgnore
  void setSchema(String schema) {
    super.put("schema", schema)
  }

  @JsonIgnore
  Integer getIndex() {
    return (Integer) super.get("index")
  }

  @JsonIgnore
  void setIndex(Integer index) {
    super.put("index", index)
  }
}
