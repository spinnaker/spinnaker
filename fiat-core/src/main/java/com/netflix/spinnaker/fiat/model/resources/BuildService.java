/*
 * Copyright 2019 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.model.resources;

import com.netflix.spinnaker.fiat.model.Authorization;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
public class BuildService implements Resource.AccessControlled, Viewable {

  private final ResourceType resourceType = ResourceType.BUILD_SERVICE;

  private String name;
  private Permissions permissions = Permissions.EMPTY;

  @Override
  public BaseView getView(Set<Role> userRoles, boolean isAdmin) {
    return new View(this, userRoles, isAdmin);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  public static class View extends Viewable.BaseView implements Authorizable {

    private String name;
    Set<Authorization> authorizations;

    public View(BuildService buildService, Set<Role> userRoles, boolean isAdmin) {
      this.name = buildService.name;
      if (isAdmin) {
        this.authorizations = Authorization.ALL;
      } else {
        this.authorizations = buildService.permissions.getAuthorizations(userRoles);
      }
    }
  }
}
