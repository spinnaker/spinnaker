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
import com.netflix.spinnaker.fiat.model.resources.BuildService;
import com.netflix.spinnaker.fiat.model.resources.Resource;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.model.resources.Viewable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
public class UserPermission {
  private String id;

  private Set<Account> accounts = new LinkedHashSet<>();
  private Set<Application> applications = new LinkedHashSet<>();
  private Set<ServiceAccount> serviceAccounts = new LinkedHashSet<>();
  private Set<Role> roles = new LinkedHashSet<>();
  private Set<BuildService> buildServices = new LinkedHashSet<>();
  private boolean admin = false;

  public void addResource(Resource resource) {
    addResources(Collections.singleton(resource));
  }

  public UserPermission addResources(Collection<Resource> resources) {
    if (resources == null) {
      return this;
    }

    resources.forEach(
        resource -> {
          if (resource instanceof Account) {
            accounts.add((Account) resource);
          } else if (resource instanceof Application) {
            applications.add((Application) resource);
          } else if (resource instanceof ServiceAccount) {
            serviceAccounts.add((ServiceAccount) resource);
          } else if (resource instanceof Role) {
            roles.add((Role) resource);
          } else if (resource instanceof BuildService) {
            buildServices.add((BuildService) resource);
          } else {
            throw new IllegalArgumentException("Cannot add unknown resource " + resource);
          }
        });

    return this;
  }

  @JsonIgnore
  public Set<Resource> getAllResources() {
    Set<Resource> retVal = new HashSet<>();
    retVal.addAll(accounts);
    retVal.addAll(applications);
    retVal.addAll(serviceAccounts);
    retVal.addAll(roles);
    retVal.addAll(buildServices);
    return retVal;
  }

  /**
   * This method adds all of other's resources to this one.
   *
   * @param other
   */
  public UserPermission merge(UserPermission other) {
    this.addResources(other.getAllResources());
    return this;
  }

  @JsonIgnore
  public View getView() {
    return new View(this);
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  @NoArgsConstructor
  @SuppressWarnings("unchecked")
  public static class View extends Viewable.BaseView {
    String name;
    Set<Account.View> accounts;
    Set<Application.View> applications;
    Set<ServiceAccount.View> serviceAccounts;
    Set<Role.View> roles;
    Set<BuildService.View> buildServices;
    boolean admin;
    boolean legacyFallback = false;
    boolean allowAccessToUnknownApplications = false;

    public View(UserPermission permission) {
      this.name = permission.id;

      Function<Set<? extends Viewable>, Set<? extends Viewable.BaseView>> toViews =
          sourceSet ->
              sourceSet.stream()
                  .map(viewable -> viewable.getView(permission.getRoles(), permission.isAdmin()))
                  .collect(Collectors.toSet());

      this.accounts = (Set<Account.View>) toViews.apply(permission.getAccounts());
      this.applications = (Set<Application.View>) toViews.apply(permission.getApplications());
      this.serviceAccounts =
          (Set<ServiceAccount.View>) toViews.apply(permission.getServiceAccounts());
      this.roles = (Set<Role.View>) toViews.apply(permission.getRoles());
      this.buildServices = (Set<BuildService.View>) toViews.apply(permission.getBuildServices());
      this.admin = permission.isAdmin();
    }
  }
}
