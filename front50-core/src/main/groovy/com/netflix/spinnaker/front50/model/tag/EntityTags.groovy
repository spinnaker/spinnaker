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


package com.netflix.spinnaker.front50.model.tag

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.spinnaker.front50.model.Timestamped

class EntityTags implements Timestamped {
  String version = "v1"
  String id
  String idPattern   // pattern used to construct the `id`, ie. {{entityType}}__{{entityId}}__{{account}}__{{region}}

  Long lastModified
  String lastModifiedBy

  Collection<EntityTag> tags = []
  Collection<EntityTagMetadata> tagsMetadata = []
  EntityRef entityRef

  @JsonInclude(JsonInclude.Include.NON_NULL)
  static class EntityRef {
    private Map<String, Object> attributes = new HashMap<String, Object>()

    String entityType
    String entityId

    @JsonAnyGetter
     Map<String,Object> attributes() {
      return attributes
    }

    @JsonAnySetter
    void set(String name, Object value) {
      attributes.put(name, value)
    }
  }

  static class EntityTagMetadata {
    String name
    Long lastModified
    String lastModifiedBy
    Long created
    String createdBy
  }

  static class EntityTag {
    String name
    String namespace
    String category
    Object value
    String valueType
  }
}
