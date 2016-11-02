/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.clouddriver.model

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class EntityTags {
  String id
  String idPattern

  Long lastModified
  String lastModifiedBy

  Map<String, Object> tags = [:]
  EntityRef entityRef

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class EntityRef {
    private Map<String, Object> attributes = new HashMap<String, Object>()

    String cloudProvider
    String entityType
    String entityId

    @JsonAnyGetter
    Map<String,Object> attributes() {
      return attributes;
    }

    @JsonAnySetter
    void set(String name, Object value) {
      attributes.put(name, value);
    }

    String getEntityType() {
      return entityType?.toLowerCase()
    }
  }
}
