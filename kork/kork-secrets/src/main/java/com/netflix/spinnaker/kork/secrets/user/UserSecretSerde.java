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

import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretException;

public interface UserSecretSerde {

  /**
   * Checks if this serde supports user secrets with the given metadata.
   *
   * @param metadata the user secret metadata to check for support
   * @return true if this serde can serialize and deserialize user secrets with the given metadata
   */
  boolean supports(UserSecretMetadata metadata);

  /**
   * Deserializes a raw user secret payload with its parsed metadata.
   *
   * @param encoded the raw user secret data
   * @param metadata the parsed user secret metadata corresponding to the given raw secret
   * @return the parsed user secret
   * @throws SecretDecryptionException if the user secret data cannot be parsed as configured by the
   *     metadata
   */
  UserSecret deserialize(byte[] encoded, UserSecretMetadata metadata);

  /**
   * Serializes a raw user secret to the specified encoding in the given metadata.
   *
   * @param secret the user secret data
   * @param metadata the metadata describing the user secret
   * @return the serialized user secret
   * @throws SecretException if the user secret cannot be serialized
   */
  byte[] serialize(UserSecretData secret, UserSecretMetadata metadata);
}
