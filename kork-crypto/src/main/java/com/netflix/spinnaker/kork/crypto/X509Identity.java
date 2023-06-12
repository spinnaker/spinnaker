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

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Destroyable;
import javax.security.auth.x500.X500PrivateCredential;
import org.bouncycastle.crypto.digests.SHAKEDigest;

/** Represents a cryptographic identity using a private key and certificate. */
public interface X509Identity extends Destroyable {

  /** Returns the private key and certificate for this identity. */
  X500PrivateCredential getPrivateCredential();

  /** Returns the certificate chain for this identity. */
  X509Certificate[] getCertificateChain();

  /**
   * Creates an {@link SSLContext} from this identity using the system default {@link TrustManager}
   * and {@link SecureRandom}.
   *
   * @return a new SSLContext using this identity for authentication
   * @throws KeyManagementException if there is an error initializing the SSLContext
   */
  default SSLContext createSSLContext() throws KeyManagementException {
    var context = StandardCrypto.getTLSContext();
    var keyManagers = new KeyManager[] {new IdentityX509KeyManager(this)};
    context.init(keyManagers, null, null);
    return context;
  }

  /**
   * Creates an {@link SSLContext} from this identity using a specific trust manager.
   *
   * @param trustManager the trust manager to use for validating TLS peers
   * @return a new SSLContext using this identity for authentication
   * @throws KeyManagementException if there is an error initializing the SSLContext
   * @see TrustStores#loadTrustManager(KeyStore)
   */
  default SSLContext createSSLContext(X509TrustManager trustManager) throws KeyManagementException {
    var context = StandardCrypto.getTLSContext();
    context.init(
        new KeyManager[] {new IdentityX509KeyManager(this)},
        new TrustManager[] {trustManager},
        null);
    return context;
  }

  /**
   * Creates an {@link SSLContext} from this identity using a specific trust manager and source of
   * randomness.
   *
   * @param trustManager the trust manager to use for validating TLS peers
   * @param secureRandom the source of randomness to use for generating cryptographic bits
   * @return a new SSLContext using this identity for authentication
   * @throws KeyManagementException if there is an error initializing the SSLContext
   */
  default SSLContext createSSLContext(X509TrustManager trustManager, SecureRandom secureRandom)
      throws KeyManagementException {
    var context = StandardCrypto.getTLSContext();
    context.init(
        new KeyManager[] {new IdentityX509KeyManager(this)},
        new TrustManager[] {trustManager},
        secureRandom);
    return context;
  }

  /**
   * Generates a certificate alias string. This alias is computed from an extensible output function
   * (XOF) of the certificate's public key.
   *
   * @param certificate certificate to compute an alias for
   * @return the computed alias
   */
  static String generateAlias(Certificate certificate) {
    var encodedPublicKey = certificate.getPublicKey().getEncoded();
    // SHAKE is an extensible output function variant of SHA-3; this particular version is SHAKE-128
    // https://crypto.stackexchange.com/a/54249
    var digest = new SHAKEDigest();
    digest.update(encodedPublicKey, 0, encodedPublicKey.length);
    // use a size divisible by 3 for nicer base64 encoded output (20 chars for 15 bytes)
    var hash = new byte[15];
    digest.doFinal(hash, 0, hash.length);
    return Base64.getEncoder().encodeToString(hash);
  }
}
