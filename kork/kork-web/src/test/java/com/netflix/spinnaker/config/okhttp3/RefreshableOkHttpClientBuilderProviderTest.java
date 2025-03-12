/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config.okhttp3;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.MetricsEndpointConfiguration;
import com.netflix.spinnaker.config.ServiceEndpoint;
import com.netflix.spinnaker.kork.crypto.StandardCrypto;
import com.netflix.spinnaker.kork.crypto.TrustStores;
import com.netflix.spinnaker.kork.crypto.X509Identity;
import com.netflix.spinnaker.kork.crypto.X509IdentitySource;
import com.netflix.spinnaker.kork.crypto.test.CertificateIdentity;
import com.netflix.spinnaker.kork.crypto.test.TestCrypto;
import com.netflix.spinnaker.kork.metrics.SpectatorConfiguration;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

class RefreshableOkHttpClientBuilderProviderTest {

  Path truststore;
  HttpsServer server;
  ServiceEndpoint serviceEndpoint;
  HttpUrl testUrl;
  CertificateIdentity clientIdentity;

  @BeforeEach
  void setUp() throws Exception {
    var ca = CertificateIdentity.generateSelfSigned();

    // set up a truststore file for later
    truststore = Files.createTempFile("ca", ".p12");
    ca.saveAsPKCS12(truststore, "guest".toCharArray());

    // preload the trust store into a trust manager directly from the certificate
    KeyStore ks = StandardCrypto.getPKCS12KeyStore();
    ks.load(null, null);
    ks.setCertificateEntry(X509Identity.generateAlias(ca.getCertificate()), ca.getCertificate());
    X509TrustManager trustManager = TrustStores.loadTrustManager(ks);

    // both client and server keys will require signatures and key agreements for TLS usage
    var tlsKeyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement);

    // generate server keypair
    var serverKeyPair = TestCrypto.generateKeyPair();
    var serverPublicKey = serverKeyPair.getPublic();
    var serverPrivateKey = serverKeyPair.getPrivate();

    // set up extension requests for localhost (both a CN and a SAN DNS name)
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

    // prepare SSLContext for server
    var serverIdentitySource = X509IdentitySource.fromPEM(serverKeyFile, serverCertFile);
    var serverSSLContext =
        serverIdentitySource.refreshable(Duration.ofMinutes(15)).createSSLContext(trustManager);

    // set up HTTPS server
    int port = findAvailablePort();
    var address = new InetSocketAddress("localhost", port);
    server = HttpsServer.create(address, 0);

    // server settings for later use with OkHttpClient
    serviceEndpoint =
        new DefaultServiceEndpoint("server", String.format("https://localhost:%d/", port), true);
    testUrl =
        new HttpUrl.Builder()
            .scheme("https")
            .host("localhost")
            .port(port)
            .addPathSegment("test")
            .build();

    // set up and start HTTPS server with test endpoint
    server.setHttpsConfigurator(new HttpsConfigurator(serverSSLContext));
    server.createContext(
        "/test",
        exchange -> {
          var response = "It works!\n".getBytes(StandardCharsets.UTF_8);
          try (var out = exchange.getResponseBody()) {
            exchange.sendResponseHeaders(200, response.length);
            out.write(response);
          }
        });
    server.start();

    // generate client keypair
    var clientKeyPair = TestCrypto.generateKeyPair();
    var clientPublicKey = clientKeyPair.getPublic();
    var clientPrivateKey = clientKeyPair.getPrivate();

    // as a client certificate, we'll pretend to be spinnaker@localhost
    var clientName = new X500Principal("CN=spinnaker, O=spinnaker");
    var clientAlternativeName =
        new DERSequence(new GeneralName(GeneralName.rfc822Name, "spinnaker@localhost"));
    var clientExtensions = new ExtensionsGenerator();
    clientExtensions.addExtension(Extension.keyUsage, true, tlsKeyUsage);
    clientExtensions.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
    clientExtensions.addExtension(Extension.subjectAlternativeName, false, clientAlternativeName);

    // get certificate signed by CA
    var clientCertificationRequest =
        new JcaPKCS10CertificationRequestBuilder(clientName, clientPublicKey)
            .addAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, clientExtensions.generate())
            .build(CertificateIdentity.signerFrom(clientPrivateKey));
    clientIdentity =
        CertificateIdentity.fromCredentials(
            clientPrivateKey, ca.signCertificationRequest(clientCertificationRequest));
  }

  @AfterEach
  void tearDown() {
    server.stop(1);
  }

  @Test
  void smokeTest() throws Exception {
    // prepare client keystore
    Path keystore = Files.createTempFile("identity", ".p12");
    char[] password = TestCrypto.generatePassword(30);
    clientIdentity.saveAsPKCS12(keystore, password);

    new ApplicationContextRunner()
        // set up minimal properties to enable refreshable keys (and therefore
        // RefreshableOkHttpClientBuilderProvider as the highest priority
        // OkHttpClientBuilderProvider)
        .withPropertyValues(
            "ok-http-client.refreshable-keys.enabled=true",
            "ok-http-client.trust-store=" + truststore,
            "ok-http-client.trust-store-password=guest",
            "ok-http-client.key-store=" + keystore,
            "ok-http-client.key-store-password=" + new String(password))
        // set up minimal spring boot auto configs expected
        .withConfiguration(
            AutoConfigurations.of(
                TaskExecutionAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                MetricsAutoConfiguration.class,
                CompositeMeterRegistryAutoConfiguration.class))
        // set up minimal kork-web config classes
        .withUserConfiguration(TestConfig.class)
        .run(
            context -> {
              OkHttpClientBuilderProvider provider =
                  context
                      .getBeanProvider(OkHttpClientBuilderProvider.class)
                      .orderedStream()
                      .filter(p -> p.supports(serviceEndpoint))
                      .findFirst()
                      .orElseThrow(() -> new AssertionFailedError("No client provider found"));
              assertThat(provider).isInstanceOf(RefreshableOkHttpClientBuilderProvider.class);
              OkHttpClient client = provider.get(serviceEndpoint).build();
              Call call = client.newCall(new Request.Builder().get().url(testUrl).build());
              try (Response response = call.execute()) {
                ResponseBody body = response.body();
                assertThat(body).isNotNull();
                assertThat(body.string()).isEqualTo("It works!\n");
              }
            });
  }

  @Import(SpectatorConfiguration.class)
  @ComponentScan(
      basePackages = "com.netflix.spinnaker.config",
      excludeFilters =
          @ComponentScan.Filter(
              type = FilterType.ASSIGNABLE_TYPE,
              classes = MetricsEndpointConfiguration.class))
  static class TestConfig {}

  private static int findAvailablePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }
}
