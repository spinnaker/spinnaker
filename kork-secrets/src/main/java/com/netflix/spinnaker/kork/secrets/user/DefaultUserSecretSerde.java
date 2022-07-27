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
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretException;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
@NonnullByDefault
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
    return userSecretTypes.containsKey(metadata.getType())
        && mappersByEncodingFormat.containsKey(metadata.getEncoding());
  }

  @Override
  public UserSecret deserialize(byte[] encoded, UserSecretMetadata metadata) {
    var type = Objects.requireNonNull(userSecretTypes.get(metadata.getType()));
    var mapper = Objects.requireNonNull(mappersByEncodingFormat.get(metadata.getEncoding()));
    try {
      return UserSecret.builder().metadata(metadata).data(mapper.readValue(encoded, type)).build();
    } catch (IOException e) {
      throw new SecretDecryptionException(e);
    }
  }

  @Override
  public byte[] serialize(UserSecretData secret, UserSecretMetadata metadata) {
    var mapper = Objects.requireNonNull(mappersByEncodingFormat.get(metadata.getEncoding()));
    try {
      return mapper.writeValueAsBytes(secret);
    } catch (JsonProcessingException e) {
      throw new SecretException(e);
    }
  }
}
