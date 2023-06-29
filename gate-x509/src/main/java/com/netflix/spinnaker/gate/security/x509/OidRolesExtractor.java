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

package com.netflix.spinnaker.gate.security.x509;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1String;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty("x509.role-oid")
public class OidRolesExtractor implements X509RolesExtractor {
  private static final Pattern ROLE_SEPARATOR = Pattern.compile("\\n");

  @Setter(
      value = AccessLevel.PACKAGE,
      onMethod_ = {@Autowired},
      onParam_ = {@Value("${x509.role-oid:}")})
  private String roleOid;

  @Override
  @SneakyThrows
  public Collection<String> fromCertificate(X509Certificate cert) {
    byte[] bytes = cert.getExtensionValue(roleOid);

    if (bytes == null) {
      return List.of();
    }
    ASN1OctetString octetString = ASN1OctetString.getInstance(bytes);
    var primitive = ASN1Primitive.fromByteArray(octetString.getOctets());
    String string;
    if (primitive instanceof ASN1String) {
      // when using OID 1.2.840.10070.8.1, this is an ASN1UTF8String
      string = ((ASN1String) primitive).getString();
    } else {
      // hope for the best (note that ASN1Sequence::toString and ASN1Set::toString are formatted
      // like AbstractCollection::toString which is probably not what you want)
      string = primitive.toString();
    }
    return ROLE_SEPARATOR
        .splitAsStream(string)
        .filter(StringUtils::hasLength)
        .collect(Collectors.toSet());
  }
}
