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

import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class AccessControlledAccountDefinitionAuthorizedRolesExtractor
    implements AuthorizedRolesExtractor {
  @Override
  public boolean supportsType(Class<? extends CredentialsDefinition> type) {
    return AccessControlledAccountDefinition.class.isAssignableFrom(type);
  }

  @Override
  public Set<String> getAuthorizedRoles(CredentialsDefinition definition) {
    return ((AccessControlledAccountDefinition) definition)
        .getPermissions()
        .getOrDefault(Authorization.WRITE, Set.of());
  }
}
