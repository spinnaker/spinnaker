/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.roles;

import com.netflix.spinnaker.security.authz.Role;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolves the roles a user belongs to from an external identity source (LDAP, GitHub teams, Google
 * groups, a file, ...) so Gate login and the SA/run-as minter can resolve roles at request time.
 */
public interface UserRolesProvider {

  default List<Role> loadUnrestrictedRoles() {
    return new ArrayList<>();
  }

  /** Load the roles assigned to the given {@link ExternalUser}. */
  List<Role> loadRoles(ExternalUser user);

  /** Load the roles assigned to each {@link ExternalUser}, keyed by user id. */
  Map<String, Collection<Role>> multiLoadRoles(Collection<ExternalUser> users);
}
