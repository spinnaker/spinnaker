/*
 * Copyright 2022 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.StandardSecretParameter;
import com.netflix.spinnaker.kork.secrets.user.UserSecretManager;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
@NonnullByDefault
public class AccountDefinitionSecretManager {
  @Delegate private final UserSecretManager userSecretManager;
  private final AccountSecurityPolicy policy;

  private final Map<String, Set<UserSecretReference>> refsByAccountName = new ConcurrentHashMap<>();

  /**
   * Gets a user secret string value for the given account. User secret references are tracked
   * through this method to support time-of-use access control checks for accounts after they've
   * been loaded by the system.
   *
   * @param reference parsed user secret reference to decrypt
   * @param accountName name of account requesting the user secret
   * @return the contents of the requested user secret string
   */
  public String getUserSecretString(UserSecretReference reference, String accountName) {
    var secret = getUserSecret(reference);
    refsByAccountName
        .computeIfAbsent(accountName, ignored -> ConcurrentHashMap.newKeySet())
        .add(reference);
    var parameterName = StandardSecretParameter.KEY.getParameterName();
    var secretKey = reference.getParameters().getOrDefault(parameterName, "");
    try {
      return secret.getSecretString(secretKey);
    } catch (NoSuchElementException e) {
      throw new SecretDecryptionException(e);
    }
  }

  /**
   * Indicates if the given username is authorized to access the given account. When Fiat is
   * enabled, this allows admins to access accounts along with users who have both WRITE permission
   * on the account and are authorized to use any provided {@link UserSecretReference} data.
   *
   * @param username username to check for authorization to use the given account
   * @param accountName the name of the account to check access to
   * @return true if the given username is allowed to access the given account
   */
  public boolean canAccessAccountWithSecrets(String username, String accountName) {
    return policy.isAdmin(username)
        || !accountDefinitionUsesUnauthorizedUserSecrets(username, accountName)
            && policy.canUseAccount(username, accountName);
  }

  /**
   * Checks the account definition for the provided account name and returns true if said account
   * references at least one UserSecret for which the provided user does not have a role in common
   * with. If the account definition uses no UserSecrets or the provided user is allowed to use all
   * the UserSecrets referenced, then this returns false.
   */
  private boolean accountDefinitionUsesUnauthorizedUserSecrets(
      String username, String accountName) {
    var userRoles = policy.getRoles(username);
    // if at least one UserSecret is referenced which the user has no common roles with
    return refsByAccountName.getOrDefault(accountName, Set.of()).stream()
        .map(this::getUserSecret)
        .anyMatch(secret -> Collections.disjoint(secret.getRoles(), userRoles));
    // Collections::disjoint checks if both collections have nothing in common (i.e., an empty set
    // intersection)
  }
}
