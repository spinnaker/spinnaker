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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.kork.crypto.test.CertificateIdentity;
import com.netflix.spinnaker.kork.crypto.test.TestCrypto;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class PKCS12IdentitySourceTest {
  @Test
  @SneakyThrows
  void smokeTest() {
    CertificateIdentity certificateIdentity = CertificateIdentity.generateSelfSigned();
    Path keystoreFile = Files.createTempFile("keystore", ".p12");
    char[] password = TestCrypto.generatePassword(24);
    certificateIdentity.saveAsPKCS12(keystoreFile, password);
    var identitySource = X509IdentitySource.fromPKCS12(keystoreFile, password::clone);
    var identityCredential = identitySource.load().getPrivateCredential();
    assertEquals(certificateIdentity.getCertificate(), identityCredential.getCertificate());
    assertEquals(certificateIdentity.getPrivateKey(), identityCredential.getPrivateKey());
  }
}
