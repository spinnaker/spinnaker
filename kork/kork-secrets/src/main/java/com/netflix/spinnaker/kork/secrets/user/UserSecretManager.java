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

import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.InvalidSecretFormatException;
import com.netflix.spinnaker.kork.secrets.SecretDecryptionException;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretEngineRegistry;
import com.netflix.spinnaker.kork.secrets.UnsupportedSecretEngineException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Central component for obtaining user secrets by reference.
 *
 * @see UserSecretReference
 * @see UserSecret
 */
@Component
@RequiredArgsConstructor
public class UserSecretManager {
  private final SecretEngineRegistry registry;

  /**
   * Fetches and decrypts the given parsed user secret reference.
   *
   * @param reference parsed user secret reference to fetch
   * @return the decrypted user secret
   * @throws UnsupportedSecretEngineException if the secret reference does not have a corresponding
   *     secret engine
   * @throws UnsupportedUserSecretEngineException if the secret engine does not support user secrets
   * @throws MissingUserSecretMetadataException if the secret is missing its {@link
   *     UserSecretMetadata}
   * @throws InvalidUserSecretMetadataException if the secret has metadata that cannot be parsed
   * @throws InvalidSecretFormatException if the secret reference has other validation errors
   * @throws SecretDecryptionException if the secret reference cannot be fetched
   */
  public UserSecret getUserSecret(UserSecretReference reference) {
    String engineIdentifier = reference.getEngineIdentifier();
    SecretEngine engine = registry.getEngine(engineIdentifier);
    if (engine == null) {
      throw new UnsupportedSecretEngineException(engineIdentifier);
    }
    engine.validate(reference);
    try {
      return engine.decrypt(reference);
    } catch (UnsupportedOperationException e) {
      throw new UnsupportedSecretEngineException(engineIdentifier);
    }
  }

  /**
   * Fetches and decrypts the given parsed external secret reference encoded as bytes. External
   * secrets are secrets available through {@link EncryptedSecret} URIs.
   *
   * @param reference parsed external secret reference to fetch
   * @return the decrypted external secret
   * @throws SecretDecryptionException if the external secret does not have a corresponding secret
   *     engine or cannot be fetched
   * @throws InvalidSecretFormatException if the external secret reference is invalid
   */
  public byte[] getExternalSecret(EncryptedSecret reference) {
    String engineIdentifier = reference.getEngineIdentifier();
    SecretEngine engine = registry.getEngine(engineIdentifier);
    if (engine == null) {
      throw new UnsupportedSecretEngineException(engineIdentifier);
    }
    engine.validate(reference);
    return engine.decrypt(reference);
  }

  /**
   * Fetches and decrypts the given parsed external secret reference encoded as a string. External
   * secrets are secrets available through {@link EncryptedSecret} URIs.
   *
   * @param reference parsed external secret reference to fetch
   * @return the decrypted external secret string
   * @throws SecretDecryptionException if the external secret does not have a corresponding secret
   *     engine or cannot be fetched
   * @throws InvalidSecretFormatException if the external secret reference is invalid
   */
  public String getExternalSecretString(EncryptedSecret reference) {
    return new String(getExternalSecret(reference), StandardCharsets.UTF_8);
  }
}
