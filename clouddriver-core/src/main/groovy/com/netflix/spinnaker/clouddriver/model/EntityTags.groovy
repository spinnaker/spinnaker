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
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper

@JsonIgnoreProperties(ignoreUnknown = true)
class EntityTags {
  /**
   * Unique identifier for a collection of EntityTag
   */
  String id

  /**
   * The string replacement pattern used to generate the id.
   *
   * Example: "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"
   */
  String idPattern

  Long lastModified
  String lastModifiedBy

  Collection<EntityTag> tags = []
  Collection<EntityTagMetadata> tagsMetadata = []
  EntityRef entityRef

  void setTags(Collection<EntityTag> tags) {
    Objects.requireNonNull(tags)

    // tag collection must be mutable (see putEntityTagIfAbsent())
    this.tags = new ArrayList<>(tags)
  }

  @JsonIgnore
  void putEntityTagMetadata(EntityTagMetadata updatedEntityTagMetadata) {
    def existingTagsMetadata = tagsMetadata.find { it.name.equalsIgnoreCase(updatedEntityTagMetadata.name) }
    if (existingTagsMetadata) {
      existingTagsMetadata.lastModified = updatedEntityTagMetadata.lastModified
      existingTagsMetadata.lastModifiedBy = updatedEntityTagMetadata.lastModifiedBy
    } else {
      tagsMetadata.add(updatedEntityTagMetadata)
    }
  }

  @JsonIgnore
  void putEntityTagIfAbsent(EntityTag entityTag) {
    if (!tags.find { it.name.equalsIgnoreCase(entityTag.name) }) {
      tags.add(entityTag)
    }
  }

  @JsonIgnore
  void removeEntityTagMetadata(String name) {
    tagsMetadata = tagsMetadata.findAll { !it.name.equalsIgnoreCase(name) }
  }

  @JsonIgnore
  void removeEntityTag(String name) {
    tags = tags.findAll { !it.name.equalsIgnoreCase(name) }
    removeEntityTagMetadata(name)
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class EntityRef {
    private Map<String, Object> attributes = new HashMap<String, Object>()

    String cloudProvider
    String application
    String accountId
    String account
    String region

    String entityType
    String entityId

    @JsonAnyGetter
    Map<String, Object> attributes() {
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

  static class EntityTagMetadata {
    String name
    Long lastModified
    String lastModifiedBy
    Long created
    String createdBy

    String getName() {
      return name.toLowerCase()
    }
  }

  static class EntityTag {
    String name

    /**
     * Scoping of the tag, allowing reuse of the same entity tag name across different partitions.
     *
     * This can also be used to restrict who has access to modify a particular groups of tags
     */
    String namespace

    /**
     * An additional, optional, grouping mechanism separate from namespace.
     */
    String category

    Object value
    EntityTagValueType valueType

    @JsonIgnore
    Long timestamp

    String getName() {
      return name.toLowerCase()
    }

    String getNamespace() {
      return (namespace ?: "default").toLowerCase()
    }

    @JsonIgnore
    Object getValueForWrite(ObjectMapper objectMapper) {
      switch (valueType) {
        case EntityTagValueType.object:
          return objectMapper.writeValueAsString(value)
        default:
          return value
      }
    }

    @JsonIgnore
    Object getValueForRead(ObjectMapper objectMapper) {
      if (!(value instanceof String)) {
        return value;
      }

      switch (valueType) {
        case EntityTagValueType.object:
          try {
            return objectMapper.readValue(value.toString(), Map.class)
          } catch (Exception e) {
            return value
          }
        default:
          return value
      }
    }
  }

  static enum EntityTagValueType {
    literal, // number or string
    object   // map
  }
}
