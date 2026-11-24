/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.Getter;
import lombok.Setter;

/**
 * Names of the identity-provider attributes that hold a user's email and name. Shared by the
 * mechanisms that map provider attributes onto a user (OAuth2/OIDC, SAML) so they are configured
 * and consumed the same way.
 *
 * <p>Principals carry the mapped values under the standard OIDC claim names ({@link #EMAIL}, {@link
 * #FIRST_NAME}, {@link #LAST_NAME}), so consumers do not need to know the provider's own names.
 */
@Getter
@Setter
public class UserProfileMapping {
  public static final String EMAIL = "email";
  public static final String FIRST_NAME = "given_name";
  public static final String LAST_NAME = "family_name";

  /** Attribute holding the user's email. */
  private String email;

  /** Attribute holding the user's first name. */
  private String firstName;

  /** Attribute holding the user's last name. */
  private String lastName;

  /**
   * Resolves the mapped attributes through {@code lookup} and returns the non-blank ones keyed by
   * the standard claim names.
   */
  public Map<String, Object> standardClaims(Function<String, Object> lookup) {
    Map<String, Object> claims = new LinkedHashMap<>();
    put(claims, EMAIL, email, lookup);
    put(claims, FIRST_NAME, firstName, lookup);
    put(claims, LAST_NAME, lastName, lookup);
    return claims;
  }

  private static void put(
      Map<String, Object> claims, String claim, String attribute, Function<String, Object> lookup) {
    if (attribute == null) {
      return;
    }
    Object value = lookup.apply(attribute);
    if (value instanceof String string && !string.isBlank()) {
      claims.put(claim, string);
    }
  }
}
