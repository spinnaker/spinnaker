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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Provides an Authentication for an HTTP request using the current {@link
 * AuthenticatedRequest#getSpinnakerUser()}.
 *
 * @see AuthenticatedRequest
 */
public class AuthenticatedRequestAuthenticationConverter implements AuthenticationConverter {
  @Override
  public Authentication convert(HttpServletRequest request) {
    return AuthenticatedRequest.getSpinnakerUser()
        .map(
            user ->
                (Authentication) new PreAuthenticatedAuthenticationToken(user, "N/A", List.of()))
        .orElseGet(
            () ->
                new AnonymousAuthenticationToken(
                    "anonymous",
                    "anonymous",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
  }
}
