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

import spock.lang.Specification
import spock.lang.Unroll

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class OidRolesExtractorSpec extends Specification {

  @Unroll
  def "should return ldap roles - #description"() {
    given:
    String oid = "1.2.840.10070.8.1"
    def extractor = new OidRolesExtractor(config: new X509Config(roleOid: oid))
    def resource = OidRolesExtractorSpec.classLoader.getResource(certFilePath)
    CertificateFactory cf = CertificateFactory.getInstance("X.509")
    X509Certificate cert = (X509Certificate) cf.generateCertificate(resource.openStream())

    when:
    def roles = extractor.fromCertificate(cert)

    then:
    roles.size() == roleCount

    where:
    description                 | certFilePath                 | roleCount
    "no member of extension"    | "noMemberOfExtension.crt"    | 0
    "no roles"                  | "memberOfNoRoles.crt"        | 0
    "one role"                  | "memberOfOneRole.crt"        | 1
    "more roles"                | "memberOfTwoRoles.crt"       | 2
  }
}
