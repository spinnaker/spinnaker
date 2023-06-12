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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Provides simpler access to standard Java cryptography algorithm classes. These are all included
 * in standard Java distributions.
 *
 * @see <a
 *     href="https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html">Standard
 *     algorithm names</a>
 */
public final class StandardCrypto {

  private StandardCrypto() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static CertificateFactory getX509CertificateFactory() {
    try {
      return CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new NestedSecurityRuntimeException(e);
    }
  }

  public static KeyStore getPKCS12KeyStore() {
    try {
      return KeyStore.getInstance("PKCS12");
    } catch (KeyStoreException e) {
      throw new NestedSecurityRuntimeException(e);
    }
  }

  public static TrustManagerFactory getPKIXTrustManagerFactory() {
    try {
      return TrustManagerFactory.getInstance("PKIX");
    } catch (NoSuchAlgorithmException e) {
      throw new NestedSecurityRuntimeException(e);
    }
  }

  public static SSLContext getTLSContext() {
    try {
      return SSLContext.getInstance("TLS");
    } catch (NoSuchAlgorithmException e) {
      throw new NestedSecurityRuntimeException(e);
    }
  }
}
