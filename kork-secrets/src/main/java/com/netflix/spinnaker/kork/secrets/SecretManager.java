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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SecretManager {

  @Getter private final SecretEngineRegistry secretEngineRegistry;

  @Autowired
  SecretManager(SecretEngineRegistry secretEngineRegistry) {
    this.secretEngineRegistry = secretEngineRegistry;
  }

  /**
   * Decrypt will deserialize the configValue into an EncryptedSecret object and decrypted based on
   * the secretEngine referenced in the configValue.
   *
   * @param configValue The config value to decrypt
   * @return secret in plaintext
   */
  public String decrypt(String configValue) {
    if (EncryptedSecret.isEncryptedSecret(configValue)) {
      return new String(decryptAsBytes(configValue));
    }
    return configValue;
  }

  /**
   * DecryptAsFile deserializes the configValue into an EncryptedSecret object, decrypts the
   * EncryptedSecret based on the secretEngine referenced in the configValue, writes the decrypted
   * value into a temporary file, and returns the absolute path to the temporary file.
   *
   * <p>Based on the EncryptedSecret's parameters, the contents of the temporary file can be: - The
   * decrypted contents of a file stored externally OR (if a key is present in the EncryptedSecret's
   * parameters) - The value of the key in the external file
   *
   * <p>Note: The temporary file that is created is deleted upon exiting the application.
   *
   * @param filePathOrEncrypted A filepath or encrypted key
   * @return path to temporary file that contains decrypted contents or null if param not encrypted
   */
  public Path decryptAsFile(String filePathOrEncrypted) {
    if (!EncryptedSecret.isEncryptedSecret(filePathOrEncrypted)) {
      return Paths.get(filePathOrEncrypted);
    } else {
      return createTempFile("tmp", decryptAsBytes(filePathOrEncrypted));
    }
  }

  public byte[] decryptAsBytes(String encryptedString) {
    EncryptedSecret encryptedSecret = EncryptedSecret.parse(encryptedString);
    if (encryptedSecret == null) {
      return encryptedString.getBytes();
    }

    SecretEngine secretEngine =
        secretEngineRegistry.getEngine(encryptedSecret.getEngineIdentifier());
    if (secretEngine == null) {
      throw new SecretDecryptionException(
          "Secret Engine does not exist: " + encryptedSecret.getEngineIdentifier());
    }

    secretEngine.validate(encryptedSecret);

    return secretEngine.decrypt(encryptedSecret);
  }

  protected Path createTempFile(String prefix, byte[] decryptedContents) {
    try {
      File tempFile = File.createTempFile(prefix, ".secret");
      FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
      BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

      bufferedOutputStream.write(decryptedContents);

      tempFile.deleteOnExit();
      bufferedOutputStream.close();
      fileOutputStream.close();

      return tempFile.toPath();
    } catch (IOException e) {
      throw new SecretDecryptionException(e.getMessage());
    }
  }
}
