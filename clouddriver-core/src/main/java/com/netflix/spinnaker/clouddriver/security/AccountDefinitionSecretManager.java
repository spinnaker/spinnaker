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

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;

@RequiredArgsConstructor
public class AccountDefinitionSecretManager {
  private final SecretManager secretManager;
  private final AccountDefinitionAuthorizer authorizer;

  public String getEncryptedSecret(String encryptedUri) {
    return EncryptedSecret.isEncryptedFile(encryptedUri)
        ? secretManager.decryptAsFile(encryptedUri).toString()
        : secretManager.decrypt(encryptedUri);
  }

  public boolean canAccessAccountWithSecrets(@Nonnull String accountName) {
    var username = SecurityContextHolder.getContext().getAuthentication().getName();
    if (authorizer.isAdmin(username)) {
      return true;
    }

    if (!"anonymous".equals(username) && authorizer.getRoles(username).isEmpty()) {
      return false;
    }

    // TODO(jvz): update with https://github.com/spinnaker/kork/pull/942
    //  to add user secrets usage tracking for time of use authz checks
    return authorizer.canAccessAccount(username, accountName);
  }
}
