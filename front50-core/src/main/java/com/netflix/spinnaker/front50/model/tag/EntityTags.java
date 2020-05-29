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
package com.netflix.spinnaker.front50.model.tag;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netflix.spinnaker.front50.model.Timestamped;
import java.util.*;

public class EntityTags implements Timestamped {

  private String version = "v1";
  private String id;
  /**
   * pattern used to construct the `id`, ie. {{entityType}}__{{entityId}}__{{account}}__{{region}}
   */
  private String idPattern;

  private Long lastModified;
  private String lastModifiedBy;
  private List<EntityTag> tags = new ArrayList<>();
  private List<EntityTagMetadata> tagsMetadata = new ArrayList<>();
  private EntityRef entityRef;

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getIdPattern() {
    return idPattern;
  }

  public void setIdPattern(String idPattern) {
    this.idPattern = idPattern;
  }

  public Long getLastModified() {
    return lastModified;
  }

  public void setLastModified(Long lastModified) {
    this.lastModified = lastModified;
  }

  public String getLastModifiedBy() {
    return lastModifiedBy;
  }

  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  public List<EntityTag> getTags() {
    return tags;
  }

  public void setTags(List<EntityTag> tags) {
    this.tags = tags;
  }

  public List<EntityTagMetadata> getTagsMetadata() {
    return tagsMetadata;
  }

  public void setTagsMetadata(List<EntityTagMetadata> tagsMetadata) {
    this.tagsMetadata = tagsMetadata;
  }

  public EntityRef getEntityRef() {
    return entityRef;
  }

  public void setEntityRef(EntityRef entityRef) {
    this.entityRef = entityRef;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class EntityRef {

    private Map<String, Object> attributes = new HashMap<>();
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
      return entityType;
    }

    public void setEntityType(String entityType) {
      this.entityType = entityType;
    }

    public String getEntityId() {
      return entityId;
    }

    public void setEntityId(String entityId) {
      this.entityId = entityId;
    }
  }

  public static class EntityTagMetadata {

    private String name;
    private Long lastModified;
    private String lastModifiedBy;
    private Long created;
    private String createdBy;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Long getLastModified() {
      return lastModified;
    }

    public void setLastModified(Long lastModified) {
      this.lastModified = lastModified;
    }

    public String getLastModifiedBy() {
      return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
      this.lastModifiedBy = lastModifiedBy;
    }

    public Long getCreated() {
      return created;
    }

    public void setCreated(Long created) {
      this.created = created;
    }

    public String getCreatedBy() {
      return createdBy;
    }

    public void setCreatedBy(String createdBy) {
      this.createdBy = createdBy;
    }
  }

  public static class EntityTag {

    private String name;
    private String namespace;
    private String category;
    private Object value;
    private String valueType;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getNamespace() {
      return namespace;
    }

    public void setNamespace(String namespace) {
      this.namespace = namespace;
    }

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public Object getValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public String getValueType() {
      return valueType;
    }

    public void setValueType(String valueType) {
      this.valueType = valueType;
    }
  }
}
