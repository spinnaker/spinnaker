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

package com.netflix.spinnaker.config.secrets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class SecretManager {

  final private SecretEngineRegistry secretEngineRegistry;

  @Autowired
  SecretManager(SecretEngineRegistry secretEngineRegistry) {
    this.secretEngineRegistry = secretEngineRegistry;
  }

  /**
   * Decrypt will deserialize the configValue into an EncryptedSecret object and decrypted based on the
   * secretEngine referenced in the configValue.
   *
   * @param configValue
   * @return secret in plaintext
   */
  public String decrypt(String configValue) {
    EncryptedSecret encryptedSecret = EncryptedSecret.parse(configValue);
    if (encryptedSecret == null) {
      return configValue;
    }

    SecretEngine secretEngine = secretEngineRegistry.getEngine(encryptedSecret.getEngineIdentifier());
    if (secretEngine == null) {
      throw new InvalidSecretFormatException("Secret Engine does not exist: " + encryptedSecret.getEngineIdentifier());
    } else {
      secretEngine.validate(encryptedSecret);
      return secretEngine.decrypt(encryptedSecret);
    }
  }

  /**
   * DecryptFile will deserialize the configValue into an EncryptedSecret object, decrypts the EncryptedSecret based
   * on the secretEngine referenced in the configValue, writes the decrypted value into a temporary file, and returns
   * the absolute path to the temporary file.
   *
   * Based on the EncryptedSecret's parameters, the contents of the temporary file can be:
   * - The decrypted contents of a file stored externally
   * OR (if a key is present in the EncryptedSecret's parameters)
   * - The value of the key in the external file
   *
   * Note: The temporary file that is created is deleted upon exiting the application.
   *
   * @param filePathOrEncrypted
   * @return path to temporary file that contains decrypted contents
   */
  public Path decryptFile(String filePathOrEncrypted) {
    if (!EncryptedSecret.isEncryptedSecret(filePathOrEncrypted)) {
      return null;
    }

    EncryptedSecret encryptedSecret = EncryptedSecret.parse(filePathOrEncrypted);
    SecretEngine secretEngine = secretEngineRegistry.getEngine(encryptedSecret.getEngineIdentifier());
    if (secretEngine == null) {
      throw new InvalidSecretFormatException("Secret Engine does not exist: " + encryptedSecret.getEngineIdentifier());
    } else {
      secretEngine.validate(encryptedSecret);
      return decryptedFilePath(secretEngine, encryptedSecret);
    }
  }

  protected Path decryptedFilePath(SecretEngine secretEngine, EncryptedSecret encryptedSecret) {
    String plainText = secretEngine.decrypt(encryptedSecret);
    try {
      File tempFile = File.createTempFile(secretEngine.identifier() + '-', ".secret");
      try (FileWriter fileWriter = new FileWriter(tempFile)) {
        fileWriter.write(plainText);
      }
      tempFile.deleteOnExit();
      return tempFile.toPath();
    } catch (IOException e) {
      throw new SecretDecryptionException(e.getMessage());
    }
  }

  //void setSecretEngineRegistry(SecretEngineRegistry secretEngineRegistry) {
  //  this.secretEngineRegistry = secretEngineRegistry;
  //}

}
