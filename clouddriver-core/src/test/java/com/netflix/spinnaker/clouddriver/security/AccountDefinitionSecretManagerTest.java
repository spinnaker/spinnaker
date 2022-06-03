/*
 * Copyright 2022 Armory.
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

import static org.mockito.ArgumentMatchers.eq;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountDefinitionSecretManagerTest {

  private static final String ANONYMOUS_USER = "anonymous";

  @Test
  void anonymousUser_shouldNotUseRoles() {
    Authentication authentication = Mockito.mock(Authentication.class);
    SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);

    Mockito.when(authentication.getName()).thenReturn(ANONYMOUS_USER);
    SecurityContextHolder.setContext(securityContext);

    AccountDefinitionAuthorizer authorizer = Mockito.mock(AccountDefinitionAuthorizer.class);
    AccountDefinitionSecretManager accountDefinitionSecretManager =
        new AccountDefinitionSecretManager(null, authorizer);

    Mockito.when(authorizer.isAdmin(eq(ANONYMOUS_USER))).thenReturn(false);
    Mockito.when(authorizer.getRoles(eq(ANONYMOUS_USER))).thenReturn(Set.of());

    String account1 = "account1";
    accountDefinitionSecretManager.canAccessAccountWithSecrets(account1);

    Mockito.verify(authorizer).canAccessAccount(eq(ANONYMOUS_USER), eq(account1));
  }
}
