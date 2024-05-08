/*
 * Copyright 2023 Apple, Inc.
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
 *
 */

package com.netflix.spinnaker.gate.security.saml;

import java.util.Set;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

/** Strategy for extracting and potentially filtering roles from a SAML assertion. */
public interface UserRolesExtractor {
  /** Returns the roles to assign the given principal. */
  Set<String> getRoles(Saml2AuthenticatedPrincipal principal);
}
