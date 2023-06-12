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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.stream.Stream;
import javax.net.ssl.X509TrustManager;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.Encodable;

/**
 * Provides utility methods related to trust stores. Trust stores are used to validate certificate
 * chains in TLS and other X.509 use cases.
 */
public final class TrustStores {
  private TrustStores() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static KeyStore loadPEM(Path caCertificates)
      throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
    var keyStore = StandardCrypto.getPKCS12KeyStore();
    keyStore.load(null, null);
    try (var parser = new PEMParser(Files.newBufferedReader(caCertificates))) {
      var certificateFactory = StandardCrypto.getX509CertificateFactory();
      Object parsedCertificate;
      while ((parsedCertificate = parser.readObject()) != null) {
        var certificateStream =
            new ByteArrayInputStream(((Encodable) parsedCertificate).getEncoded());
        var certificate = certificateFactory.generateCertificate(certificateStream);
        var alias = X509Identity.generateAlias(certificate);
        keyStore.setCertificateEntry(alias, certificate);
      }
    }
    return keyStore;
  }

  public static X509TrustManager loadTrustManager(KeyStore keyStore) throws KeyStoreException {
    var trustManagerFactory = StandardCrypto.getPKIXTrustManagerFactory();
    trustManagerFactory.init(keyStore);
    return Stream.of(trustManagerFactory.getTrustManagers())
        .filter(X509TrustManager.class::isInstance)
        .map(X509TrustManager.class::cast)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Provided KeyStore does not contain any X.509 certificates"));
  }

  public static X509TrustManager getSystemTrustManager() {
    var trustManagerFactory = StandardCrypto.getPKIXTrustManagerFactory();
    try {
      trustManagerFactory.init((KeyStore) null);
    } catch (KeyStoreException e) {
      throw new NestedSecurityRuntimeException(e);
    }
    return Stream.of(trustManagerFactory.getTrustManagers())
        .filter(X509TrustManager.class::isInstance)
        .map(X509TrustManager.class::cast)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No system default trust store configured"));
  }
}
