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

package com.netflix.spinnaker.kork.crypto;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Identity source using a keystore file and password provider functions for the keystore and
 * identity private key.
 */
@RequiredArgsConstructor
public class PasswordProtectedKeyStoreIdentitySource implements X509IdentitySource {
  private final Path keystoreFile;
  private final String keystoreType;
  private final PasswordProvider keystorePasswordProvider;
  private final PasswordProvider privateKeyPasswordProvider;

  @Getter private Instant lastLoaded = Instant.MIN;
  @Getter private Instant expiresAt = Instant.MAX;

  @Override
  public Instant getLastModified() {
    try {
      return Files.getLastModifiedTime(keystoreFile).toInstant();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public X509Identity load() throws IOException {
    KeyStore keyStore;
    char[] password;
    try {
      keyStore = KeyStore.getInstance(keystoreType);
      password = keystorePasswordProvider.password();
    } catch (GeneralSecurityException e) {
      throw new NestedSecurityIOException(e);
    }
    try (var stream = Files.newInputStream(keystoreFile)) {
      keyStore.load(stream, password);
    } catch (CertificateException | NoSuchAlgorithmException e) {
      throw new NestedSecurityIOException(e);
    } finally {
      Arrays.fill(password, (char) 0);
    }
    X509Identity identity;
    try {
      password = privateKeyPasswordProvider.password();
      identity = findIdentity(keyStore, new KeyStore.PasswordProtection(password));
    } catch (GeneralSecurityException e) {
      throw new NestedSecurityIOException(e);
    } finally {
      Arrays.fill(password, (char) 0);
    }
    for (X509Certificate certificate : identity.getCertificateChain()) {
      Instant notAfter = certificate.getNotAfter().toInstant();
      if (expiresAt.isAfter(notAfter)) {
        expiresAt = notAfter;
      }
    }
    lastLoaded = Instant.now();
    return identity;
  }

  private X509Identity findIdentity(
      KeyStore keyStore, KeyStore.ProtectionParameter protectionParameter)
      throws GeneralSecurityException {
    var aliases = keyStore.aliases();
    while (aliases.hasMoreElements()) {
      var alias = aliases.nextElement();
      if (keyStore.isKeyEntry(alias)) {
        var entry = keyStore.getEntry(alias, protectionParameter);
        if (entry instanceof KeyStore.PrivateKeyEntry) {
          var privateKeyEntry = (KeyStore.PrivateKeyEntry) entry;
          var chain = privateKeyEntry.getCertificateChain();
          if (chain instanceof X509Certificate[]) {
            X509Certificate[] certificateChain = (X509Certificate[]) chain;
            PrivateKey privateKey = privateKeyEntry.getPrivateKey();
            return new StaticX509Identity(privateKey, certificateChain);
          }
        }
      }
    }
    throw new IllegalArgumentException("No private key entry found in keystore: " + keystoreFile);
  }
}
