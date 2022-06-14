/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Set;

/** Provides account authorization checks and related security abstractions on Fiat when enabled. */
@NonnullByDefault
public interface AccountSecurityPolicy {

  /** Indicates if the provided user is an admin. */
  boolean isAdmin(String username);

  /** Indicates if the provided user is an account manager. */
  boolean isAccountManager(String username);

  /** Returns the set of roles assigned to the provided user. */
  Set<String> getRoles(String username);

  /** Indicates if the provided user can use the provided account. */
  boolean canUseAccount(String username, String account);

  /** Indicates if the provided user can modify the provided account. */
  boolean canModifyAccount(String username, String account);
}
