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

import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.ParsedSecretReference;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretReference;
import com.netflix.spinnaker.kork.secrets.SecretReferenceParser;
import com.netflix.spinnaker.kork.secrets.SecretUriReferenceParser;
import com.netflix.spinnaker.kork.secrets.SecretUriType;
import java.net.URI;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;

/**
 * Parses a URI reference to a {@link UserSecret}. These URIs use the scheme {@code secret://}
 * followed by the {@linkplain SecretEngine#identifier() secret engine identifier}, a question mark,
 * and parameter key/value pairs separated by ampersands (i.e., parameters are provided as the query
 * string of this URI). The engine identifier and the query string may be encoded using URL-escaping
 * for otherwise illegal characters in a URI (see {@link URI} for more details). For example, with
 * an engine identifier of {@code foo} with parameters {@code s=my-secret}, {@code r=us-north-1},
 * and {@code k=my-key}, the corresponding user secret URI would be {@code
 * secret://foo?s=my-secret&r=us-north-1&k=my-key}.
 *
 * <h2>Encoding Parameter</h2>
 *
 * <p>User secrets may be encoded in JSON, YAML, or CBOR, and the specific encoding format being
 * used by a user secret should be specified through the corresponding {@link UserSecretMetadata} of
 * that secret along. Additional encoding formats and special types may be configured by registering
 * additional {@link UserSecretSerde} beans.
 *
 * <h2>Key Parameter</h2>
 *
 * <p>User secrets may contain more than one secret value. The {@code k} parameter may be specified
 * to select one of the secrets by name which will return a projected version of the secret with the
 * selected key. <em>Only one {@code k} parameter should be specified in a secret URI.</em>
 *
 * @see UserSecret
 */
@Value
@RequiredArgsConstructor
public class UserSecretReference implements SecretReference {
  public static final String SECRET_SCHEME = "secret";
  private static final SecretReferenceParser PARSER =
      new SecretUriReferenceParser(SECRET_SCHEME + "://", "&", "=", SecretUriType.HIERARCHICAL);

  @Delegate ParsedSecretReference reference;

  /**
   * Parses a user secret URI. Invalid URIs will throw an InvalidSecretFormatException.
   *
   * @param input URI data to parse
   * @return the parsed UserSecretReference
   * @throws InvalidUserSecretReferenceException when the URI is invalid
   */
  public static UserSecretReference parse(String input) {
    try {
      return new UserSecretReference(PARSER.parse(input));
    } catch (InvalidSecretFormatException e) {
      throw new InvalidUserSecretReferenceException(input, e);
    }
  }

  /**
   * Tries to parse a user secret URI into a UserSecretReference. Invalid secret URIs return an
   * empty value.
   */
  public static Optional<UserSecretReference> tryParse(@Nullable Object value) {
    if (!(value instanceof String && isUserSecret((String) value))) {
      return Optional.empty();
    }
    try {
      return Optional.of(new UserSecretReference(PARSER.parse((String) value)));
    } catch (InvalidSecretFormatException e) {
      return Optional.empty();
    }
  }

  /** Checks if the provided input string looks like a user secret URI. */
  public static boolean isUserSecret(String input) {
    return PARSER.matches(input);
  }
}
