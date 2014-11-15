/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.gate.security.onelogin.AppSettings
import java.text.SimpleDateFormat
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.xml.stream.*
import org.apache.commons.codec.binary.Base64

class AuthRequest {

  private final String id
  private final String issueInstant
  private final AppSettings appSettings

  public AuthRequest(AppSettings appSettings) {
    this.appSettings = appSettings
    id = "_" + UUID.randomUUID().toString()
    SimpleDateFormat simpleDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    issueInstant = simpleDf.format(new Date())
  }

  public String getRequest() throws XMLStreamException, IOException {
    String result = ""

    ByteArrayOutputStream baos = new ByteArrayOutputStream()

    XMLOutputFactory factory = XMLOutputFactory.newInstance()
    XMLStreamWriter writer = factory.createXMLStreamWriter(baos)

    writer.writeStartElement("samlp", "AuthnRequest", "urn:oasis:names:tc:SAML:2.0:protocol")
    writer.writeNamespace("samlp", "urn:oasis:names:tc:SAML:2.0:protocol")

    writer.writeAttribute("ID", id)
    writer.writeAttribute("Version", "2.0")
    writer.writeAttribute("IssueInstant", this.issueInstant)
    writer.writeAttribute("ProtocolBinding", "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST")
    writer.writeAttribute("AssertionConsumerServiceURL", this.appSettings.getAssertionConsumerServiceUrl())

    writer.writeStartElement("saml", "Issuer", "urn:oasis:names:tc:SAML:2.0:assertion")
    writer.writeNamespace("saml", "urn:oasis:names:tc:SAML:2.0:assertion")
    writer.writeCharacters(this.appSettings.getIssuer())
    writer.writeEndElement()

    writer.writeStartElement("samlp", "NameIDPolicy", "urn:oasis:names:tc:SAML:2.0:protocol")

    writer.writeAttribute("Format", "urn:oasis:names:tc:SAML:2.0:nameid-format:unspecified")
    writer.writeAttribute("AllowCreate", "true")
    writer.writeEndElement()

    writer.writeStartElement("samlp", "RequestedAuthnContext", "urn:oasis:names:tc:SAML:2.0:protocol")

    writer.writeAttribute("Comparison", "exact")

    writer.writeStartElement("saml", "AuthnContextClassRef", "urn:oasis:names:tc:SAML:2.0:assertion")
    writer.writeNamespace("saml", "urn:oasis:names:tc:SAML:2.0:assertion")
    writer.writeCharacters("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport")
    writer.writeEndElement()

    writer.writeEndElement()
    writer.writeEndElement()
    writer.flush()

    result = encodeSAMLRequest(baos.toByteArray())
    return result
  }

  private static String encodeSAMLRequest(byte[] pSAMLRequest) throws RuntimeException {

    Base64 base64Encoder = new Base64()

    try {
      ByteArrayOutputStream byteArray = new ByteArrayOutputStream()
      Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)

      DeflaterOutputStream defer = new DeflaterOutputStream(byteArray, deflater)
      defer.write(pSAMLRequest)
      defer.close()
      byteArray.close()

      String stream = new String(base64Encoder.encode(byteArray.toByteArray()))

      return stream.trim()
    } catch (Exception e) {
      throw new RuntimeException(e)
    }
  }
}
