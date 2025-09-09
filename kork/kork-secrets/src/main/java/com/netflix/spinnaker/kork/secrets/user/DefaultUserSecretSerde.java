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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.jackson.UserFriendlyErrorHandler;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.core.NestedExceptionUtils;

/**
 * Maps structured user secret data types to a corresponding {@link UserSecretData} instance using
 * different encoding formats. Encoding formats are specified by an {@link ObjectMapper}'s {@link
 * JsonFactory#getFormatName()} use case-insensitive string comparison. The type of user secret
 * being encoded is provided by metadata and must correspond to a UserSecretData class annotated
 * with {@link UserSecretType}.
 *
 * @see UserSecretData
 * @see UserSecretReference
 * @see UserSecretType
 */
public class DefaultUserSecretSerde implements UserSecretSerde {
  private final Map<String, Class<? extends UserSecretData>> userSecretTypes =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private final Map<String, ObjectMapper> mappersByEncodingFormat =
      new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  public DefaultUserSecretSerde(
      Collection<ObjectMapper> mappers, Collection<Class<? extends UserSecretData>> types) {
    mappers.forEach(
        mapper -> mappersByEncodingFormat.put(mapper.getFactory().getFormatName(), mapper));
    types.forEach(
        type -> userSecretTypes.put(type.getAnnotation(UserSecretType.class).value(), type));
  }

  @Override
  public boolean supports(UserSecretMetadata metadata) {
    var encoding = metadata.getEncoding();
    return encoding != null
        && userSecretTypes.containsKey(metadata.getType())
        && mappersByEncodingFormat.containsKey(encoding);
  }

  @Override
  public UserSecret deserialize(byte[] encoded, UserSecretMetadata metadata) {
    var secretType = metadata.getType();
    var type = userSecretTypes.get(secretType);
    if (type == null) {
      throw new UnsupportedUserSecretTypeException(secretType);
    }
    var encoding = metadata.getEncoding();
    if (encoding == null) {
      throw new UnsupportedUserSecretEncodingException();
    }
    var mapper = mappersByEncodingFormat.get(encoding);
    if (mapper == null) {
      throw new UnsupportedUserSecretEncodingException(encoding);
    }
    UserSecretData data;
    try {
      data = mapper.readValue(encoded, type);
    } catch (IOException e) {
      throw sanitizedSecretDataException(e);
    }
    return UserSecret.builder().metadata(metadata).data(data).build();
  }

  /**
   * Returns a sanitized exception for a secret data parsing error, making sure not to leak the
   * contents of the secret data that originally caused the error. Should be safe to log.
   */
  private static InvalidUserSecretDataException sanitizedSecretDataException(final IOException e) {
    Throwable rootCause = NestedExceptionUtils.getRootCause(e);
    final String suffix;
    if (rootCause instanceof JsonProcessingException) { // includes JsonParseException
      suffix = UserFriendlyErrorHandler.translateJacksonError(rootCause);
    } else {
      suffix = "unknown error encountered while decoding the contents as JSON";
    }
    // do not attach original object `e` to avoid leaking the contents of a secret
    return new InvalidUserSecretDataException(
        "the secret value does not seem to be valid JSON: " + suffix);
  }

  @Override
  public byte[] serialize(UserSecretData secret, UserSecretMetadata metadata) {
    var encoding = metadata.getEncoding();
    if (encoding == null) {
      throw new UnsupportedUserSecretEncodingException();
    }
    var mapper = mappersByEncodingFormat.get(encoding);
    if (mapper == null) {
      throw new UnsupportedUserSecretEncodingException(encoding);
    }
    try {
      return mapper.writeValueAsBytes(secret);
    } catch (IOException e) {
      throw sanitizedSecretDataException(e);
    }
  }
}
