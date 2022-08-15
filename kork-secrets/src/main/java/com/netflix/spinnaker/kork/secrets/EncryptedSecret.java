/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.kork.secrets;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * EncryptedSecrets contain an engineIdentifier and named parameters. EncryptedSecrets can be used
 * by a SecretEngine implementation to get the decrypted secret.
 *
 * <p>Format for Encrypted Secrets:
 *
 * <p>encrypted:[engine-identifier]![param-name_1]:[param-value_1]!..![param-name_n]:[param-value_n]
 *
 * <p>Note: Valid param-names match the regex: `[a-zA-Z0-9]+` Note: secret-params may contain ':'
 * Note: `encrypted` cannot be a param-name Note: There must be at least one
 * [param-name]:[param-value] pair Named parameters are used to allow for adding additional options
 * in the future.
 */
@EqualsAndHashCode
@NoArgsConstructor
@Log4j2
public class EncryptedSecret {

  public static final String ENCRYPTED_STRING_PREFIX = "encrypted:";
  public static final String ENCRYPTED_FILE_PREFIX = "encryptedFile:";
  private static final String ENCRYPTED_TAG_REGEX = ".+(![a-zA-Z0-9]+:.+)+";
  private static final String ENCRYPTED_STRING_REGEX =
      ENCRYPTED_STRING_PREFIX + ENCRYPTED_TAG_REGEX;
  private static final String ENCRYPTED_FILE_REGEX = ENCRYPTED_FILE_PREFIX + ENCRYPTED_TAG_REGEX;

  @Getter @Setter private String engineIdentifier;

  @Getter private Map<String, String> params = new HashMap<>();

  @Getter private boolean encryptedFile = false;

  EncryptedSecret(String secretConfig) {
    this.update(secretConfig);
  }

  /**
   * @param secretConfig Potentially encrypted secret value
   * @return EncryptedSecret object
   */
  public static EncryptedSecret parse(String secretConfig) {
    if (EncryptedSecret.isEncryptedSecret(secretConfig)) {
      return new EncryptedSecret(secretConfig);
    }
    return null;
  }

  /**
   * Tries to parse the provided value as an EncryptedSecret if possible or returns an empty
   * Optional otherwise.
   */
  public static Optional<EncryptedSecret> tryParse(@Nullable Object value) {
    if (!(value instanceof String && isEncryptedSecret((String) value))) {
      return Optional.empty();
    }
    try {
      return Optional.of(new EncryptedSecret((String) value));
    } catch (InvalidSecretFormatException e) {
      log.warn("Tried to parse invalid encrypted secret URI '{}'", value, e);
      return Optional.empty();
    }
  }

  protected void update(String secretConfig) {
    encryptedFile = isEncryptedFile(secretConfig);
    String[] keyValues = secretConfig.split("!");
    if (keyValues.length < 2) {
      throw new InvalidSecretFormatException(
          "Invalid encrypted secret format, must have at least one parameter");
    }
    for (int i = 0; i < keyValues.length; i++) {
      String[] keyV = keyValues[i].split(":", 2);
      if (keyV.length != 2) {
        throw new InvalidSecretFormatException(
            "Invalid encrypted secret format, keys and values must be delimited by ':'");
      }
      if (i == 0) {
        this.engineIdentifier = keyV[1];
      } else {
        this.params.put(keyV[0], keyV[1]);
      }
    }
  }

  /**
   * @param secretConfig Potentially encrypted secret value
   * @return boolean representing whether or not the secretConfig is formatted correctly
   */
  public static boolean isEncryptedSecret(String secretConfig) {
    return secretConfig != null
        && (matchesEncryptedStringSyntax(secretConfig) || matchesEncryptedFileSyntax(secretConfig));
  }

  public static boolean isEncryptedFile(String secretConfig) {
    return secretConfig != null && matchesEncryptedFileSyntax(secretConfig);
  }

  private static boolean matchesEncryptedStringSyntax(String secretConfig) {
    return secretConfig.startsWith(ENCRYPTED_STRING_PREFIX)
        && secretConfig.matches(ENCRYPTED_STRING_REGEX);
  }

  private static boolean matchesEncryptedFileSyntax(String secretConfig) {
    return secretConfig.toLowerCase().startsWith(ENCRYPTED_FILE_PREFIX.toLowerCase())
        && secretConfig.matches(ENCRYPTED_FILE_REGEX);
  }
}
