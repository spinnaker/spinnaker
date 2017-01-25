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
import com.netflix.spinnaker.fiat.model.Authorization;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class Account implements GroupAccessControlled, Resource, Viewable {
  final ResourceType resourceType = ResourceType.ACCOUNT;

  private String name;
  private String cloudProvider;
  private List<String> requiredGroupMembership = new ArrayList<>();

  public Account setRequiredGroupMembership(List<String> membership) {
    if (membership == null) {
      membership = new ArrayList<>();
    }
    requiredGroupMembership = membership.stream().map(String::toLowerCase).collect(Collectors.toList());
    return this;
  }

  @JsonIgnore
  public View getView() {
    return new View(this);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  public static class View extends BaseView implements Authorizable {
    String name;
    Set<Authorization> authorizations;

    public View(Account account) {
      this.name = account.name;
      this.authorizations = new HashSet<>();
      this.authorizations.add(Authorization.READ);
      this.authorizations.add(Authorization.WRITE);
    }
  }
}
