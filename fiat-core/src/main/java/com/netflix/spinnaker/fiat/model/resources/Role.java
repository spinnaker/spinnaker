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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;
import java.util.Set;

@Data
@EqualsAndHashCode(of = "name")
@NoArgsConstructor
public class Role implements Resource, Viewable {

  private final ResourceType resourceType = ResourceType.ROLE;
  private String name;

  public enum Source {
    EXTERNAL,
    FILE,
    GOOGLE_GROUPS,
    GITHUB_TEAMS,
    LDAP
  }

  private Source source;

  public Role(String name) {
    this.setName(name);
  }

  public Role setName(@Nonnull String name) {
    if (name.isEmpty()) {
      throw new IllegalArgumentException("name cannot be empty");
    }
    this.name = name.toLowerCase();
    return this;
  }

  @JsonIgnore
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


