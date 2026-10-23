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

import com.netflix.spinnaker.fiat.model.SpinnakerAuthorities;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.kork.common.Header;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

/**
 * Converts an {@code X-SPINNAKER-USER} HTTP header into an Authentication object containing a list
 * of roles and other {@linkplain SpinnakerAuthorities granted authorities} in its granted
 * authorities.
 */
@RequiredArgsConstructor
public class FiatAuthenticationConverter implements AuthenticationConverter {
  private final FiatPermissionEvaluator permissionEvaluator;

  @Override
  public Authentication convert(HttpServletRequest request) {
    String user = request.getHeader(Header.USER.getHeader());
    if (user != null) {
      UserPermission.View permission = permissionEvaluator.getPermission(user);
      if (permission != null) {
        return new PreAuthenticatedAuthenticationToken(
            user, "N/A", permission.toGrantedAuthorities());
      }
    }
    return new AnonymousAuthenticationToken(
        "anonymous", "anonymous", List.of(SpinnakerAuthorities.ANONYMOUS_AUTHORITY));
  }
}
