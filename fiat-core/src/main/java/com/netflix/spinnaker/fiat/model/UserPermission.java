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
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Viewable;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
public class UserPermission implements Viewable {
  private String id;
  private Set<Account> accounts = new HashSet<>();
  private Set<Application> applications = new HashSet<>();
  private Set<ServiceAccount> serviceAccounts = new HashSet<>();

  /**
   * True if any of the resources listed above are incomplete for whatever reason - usually because
   * an external provider was down.
   */
  @JsonIgnore
  private boolean isPartialPermission = false;

  @JsonIgnore
  public boolean isEmpty() {
    return accounts.isEmpty() && applications.isEmpty();
  }

  public void addResource(Resource resource) {
    addResources(Collections.singleton(resource));
  }

  public void addResources(Collection<Resource> resources) {
    resources.forEach(resource -> {
      if (resource instanceof Account) {
        accounts.add((Account) resource);
      } else if (resource instanceof Application) {
        applications.add((Application) resource);
      } else if (resource instanceof ServiceAccount) {
        serviceAccounts.add((ServiceAccount) resource);
      } else {
        throw new IllegalArgumentException("Cannot add unknown resource " + resource);
      }
    });
  }

  @JsonIgnore
  public View getView() {
    return new View(this);
  }

  @Data
  @NoArgsConstructor
  @SuppressWarnings("unchecked")
  public static class View extends BaseView implements Resource {
    String name;
    Set<Account.View> accounts;
    Set<Application.View> applications;
    Set<ServiceAccount.View> serviceAccounts;

    public View(UserPermission permission) {
      this.name = permission.id;

      Function<Set<? extends Viewable>, Set<? extends BaseView>> toViews = sourceSet ->
          sourceSet.stream()
                   .map(Viewable::getView)
                   .collect(Collectors.toSet());

      this.accounts = (Set<Account.View>) toViews.apply(permission.getAccounts());
      this.applications = (Set<Application.View>) toViews.apply(permission.getApplications());
      this.serviceAccounts = (Set<ServiceAccount.View>) toViews.apply(permission.getServiceAccounts());
    }
  }
}
