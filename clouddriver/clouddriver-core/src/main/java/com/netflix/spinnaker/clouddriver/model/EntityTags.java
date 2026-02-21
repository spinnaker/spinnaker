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

package com.netflix.spinnaker.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityTags {
  /** Unique identifier for a collection of EntityTag */
  private String id;

  /**
   * The string replacement pattern used to generate the id.
   *
   * <p>Example: "{{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"
   */
  private String idPattern;

  private Long lastModified;
  private String lastModifiedBy;

  private Collection<EntityTag> tags = new ArrayList<>();
  private Collection<EntityTagMetadata> tagsMetadata = new ArrayList<>();
  private EntityRef entityRef;

  public void setTags(Collection<EntityTag> tags) {
    Objects.requireNonNull(tags);

    // tag collection must be mutable (see putEntityTagIfAbsent())
    this.tags = new ArrayList<>(tags);
  }

  @JsonIgnore
  public void putEntityTagMetadata(EntityTagMetadata updatedEntityTagMetadata) {
    EntityTagMetadata existingTagsMetadata =
        tagsMetadata.stream()
            .filter(m -> m.getName().equalsIgnoreCase(updatedEntityTagMetadata.getName()))
            .findFirst()
            .orElse(null);
    if (existingTagsMetadata != null) {
      existingTagsMetadata.setLastModified(updatedEntityTagMetadata.getLastModified());
      existingTagsMetadata.setLastModifiedBy(updatedEntityTagMetadata.getLastModifiedBy());
    } else {
      tagsMetadata.add(updatedEntityTagMetadata);
    }
  }

  @JsonIgnore
  public void putEntityTagIfAbsent(EntityTag entityTag) {
    boolean exists = tags.stream().anyMatch(t -> t.getName().equalsIgnoreCase(entityTag.getName()));
    if (!exists) {
      tags.add(entityTag);
    }
  }

  @JsonIgnore
  public void removeEntityTagMetadata(String name) {
    tagsMetadata =
        tagsMetadata.stream()
            .filter(m -> !m.getName().equalsIgnoreCase(name))
            .collect(Collectors.toCollection(ArrayList::new));
  }

  @JsonIgnore
  public void removeEntityTag(String name) {
    tags =
        tags.stream()
            .filter(t -> !t.getName().equalsIgnoreCase(name))
            .collect(Collectors.toCollection(ArrayList::new));
    removeEntityTagMetadata(name);
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class EntityRef {
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Map<String, Object> attributes = new HashMap<>();

    private String cloudProvider;
    private String application;
    private String accountId;
    private String account;
    private String region;

    private String entityType;
    private String entityId;

    @JsonAnyGetter
    public Map<String, Object> attributes() {
      return attributes;
    }

    @JsonAnySetter
    public void set(String name, Object value) {
      attributes.put(name, value);
    }

    public String getEntityType() {
      return entityType != null ? entityType.toLowerCase() : null;
    }
  }

  @Data
  public static class EntityTagMetadata {
    private String name;
    private Long lastModified;
    private String lastModifiedBy;
    private Long created;
    private String createdBy;

    public String getName() {
      return name.toLowerCase();
    }
  }

  @Data
  public static class EntityTag {
    private String name;

    /**
     * Scoping of the tag, allowing reuse of the same entity tag name across different partitions.
     *
     * <p>This can also be used to restrict who has access to modify a particular groups of tags
     */
    private String namespace;

    /** An additional, optional, grouping mechanism separate from namespace. */
    private String category;

    private Object value;
    private EntityTagValueType valueType;

    @JsonIgnore private Long timestamp;

    public String getName() {
      return name.toLowerCase();
    }

    public String getNamespace() {
      return (namespace != null ? namespace : "default").toLowerCase();
    }

    @JsonIgnore
    public Object getValueForWrite(ObjectMapper objectMapper) {
      switch (valueType) {
        case object:
          try {
            return objectMapper.writeValueAsString(value);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        default:
          return value;
      }
    }

    @JsonIgnore
    public Object getValueForRead(ObjectMapper objectMapper) {
      if (!(value instanceof String)) {
        return value;
      }

      switch (valueType) {
        case object:
          try {
            return objectMapper.readValue(value.toString(), Map.class);
          } catch (Exception e) {
            return value;
          }
        default:
          return value;
      }
    }
  }

  public enum EntityTagValueType {
    literal, // number or string
    object // map
  }
}
