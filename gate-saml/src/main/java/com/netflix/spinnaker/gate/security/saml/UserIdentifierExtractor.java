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

import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

/** Strategy for extracting a userid from an authenticated SAML2 principal. */
public interface UserIdentifierExtractor {
  String fromPrincipal(Saml2AuthenticatedPrincipal principal);
}
