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

package com.netflix.spinnaker.fiat.roles;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.permissions.ExternalUser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface UserRolesProvider {

  default List<Role> loadUnrestrictedRoles() {
    return new ArrayList<>();
  }

  /**
   * Load the roles assigned to the {@link com.netflix.spinnaker.security.User User}.
   *
   * @param user to load roles for
   * @return Roles assigned to the {@link com.netflix.spinnaker.security.User User} with the given
   *     userEmail.
   */
  List<Role> loadRoles(ExternalUser user);

  /**
   * Load the roles assigned to each {@link com.netflix.spinnaker.security.User User's} email in
   * userEmails.
   *
   * @param users to load roles for
   * @return Map whose keys are the {@link com.netflix.spinnaker.security.User User's} email and
   *     values are their assigned roles.
   */
  Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users);
}
