/*
 * Copyright 2017 Target, Inc.
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
package com.netflix.spinnaker.gate.security.x509

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import java.security.cert.X509Certificate

@Component
@ConditionalOnProperty("x509.roleOid")
class OidRolesExtractor implements X509RolesExtractor {

  @Autowired
  X509Config config

  @Override
  Collection<String> fromCertificate(X509Certificate cert) {
    byte[] bytes = cert.getExtensionValue(config.roleOid)

    if (bytes == null) {
      return []
    }
    ASN1OctetString octetString = (ASN1OctetString) new ASN1InputStream(new ByteArrayInputStream(bytes)).readObject()
    ASN1InputStream inputStream = new ASN1InputStream(new ByteArrayInputStream(octetString.getOctets()))
    def groups = inputStream.readObject()?.toString()?.split("\\n")
    return groups.findAll{ !it.isEmpty()  }
  }
}
