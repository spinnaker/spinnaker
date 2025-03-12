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
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshableIdentityTest {
  SSLContext serverContext;
  SSLContext clientContext;

  @BeforeEach
  void setUp() throws Exception {
    // start with a fresh CA cert
    var ca = CertificateIdentity.generateSelfSigned();
    var caKeyFile = Files.createTempFile("ca", ".key");
    var caCertFile = Files.createTempFile("ca", ".crt");
    ca.saveAsPEM(caKeyFile, caCertFile);
    var trustManager = TrustStores.loadTrustManager(TrustStores.loadPEM(caCertFile));

    // common cert attributes
    var tlsKeyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement);

    // generate server keypair
    var serverKeyPair = TestCrypto.generateKeyPair();
    var serverPublicKey = serverKeyPair.getPublic();
    var serverPrivateKey = serverKeyPair.getPrivate();
    // traditionally, a TLS server certificate relied on the common name in the subject for the
    // hostname, but these days, we specify the hostname in the subject alternative names extension
    var serverName = new X500Principal("CN=localhost");
    var serverAlternativeName = new DERSequence(new GeneralName(GeneralName.dNSName, "localhost"));
    var serverExtensions = new ExtensionsGenerator();
    serverExtensions.addExtension(Extension.keyUsage, true, tlsKeyUsage);
    serverExtensions.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    serverExtensions.addExtension(Extension.subjectAlternativeName, false, serverAlternativeName);

    // request CA signature and store as PEM files
    var serverCertificationRequest =
        new JcaPKCS10CertificationRequestBuilder(serverName, serverPublicKey)
            .addAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, serverExtensions.generate())
            .build(CertificateIdentity.signerFrom(serverPrivateKey));
    var serverIdentity =
        CertificateIdentity.fromCredentials(
            serverPrivateKey, ca.signCertificationRequest(serverCertificationRequest));
    var serverKeyFile = Files.createTempFile("server", ".key");
    var serverCertFile = Files.createTempFile("server", ".crt");
    serverIdentity.saveAsPEM(serverKeyFile, serverCertFile);
    var serverIdentitySource = X509IdentitySource.fromPEM(serverKeyFile, serverCertFile);
    var server = serverIdentitySource.refreshable(Duration.ofMinutes(15));
    serverContext = server.createSSLContext(trustManager);

    // generate client keypair
    var clientKeyPair = TestCrypto.generateKeyPair();
    var clientPublicKey = clientKeyPair.getPublic();
    var clientPrivateKey = clientKeyPair.getPrivate();
    var clientName = new X500Principal("CN=spinnaker, O=spinnaker");
    var clientAlternativeName =
        new DERSequence(new GeneralName(GeneralName.rfc822Name, "spinnaker@localhost"));
    var clientExtensions = new ExtensionsGenerator();
    clientExtensions.addExtension(Extension.keyUsage, true, tlsKeyUsage);
    clientExtensions.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    clientExtensions.addExtension(Extension.subjectAlternativeName, false, clientAlternativeName);

    // request CA signature and store as PEM files
    var clientCertificationRequest =
        new JcaPKCS10CertificationRequestBuilder(clientName, clientPublicKey)
            .addAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, clientExtensions.generate())
            .build(CertificateIdentity.signerFrom(clientPrivateKey));
    var clientIdentity =
        CertificateIdentity.fromCredentials(
            clientPrivateKey, ca.signCertificationRequest(clientCertificationRequest));
    var clientKeyFile = Files.createTempFile("client", ".key");
    var clientCertFile = Files.createTempFile("client", ".crt");
    clientIdentity.saveAsPEM(clientKeyFile, clientCertFile);
    var clientIdentitySource = X509IdentitySource.fromPEM(clientKeyFile, clientCertFile);
    var client = clientIdentitySource.refreshable(Duration.ofMinutes(15));
    clientContext = client.createSSLContext(trustManager);
  }

  @Test
  void smokeTest() throws Exception {
    var server = HttpsServer.create();
    server.setHttpsConfigurator(new HttpsConfigurator(serverContext));

    // set up a simple echo handler
    server.createContext(
        "/echo",
        exchange -> {
          try (var request = exchange.getRequestBody();
              var response = exchange.getResponseBody()) {
            byte[] body = request.readAllBytes();
            exchange.sendResponseHeaders(200, body.length);
            response.write(body);
          }
        });
    // set up a random server port to serve from
    int port;
    try (var socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      port = socket.getLocalPort();
    }
    var serverAddress = new InetSocketAddress("localhost", port);
    server.bind(serverAddress, 0);
    server.start();
    try {
      var client = HttpClient.newBuilder().sslContext(clientContext).build();
      var request =
          HttpRequest.newBuilder(
                  new URI(
                      "https",
                      null,
                      serverAddress.getHostName(),
                      serverAddress.getPort(),
                      "/echo",
                      null,
                      null))
              .POST(HttpRequest.BodyPublishers.ofString("Hello, world!"))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals("Hello, world!", response.body());
    } finally {
      server.stop(1);
    }
  }
}
