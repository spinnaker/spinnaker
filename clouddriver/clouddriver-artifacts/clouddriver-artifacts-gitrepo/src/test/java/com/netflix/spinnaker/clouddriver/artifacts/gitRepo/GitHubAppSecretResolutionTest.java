/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.netflix.spinnaker.kork.github.GitHubAppCredentials;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretEngine;
import com.netflix.spinnaker.kork.secrets.SecretEngineRegistry;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import com.netflix.spinnaker.kork.secrets.SecretPropertyProcessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that githubApp.appPrivateKeyPath supports kork-secrets encrypted secret URIs (encrypted:
 * and encryptedFile:), which clouddriver resolves to a decrypted temp file at binding time via
 * {@link SecretPropertyProcessor}.
 */
class GitHubAppSecretResolutionTest {

  private static final String APP_PRIVATE_KEY_PATH_PROPERTY =
      "artifacts.git-repo.accounts[0].githubApp.appPrivateKeyPath";
  private static final String SECRET_URI = "encrypted:test!f:gh-app-key.pem";
  private static final String SECRET_FILE_URI = "encryptedFile:test!f:gh-app-key.pem";

  private byte[] pemContents;
  private SecretPropertyProcessor secretPropertyProcessor;

  @BeforeEach
  void setUp() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    pemContents =
        ("-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                    .encodeToString(keyPairGenerator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n")
            .getBytes(StandardCharsets.UTF_8);

    SecretEngine testEngine =
        new SecretEngine() {
          @Override
          public String identifier() {
            return "test";
          }

          @Override
          public byte[] decrypt(EncryptedSecret encryptedSecret) {
            return pemContents;
          }

          @Override
          public void validate(EncryptedSecret encryptedSecret) {}

          @Override
          public void clearCache() {}
        };

    secretPropertyProcessor = new SecretPropertyProcessor();
    secretPropertyProcessor.setSecretManager(
        new SecretManager(new SecretEngineRegistry(List.of(testEngine))));
  }

  @Test
  @DisplayName("encrypted: URI on appPrivateKeyPath resolves to a decrypted temp file")
  void resolvesEncryptedSecretUriToDecryptedTempFile() throws Exception {
    // The property name ends with "path", so encrypted: values are treated as file references
    Object resolved =
        secretPropertyProcessor.processPropertyValue(APP_PRIVATE_KEY_PATH_PROPERTY, SECRET_URI);

    assertThat(resolved).isInstanceOf(String.class);
    assertThat(Files.readAllBytes(Path.of((String) resolved))).isEqualTo(pemContents);
  }

  @Test
  @DisplayName("encryptedFile: URI on appPrivateKeyPath resolves to a decrypted temp file")
  void resolvesEncryptedFileUriToDecryptedTempFile() throws Exception {
    Object resolved =
        secretPropertyProcessor.processPropertyValue(
            APP_PRIVATE_KEY_PATH_PROPERTY, SECRET_FILE_URI);

    assertThat(resolved).isInstanceOf(String.class);
    assertThat(Files.readAllBytes(Path.of((String) resolved))).isEqualTo(pemContents);
  }

  @Test
  @DisplayName("Plain file paths are left untouched")
  void leavesPlainPathsUntouched() {
    Object resolved =
        secretPropertyProcessor.processPropertyValue(
            APP_PRIVATE_KEY_PATH_PROPERTY, "/plain/path/gh-app-key.pem");

    assertThat(resolved).isEqualTo("/plain/path/gh-app-key.pem");
  }

  @Test
  @DisplayName("The resolved key path is usable for GitHub App authentication")
  void resolvedPathIsUsableByGitHubAppCredentials() {
    String resolvedPath =
        (String)
            secretPropertyProcessor.processPropertyValue(
                APP_PRIVATE_KEY_PATH_PROPERTY, SECRET_FILE_URI);

    GitHubAppCredentials credentials =
        new GitHubAppCredentials("12345", resolvedPath, "67890", null);

    assertDoesNotThrow(credentials::toAuthenticator);
  }
}
