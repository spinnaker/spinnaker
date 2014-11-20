/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security.onelogin.saml

import java.security.cert.*
import org.apache.commons.codec.binary.Base64

class Certificate {
  X509Certificate x509Cert;

  /**
   * Loads certificate from a base64 encoded string
   */
  public void loadCertificate(String certificate) throws CertificateException {
    CertificateFactory fty = CertificateFactory.getInstance("X.509");
    ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decodeBase64(certificate.getBytes()));
    x509Cert = (X509Certificate) fty.generateCertificate(bais);
  }

  /**
   * Loads a certificate from a encoded base64 byte array.
   * @param certificate an encoded base64 byte array.
   * @throws CertificateException In case it can't load the certificate.
   */
  public void loadCertificate(byte[] certificate) throws CertificateException {
    CertificateFactory fty = CertificateFactory.getInstance("X.509");
    ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decodeBase64(certificate));
    x509Cert = (X509Certificate) fty.generateCertificate(bais);
  }
}
