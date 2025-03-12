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
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

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
 * selected key.
 *
 * @see UserSecret
 */
@EqualsAndHashCode
@ToString
@Getter
public class UserSecretReference {
  private static final Pattern SECRET_URI = Pattern.compile("^secret(File)?://.+");
  public static final String SECRET_SCHEME = "secret";

  @Nonnull private final String engineIdentifier;
  @Nonnull private final Map<String, String> parameters = new ConcurrentHashMap<>();

  private UserSecretReference(URI uri) {
    if (!SECRET_SCHEME.equals(uri.getScheme())) {
      throw new InvalidSecretFormatException("Only secret:// URIs supported");
    }
    engineIdentifier = uri.getAuthority();
    String[] queryKeyValues = uri.getQuery().split("&");
    if (queryKeyValues.length == 0) {
      throw new InvalidSecretFormatException(
          "Invalid user secret URI has no query parameters defined");
    }
    for (String keyValue : queryKeyValues) {
      String[] pair = keyValue.split("=", 2);
      if (pair.length != 2) {
        throw new InvalidSecretFormatException(
            "Invalid user secret query string; missing parameter value for '" + keyValue + "'");
      }
      parameters.put(pair[0], pair[1]);
    }
  }

  /**
   * Parses a user secret URI. Invalid URIs will throw an InvalidSecretFormatException.
   *
   * @param input URI data to parse
   * @return the parsed UserSecretReference
   * @throws InvalidSecretFormatException when the URI is invalid
   */
  @Nonnull
  public static UserSecretReference parse(@Nonnull String input) {
    try {
      return new UserSecretReference(new URI(input));
    } catch (URISyntaxException e) {
      throw new InvalidSecretFormatException(e);
    }
  }

  /**
   * Tries to parse a user secret URI into a UserSecretReference. Invalid secret URIs return an
   * empty value.
   */
  @Nonnull
  public static Optional<UserSecretReference> tryParse(@Nullable Object value) {
    if (!(value instanceof String && isUserSecret((String) value))) {
      return Optional.empty();
    }
    try {
      return Optional.of(new UserSecretReference(new URI((String) value)));
    } catch (URISyntaxException | InvalidSecretFormatException e) {
      return Optional.empty();
    }
  }

  /** Checks if the provided input string looks like a user secret URI. */
  public static boolean isUserSecret(@Nonnull String input) {
    return SECRET_URI.matcher(input).matches();
  }
}
