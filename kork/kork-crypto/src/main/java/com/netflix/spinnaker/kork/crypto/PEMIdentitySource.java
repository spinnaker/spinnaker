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
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

/**
 * Implements an identity source based on a PEM-encoded private key and certificate file. Private
 * keys must not be encrypted (e.g., they must include the {@code -nodes} option when generating
 * them via OpenSSL). The certificate file must use the same key algorithm as the private key file.
 * Supported key algorithms include RSA and EC.
 */
@RequiredArgsConstructor
public class PEMIdentitySource implements X509IdentitySource {

  private final Path keyFile;
  private final Path certificateFile;

  @Getter private Instant lastLoaded = Instant.MIN;
  @Getter private Instant expiresAt = Instant.MAX;

  @Override
  public Instant getLastModified() {
    try {
      return Files.getLastModifiedTime(certificateFile).toInstant();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public X509Identity load() throws IOException {
    PrivateKey privateKey;
    try (var parser = new PEMParser(Files.newBufferedReader(keyFile))) {
      var object = parser.readObject();
      PrivateKeyInfo keyInfo;
      if (object instanceof PrivateKeyInfo) {
        keyInfo = (PrivateKeyInfo) object;
      } else if (object instanceof PEMKeyPair) {
        keyInfo = ((PEMKeyPair) object).getPrivateKeyInfo();
      } else {
        // could be an encrypted private key?
        throw new UnsupportedEncodingException(
            "Unsupported private key data type: " + object.getClass());
      }
      var keySpec = new PKCS8EncodedKeySpec(keyInfo.getEncoded());
      var keyFactory = KeyFactories.getKeyFactory(keyInfo.getPrivateKeyAlgorithm().getAlgorithm());
      privateKey = keyFactory.generatePrivate(keySpec);
    } catch (InvalidKeySpecException e) {
      throw new NestedSecurityIOException(e);
    }
    List<X509Certificate> certificates = new ArrayList<>();
    try (var parser = new PEMParser(Files.newBufferedReader(certificateFile))) {
      var certificateFactory = StandardCrypto.getX509CertificateFactory();
      Object parsedCertificate;
      while ((parsedCertificate = parser.readObject()) != null) {
        var certificateHolder = (X509CertificateHolder) parsedCertificate;
        var cert =
            (X509Certificate)
                certificateFactory.generateCertificate(
                    new ByteArrayInputStream(certificateHolder.getEncoded()));
        Instant notAfter = cert.getNotAfter().toInstant();
        if (expiresAt.isAfter(notAfter)) {
          expiresAt = notAfter;
        }
        certificates.add(cert);
      }
    } catch (CertificateException e) {
      throw new NestedSecurityIOException(e);
    }
    lastLoaded = Instant.now();
    return new StaticX509Identity(privateKey, certificates.toArray(X509Certificate[]::new));
  }
}
