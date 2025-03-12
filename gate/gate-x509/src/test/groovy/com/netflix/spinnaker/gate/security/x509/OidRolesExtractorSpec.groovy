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

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import spock.lang.Specification
import spock.lang.Unroll

import javax.security.auth.x500.X500Principal
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration

class OidRolesExtractorSpec extends Specification {

  @Unroll
  def "should return ldap roles - #description"() {
    given:
    String oid = "1.2.840.10070.8.1"
    def extractor = new OidRolesExtractor(roleOid: oid)
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

  def "should return roles listed in oid extension - #description"() {
    given:
    String roleOid = '1.2.840.10070.8.1'
    def extractor = new OidRolesExtractor(roleOid: roleOid)
    def certificate = generateCertificate(roleOid, roles)

    when:
    def extractedRoles = extractor.fromCertificate(certificate)

    then:
    roles.containsAll(extractedRoles) && extractedRoles.containsAll(roles)

    where:
    description    | roles
    'empty list'   | []
    'one group'    | ['groupA']
    'two groups'   | ['groupA', 'groupB']
    'three groups' | ['groupA', 'groupC', 'groupE']
  }

  private static X509Certificate generateCertificate(String roleOid, List<String> roles) {
    // generate a P.256 keypair
    def generator = KeyPairGenerator.getInstance('EC')
    generator.initialize(256)
    def keypair = generator.generateKeyPair()
    def signer = new JcaContentSignerBuilder('SHA256withECDSA')
      .build(keypair.private)
    // set up a self-signed certificate that expires in an hour
    def subject = new X500Principal('CN=spinnaker')
    def notBefore = new Date()
    def notAfter = Date.from(notBefore.toInstant() + Duration.ofHours(1))
    def serial = BigInteger.valueOf(notBefore.time)
    // standard spinnaker OID extension: encode the roles in a string separated by newlines
    def oid = new ASN1ObjectIdentifier(roleOid)
    def encodedRoles = new DERUTF8String(roles.join('\n'))
    // generate a self-signed certificate with only the roles extension specified;
    // a real certificate should also set the key usage, basic constraints, and extended key usage extensions
    def holder = new JcaX509v3CertificateBuilder(
        subject, serial, notBefore, notAfter, subject, keypair.public)
      .addExtension(oid, false, encodedRoles)
      .build(signer)
    // convert from bouncycastle to plain java
    CertificateFactory.getInstance('X.509')
      .generateCertificate(new ByteArrayInputStream(holder.encoded)) as X509Certificate
  }
}
