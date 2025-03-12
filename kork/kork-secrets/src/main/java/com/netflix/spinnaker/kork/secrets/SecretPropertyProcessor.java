/*
 * Copyright 2023 Apple Inc.
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

package com.netflix.spinnaker.kork.secrets;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Handles replacing property values with {@link EncryptedSecret} URIs with fetched secrets. */
@Component
@RequiredArgsConstructor
public class SecretPropertyProcessor {
  // special support for some SAML-related properties in Gate
  private static final List<String> SAML_FILE_PROPERTY_NAME_ENDINGS =
      List.of("keystore", "metadataurl");
  // special support for properties expecting filenames
  private static final List<String> FILE_PROPERTY_NAME_ENDINGS =
      List.of("file", "path", "truststore");

  @Setter(onMethod_ = {@Autowired})
  private SecretManager secretManager;

  /**
   * Examines the given property name and value for use of encrypted secrets, returning either the
   * unchanged property value or the decrypted property value when encountering {@link
   * EncryptedSecret} URIs.
   */
  @Nullable
  public Object processPropertyValue(@Nonnull String name, @Nullable Object value) {
    if (!(value instanceof String)) {
      return value;
    }

    String string = (String) value;
    if (!EncryptedSecret.isEncryptedSecret(string)) {
      return string;
    }

    if (secretManager == null) {
      throw new SecretException("No secret manager to decrypt value of " + name);
    }

    if (isSamlFilePropertyName(name)) {
      Path file = secretManager.decryptAsFile(string);
      return "file:" + file;
    }

    if (isFilePropertyName(name) || EncryptedSecret.isEncryptedFile(string)) {
      Path file = secretManager.decryptAsFile(string);
      return file.toString();
    }

    return secretManager.decrypt(string);
  }

  private static boolean isSamlFilePropertyName(String name) {
    return SAML_FILE_PROPERTY_NAME_ENDINGS.stream()
        .anyMatch(suffix -> StringUtils.endsWithIgnoreCase(name, suffix));
  }

  private static boolean isFilePropertyName(String name) {
    return FILE_PROPERTY_NAME_ENDINGS.stream()
        .anyMatch(suffix -> StringUtils.endsWithIgnoreCase(name, suffix));
  }
}
