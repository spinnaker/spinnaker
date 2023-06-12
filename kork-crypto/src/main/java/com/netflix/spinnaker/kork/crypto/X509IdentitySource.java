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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.springframework.aop.framework.ProxyFactory;

/**
 * Provides a source for loading an {@link X509Identity} from some underlying key and certificate.
 * These sources should keep track of the last time an identity was loaded along with the earliest
 * expiration date of any contained certificates. These identities may be adapted into refreshable
 * identities via {@link #refreshable(Duration)} which specifies a polling duration in which to
 * recheck if the identity should be reloaded.
 */
public interface X509IdentitySource {
  /**
   * Returns the time this source last loaded an identity. This may return {@link Instant#MIN} if no
   * identity has been loaded yet.
   */
  Instant getLastLoaded();

  /** Returns the time that the key or certificate source was last modified. */
  Instant getLastModified();

  /**
   * Returns the earliest date and time of expiration of the certificates included in this source.
   * This may return {@link Instant#MAX} if no expiration date is known.
   */
  Instant getExpiresAt();

  /**
   * Loads an {@link X509Identity} from this underlying source. Any thrown {@link
   * java.security.GeneralSecurityException} instances should be rethrown in a {@link
   * NestedSecurityIOException}.
   */
  X509Identity load() throws IOException;

  /**
   * Creates a refreshable {@link X509Identity} from this source and the given refresh check delay.
   * The returned identity will periodically check if a reload is required based on the last
   * modified timestamp of the source along with the expiration of the certificates.
   *
   * @see IdentityX509KeyManager
   */
  default X509Identity refreshable(Duration refreshCheckDelay) {
    var identity = new RefreshableX509Identity(this);
    identity.setRefreshCheckDelay(refreshCheckDelay.toMillis());
    return ProxyFactory.getProxy(X509Identity.class, identity);
  }

  /** Creates an identity source from a PEM-encoded private key file and certificate file. */
  static X509IdentitySource fromPEM(Path keyFile, Path certificateFile) {
    return new PEMIdentitySource(keyFile, certificateFile);
  }

  /**
   * Creates an identity source from a PKCS#12-encoded keystore file and password provider function.
   */
  static X509IdentitySource fromPKCS12(Path keystoreFile, PasswordProvider passwordProvider) {
    return fromPKCS12(keystoreFile, passwordProvider, passwordProvider);
  }

  /**
   * Creates an identity source from a PKCS#12-encoded keystore file, keystore password provider
   * function, and identity private key password provider function.
   */
  static X509IdentitySource fromPKCS12(
      Path keystoreFile,
      PasswordProvider keystorePasswordProvider,
      PasswordProvider privateKeyPasswordProvider) {
    return fromKeyStore(
        keystoreFile, "PKCS12", keystorePasswordProvider, privateKeyPasswordProvider);
  }

  /** Creates an identity source from a password-protected {@link java.security.KeyStore} file. */
  static X509IdentitySource fromKeyStore(
      Path keystoreFile, String keystoreType, PasswordProvider passwordProvider) {
    return fromKeyStore(keystoreFile, keystoreType, passwordProvider, passwordProvider);
  }

  /**
   * Creates an identity source from a password-protected {@link java.security.KeyStore} file.
   *
   * @param keystoreFile path to the keystore file to read
   * @param keystoreType the type of the keystore (typically {@code PKCS12})
   * @param keystorePasswordProvider function for obtaining the password to decrypt the keystore
   *     file
   * @param privateKeyPasswordProvider function for obtaining the password to decrypt the identity
   *     private key (this is typically the same as the keystore password)
   * @return an identity source from the provided keystore details
   */
  static X509IdentitySource fromKeyStore(
      Path keystoreFile,
      String keystoreType,
      PasswordProvider keystorePasswordProvider,
      PasswordProvider privateKeyPasswordProvider) {
    return new PasswordProtectedKeyStoreIdentitySource(
        keystoreFile, keystoreType, keystorePasswordProvider, privateKeyPasswordProvider);
  }
}
