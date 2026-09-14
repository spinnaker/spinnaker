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

package com.netflix.spinnaker.kork.github.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Generates throwaway RSA private keys in the PEM formats a GitHub App private key can take, for
 * tests that need to exercise key loading and JWT signing.
 *
 * <p>Keys are generated per call and never leave the JVM that created them; nothing here should be
 * used outside tests.
 */
public final class GitHubAppTestKeys {

  private static final int KEY_SIZE = 2048;

  private GitHubAppTestKeys() {}

  /**
   * Generates a PKCS#8 ("BEGIN PRIVATE KEY") PEM, the format GitHub issues for app private keys.
   *
   * @return the PEM contents
   */
  public static String generatePkcs8Pem() {
    return "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(generateKeyPairPrivateKeyEncoded())
        + "\n-----END PRIVATE KEY-----\n";
  }

  /**
   * Generates a PKCS#8 PEM and writes it to the given file.
   *
   * @param file where to write the key
   * @return the file that was written, for convenient chaining
   */
  public static Path writePkcs8Pem(Path file) {
    try {
      return Files.writeString(file, generatePkcs8Pem());
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to write test GitHub App private key to " + file, e);
    }
  }

  private static byte[] generateKeyPairPrivateKeyEncoded() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(KEY_SIZE);
      return keyPairGenerator.generateKeyPair().getPrivate().getEncoded();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation is unavailable", e);
    }
  }
}
