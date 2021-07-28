/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.model.resources;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class Role implements Resource, Viewable {

  private String name;
  private int hashCode;

  public enum Source {
    EXTERNAL,
    FILE,
    GOOGLE_GROUPS,
    GITHUB_TEAMS,
    LDAP
  }

  private Source source;

  public Role() {}

  public Role(String name) {
    this.setName(name);
  }

  public ResourceType getResourceType() {
    return ResourceType.ROLE;
  }

  public String getName() {
    return this.name;
  }

  public Role setName(@Nonnull String name) {
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("name cannot be empty");
    }
    this.name = name.toLowerCase();
    this.hashCode = Objects.hash(ResourceType.ROLE, this.name);
    return this;
  }

  public Source getSource() {
    return this.source;
  }

  public Role setSource(Source source) {
    this.source = source;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Role that = (Role) o;
    return Objects.equals(this.name, that.name);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return "Role(resourceType="
        + this.getResourceType()
        + ", name="
        + this.getName()
        + ", source="
        + this.getSource()
        + ")";
  }

  @JsonIgnore
  @Override
  public View getView(Set<Role> ignored, boolean isAdmin) {
    return new View(this);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  public static class View extends BaseView {
    private String name;
    private Source source;

    public View(Role role) {
      this.name = role.name;
      this.source = role.getSource();
    }
  }
}
