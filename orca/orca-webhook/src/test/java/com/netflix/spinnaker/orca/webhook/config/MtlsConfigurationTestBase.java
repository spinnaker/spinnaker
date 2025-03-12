/*
 * Copyright 2024 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.webhook.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.kork.crypto.StandardCrypto;
import com.netflix.spinnaker.kork.crypto.StaticX509Identity;
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Duration;
import java.util.Date;
import javax.net.ssl.X509TrustManager;
import lombok.SneakyThrows;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class MtlsConfigurationTestBase {

  static final String password = "password";

  // Tempfiles to store our keystores and PEM files
  static File caStoreFile;
  static File caPemFile;

  static File clientIdentityStoreFile;
  static File clientIdentityKeyPemFile;
  static File clientIdentityCertPemFile;

  static MockWebServer mockWebServer;
  static final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

  static class TestConfigurationBase {
    @Bean
    UserConfiguredUrlRestrictions userConfiguredUrlRestrictions() {
      return new UserConfiguredUrlRestrictions.Builder().withRejectLocalhost(false).build();
    }

    @Bean
    HttpLoggingInterceptor.Level logLevel() {
      return HttpLoggingInterceptor.Level.NONE;
    }

    @Bean
    TaskExecutorBuilder taskExecutorBuilder() {
      return new TaskExecutorBuilder();
    }

    @Bean
    ObjectMapper objectMapper() {
      return mapper;
    }

    @MockBean FiatService fiatService;
  }

  @SneakyThrows
  private static KeyPair createKeyPair() {
    // Create test keypair
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
    return generator.generateKeyPair();
  }

  @SneakyThrows
  private static X509Certificate createCertificate(
      X500Name subject, PrivateKey privateKey, PublicKey publicKey, boolean isCa) {
    // Create certificate
    var issuer = new X500Name("CN=ca");
    var serial = BigInteger.valueOf(System.currentTimeMillis());
    var notBefore = new Date();
    var notAfter = Date.from(notBefore.toInstant().plus(Duration.ofDays(1)));
    var certificateHolderBuilder =
        new JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, publicKey);

    if (isCa) {
      certificateHolderBuilder
          .addExtension(
              Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))
          .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    } else {
      certificateHolderBuilder
          .addExtension(
              Extension.keyUsage,
              false,
              new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment))
          .addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
    }

    var certificateHolder =
        certificateHolderBuilder.build(
            new JcaContentSignerBuilder("SHA1withRSA").build(privateKey));
    return (X509Certificate)
        StandardCrypto.getX509CertificateFactory()
            .generateCertificate(new ByteArrayInputStream(certificateHolder.getEncoded()));
  }

  @SneakyThrows
  private static void writeKeyPem(PrivateKey privateKey, File file) {
    var pemWriter = new JcaPEMWriter(new FileWriter(file));
    pemWriter.writeObject(new JcaPKCS8Generator(privateKey, null));
    pemWriter.close();
  }

  @SneakyThrows
  private static void writeCertPem(X509Certificate[] certs, File file) {
    var pemWriter = new JcaPEMWriter(new FileWriter(file));
    for (var cert : certs) {
      pemWriter.writeObject(cert);
    }
    pemWriter.close();
  }

  @SneakyThrows
  private static KeyStore writeTrustStore(Certificate certificate, File file) {
    var store = StandardCrypto.getPKCS12KeyStore();
    store.load(null);
    store.setCertificateEntry("ca", certificate);
    store.store(Files.newOutputStream(file.toPath()), password.toCharArray());
    return store;
  }

  @SneakyThrows
  private static KeyStore writeIdentityStore(
      PrivateKey privateKey, X509Certificate[] certificateChain, File file) {
    var store = StandardCrypto.getPKCS12KeyStore();
    store.load(null);
    store.setKeyEntry("identity", privateKey, password.toCharArray(), certificateChain);
    store.store(Files.newOutputStream(file.toPath()), password.toCharArray());
    return store;
  }

  @BeforeAll
  @SneakyThrows
  public static void beforeAll() throws IOException {
    // Create tempfiles
    caStoreFile = File.createTempFile("testca", "");
    caPemFile = File.createTempFile("testcapem", "");

    clientIdentityStoreFile = File.createTempFile("testid", "");
    clientIdentityKeyPemFile = File.createTempFile("testidpemkey", "");
    clientIdentityCertPemFile = File.createTempFile("testidcertkey", "");

    // Invent a CA that will sign our client certificate, and will be trusted by the server
    var caKeyPair = createKeyPair();
    var caCert =
        createCertificate(
            new X500Name("CN=ca"), caKeyPair.getPrivate(), caKeyPair.getPublic(), true);

    var caStore = writeTrustStore(caCert, caStoreFile);
    writeCertPem(new X509Certificate[] {caCert}, caPemFile);

    // Create a client identity signed by our invented CA
    var clientIdentityKeyPair = createKeyPair();
    var clientIdentityCert =
        createCertificate(
            new X500Name("CN=client"),
            caKeyPair.getPrivate(),
            clientIdentityKeyPair.getPublic(),
            false);
    var clientIdentityCertChain = new X509Certificate[] {clientIdentityCert, caCert};

    writeIdentityStore(
        clientIdentityKeyPair.getPrivate(), clientIdentityCertChain, clientIdentityStoreFile);
    writeKeyPem(clientIdentityKeyPair.getPrivate(), clientIdentityKeyPemFile);
    writeCertPem(clientIdentityCertChain, clientIdentityCertPemFile);

    // Create a server identity signed by our invented CA
    var serverIdentityKeyPair = createKeyPair();
    var serverIdentityCert =
        createCertificate(
            new X500Name("CN=server"),
            caKeyPair.getPrivate(),
            serverIdentityKeyPair.getPublic(),
            false);
    var serverIdentityCertChain = new X509Certificate[] {serverIdentityCert, caCert};

    // Set server trust to our CA we just made
    var serverTrustManagerFactory = StandardCrypto.getPKIXTrustManagerFactory();
    serverTrustManagerFactory.init(caStore);
    var serverTrustManager = (X509TrustManager) serverTrustManagerFactory.getTrustManagers()[0];

    // Set the server identity to the server identity we just made
    var serverIdentity =
        new StaticX509Identity(serverIdentityKeyPair.getPrivate(), serverIdentityCertChain);
    var serverSocketFactory =
        serverIdentity.createSSLContext(serverTrustManager).getSocketFactory();

    // Configure MockWebServer
    mockWebServer = new MockWebServer();
    mockWebServer.useHttps(serverSocketFactory, false);
    mockWebServer.requireClientAuth();
    mockWebServer.enqueue(new MockResponse().setBody("{ \"mtls\": \"yep\" }"));
    mockWebServer.start();
  }

  @AfterAll
  @SneakyThrows
  public static void afterAll() {
    mockWebServer.shutdown();
  }
}
