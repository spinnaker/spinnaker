/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security.saml

import groovy.util.logging.Slf4j
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import org.opensaml.common.SAMLObject
import org.opensaml.common.binding.BasicSAMLMessageContext
import org.opensaml.common.xml.SAMLConstants
import org.opensaml.saml2.core.Assertion
import org.opensaml.saml2.core.Attribute
import org.opensaml.saml2.core.AuthnRequest
import org.opensaml.saml2.core.Issuer
import org.opensaml.saml2.core.Response
import org.opensaml.saml2.metadata.SingleSignOnService
import org.opensaml.ws.transport.http.HttpServletResponseAdapter
import org.opensaml.xml.Configuration
import org.opensaml.xml.XMLObject
import org.opensaml.xml.XMLObjectBuilderFactory
import org.opensaml.xml.security.CriteriaSet
import org.opensaml.xml.security.credential.Credential
import org.opensaml.xml.security.credential.KeyStoreCredentialResolver
import org.opensaml.xml.security.criteria.EntityIDCriteria
import org.opensaml.xml.security.x509.BasicX509Credential
import org.opensaml.xml.signature.SignatureValidator

import javax.servlet.http.HttpServletResponse
import javax.xml.namespace.QName
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Slf4j
class SAMLUtils {
  public static <T> T buildSAMLObject(final Class<T> clazz) {
    XMLObjectBuilderFactory builderFactory = Configuration.getBuilderFactory()

    QName defaultElementName = (QName) clazz.getDeclaredField("DEFAULT_ELEMENT_NAME").get(null)
    T object = (T) builderFactory.getBuilder(defaultElementName).buildObject(defaultElementName)
    return object
  }

  public static AuthnRequest buildAuthnRequest(String destinationUrl, URL redirectUrl, String issuerId) {
    AuthnRequest authnRequest = buildSAMLObject(AuthnRequest)
    authnRequest.setID("_" + UUID.randomUUID().toString())
    authnRequest.setIssueInstant(new DateTime())
    authnRequest.setAssertionConsumerServiceURL(redirectUrl.toString())
    authnRequest.setDestination(destinationUrl)
    authnRequest.setIssuer(buildIssuer(issuerId))

    return authnRequest
  }

  static Optional<Credential> buildCredential(String keystoreType,
                                              File keystoreFile,
                                              String keystorePassword,
                                              String keystoreAliasName) {
    if (!keystoreType || !keystoreAliasName || !keystoreFile?.exists()) {
      return Optional.empty()
    }

    def keystore = KeyStore.getInstance(keystoreType)
    keystore.load(new FileInputStream(keystoreFile), keystorePassword.toCharArray())

    KeyStoreCredentialResolver resolver = new KeyStoreCredentialResolver(keystore, [
      (keystoreAliasName): keystorePassword
    ])

    CriteriaSet criteriaSet = new CriteriaSet(new EntityIDCriteria(keystoreAliasName))
    return Optional.of(resolver.resolveSingle(criteriaSet))
  }

  static BasicSAMLMessageContext buildSAMLMessageContext(AuthnRequest authnRequest,
                                                         HttpServletResponse response,
                                                         String url) {
    def context = new BasicSAMLMessageContext<SAMLObject, AuthnRequest, SAMLObject>()
    context.setOutboundMessageTransport(new HttpServletResponseAdapter(response, true))
    context.setOutboundSAMLMessage(authnRequest)

    def endpoint = buildSAMLObject(SingleSignOnService)
    endpoint.setBinding(SAMLConstants.SAML2_REDIRECT_BINDING_URI)
    endpoint.setLocation(url)
    context.setPeerEntityEndpoint(endpoint)

    return context
  }

  static Assertion buildAssertion(String samlResponse, X509Certificate certificate = null) {
    def base64DecodedResponse = new Base64().decode(samlResponse)
    def documentBuilderFactory = DocumentBuilderFactory.newInstance()
    documentBuilderFactory.setNamespaceAware(true)

    def docBuilder = documentBuilderFactory.newDocumentBuilder()
    def document = docBuilder.parse(new ByteArrayInputStream(base64DecodedResponse))

    def element = document.getDocumentElement()
    def unmarshaller = Configuration.getUnmarshallerFactory().getUnmarshaller(element)
    def response = (Response) unmarshaller.unmarshall(element)

    if (!response.assertions) {
      throw new IllegalStateException("No assertions found in response (samlResponse: ${new String(base64DecodedResponse)})")
    }

    if (certificate) {
      response.getDOM().getOwnerDocument().getDocumentElement().setIdAttribute("ID", true)

      BasicX509Credential publicCredential = new BasicX509Credential();
      publicCredential.setEntityCertificate(certificate);

      SignatureValidator sigValidator = new SignatureValidator(publicCredential)
      sigValidator.validate(response.getSignature())
    }

    return response.assertions[0]
  }

  static X509Certificate loadCertificate(String certificate) throws CertificateException {
    ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decodeBase64(certificate.getBytes()))
    return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(bais)
  }

  private static Issuer buildIssuer(String issuerId) {
    Issuer issuer = buildSAMLObject(Issuer)
    issuer.setValue(issuerId)
    return issuer
  }

  private static void logSAMLObject(XMLObject object) {
    try {
      def factory = DocumentBuilderFactory.newInstance()
      factory.setNamespaceAware(true)

      def document = factory.newDocumentBuilder().newDocument()
      Configuration.getMarshallerFactory().getMarshaller(object).marshall(object, document)

      def transformer = TransformerFactory.newInstance().newTransformer()
      transformer.setOutputProperty(OutputKeys.INDENT, "yes")

      def result = new StreamResult(new StringWriter())
      transformer.transform(new DOMSource(document), result)

      log.info(result.getWriter().toString())
    } catch (Exception e) {
      log.error(e.getMessage(), e)
    }
  }

  static Map<String, List<String>> extractAttributes(Assertion assertion) {
    def attributes = [:]
    assertion.attributeStatements*.attributes.flatten().each { Attribute attribute ->
      def name = attribute.name
      def values = attribute.attributeValues*.getDOM()*.textContent
      attributes[name] = values
    }

    return attributes
  }
}
