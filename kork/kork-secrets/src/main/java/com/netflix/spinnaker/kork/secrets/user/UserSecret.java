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

package com.netflix.spinnaker.kork.secrets.user;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.security.AccessControlled;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * User secrets are externally stored secrets with additional user-provided metadata regarding who
 * is authorized to use the contents of the secret along with defining the type of secret stored.
 * User secrets are stored in user-specific secrets managers supported by the {@link SecretEngine}
 * API. Each SecretEngine should use an appropriate metadata API corresponding to its secret storage
 * API to avoid storing metadata directly in secret payloads.
 *
 * @see UserSecretData
 * @see UserSecretMetadata
 * @see UserSecretManager
 */
@NonnullByDefault
@Getter
@Builder
public class UserSecret implements AccessControlled {
  /** Returns the metadata for this secret. */
  @Delegate private final UserSecretMetadata metadata;

  /** Returns the user secret data contained in this secret. */
  @Delegate private final UserSecretData data;

  @Override
  public boolean isAuthorized(Authentication authentication, Object authorization) {
    Set<String> userAuthorities =
        AuthorityUtils.authorityListToSet(authentication.getAuthorities());
    Set<String> permittedAuthorities =
        getRoles().stream().map(role -> "ROLE_" + role).collect(Collectors.toSet());
    return permittedAuthorities.isEmpty()
        || !Collections.disjoint(userAuthorities, permittedAuthorities);
  }
}
