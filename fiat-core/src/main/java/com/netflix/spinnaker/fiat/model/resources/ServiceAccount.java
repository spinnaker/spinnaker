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
import com.netflix.spinnaker.fiat.model.UserPermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.val;

@Data
@EqualsAndHashCode(callSuper = false)
public class ServiceAccount implements Resource, Viewable {
  private final ResourceType resourceType = ResourceType.SERVICE_ACCOUNT;

  private String name;
  private List<String> memberOf = new ArrayList<>();

  public UserPermission toUserPermission() {
    val roles =
        memberOf.stream()
            .map(membership -> new Role(membership).setSource(Role.Source.EXTERNAL))
            .collect(Collectors.toSet());
    return new UserPermission().setId(name).setRoles(roles);
  }

  public ServiceAccount setMemberOf(List<String> membership) {
    if (membership == null) {
      membership = new ArrayList<>();
    }
    memberOf =
        membership.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toList());
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
    private List<String> memberOf;

    public View(ServiceAccount serviceAccount) {
      this.name = serviceAccount.name;
      this.memberOf = serviceAccount.memberOf;
    }
  }
}
