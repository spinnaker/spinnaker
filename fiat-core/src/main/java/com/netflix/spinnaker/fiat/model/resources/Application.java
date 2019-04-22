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
import com.google.common.collect.Sets;
import com.netflix.spinnaker.fiat.model.Authorization;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
public class Application extends BaseAccessControlled implements Viewable {
  final ResourceType resourceType = ResourceType.APPLICATION;

  private String name;
  private Permissions permissions = Permissions.EMPTY;
  
  @JsonIgnore
  public View getView(Set<Role> userRoles, boolean isAdmin) {
    return new View(this, userRoles, isAdmin);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  public static class View extends BaseView implements Authorizable {
    String name;
    Set<Authorization> authorizations;

    public View(Application application, Set<Role> userRoles, boolean isAdmin) {
      this.name = application.name;
      if (isAdmin) {
        this.authorizations = Sets.newHashSet(Authorization.READ, Authorization.WRITE, Authorization.EXECUTE);
      } else {
        this.authorizations = application.permissions.getAuthorizations(userRoles);
      }
    }
  }
}
