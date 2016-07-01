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

package com.netflix.spinnaker.fiat.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import lombok.Data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Data
public class UserPermission {
  private String id;
  private Set<Account> accounts = new HashSet<>();
  private Set<Application> applications = new HashSet<>();

  @JsonIgnore
  public View getView() {
    return new View();
  }

  @Data
  public class View {
    String name = UserPermission.this.id;
    Map<String, Object> resources = ImmutableMap.of(
        "accounts",
        UserPermission.this.accounts
            .stream()
            .map(Account::getView)
            .collect(Collectors.toSet()),
        "applications",
        UserPermission.this.applications
            .stream()
            .map(Application::getView)
            .collect(Collectors.toSet()));
  }
}
