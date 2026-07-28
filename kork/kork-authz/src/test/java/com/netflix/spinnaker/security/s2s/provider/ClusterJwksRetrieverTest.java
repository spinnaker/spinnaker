/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.kork.crypto.test.CertificateIdentity;
import com.netflix.spinnaker.kork.crypto.test.TestCrypto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end guard for the two things a stock resource retriever gets wrong against an in-cluster
 * Kubernetes API server. Each failure denies every service-to-service call with an identical,
 * uninformative "no authenticated caller" 403, so both are worth pinning down:
 *
 * <ul>
 *   <li>the API server's certificate is signed by the cluster's own CA, which the JVM does not
 *       trust by default, so the fetch must be given that CA;
 *   <li>clusters running {@code --anonymous-auth=false} answer unauthenticated discovery requests
 *       with {@code 401}, so the fetch must present this pod's own ServiceAccount token.
 * </ul>
 *
 * <p>These are exercised against a real loopback HTTPS server rather than by inspecting a prepared
 * connection, because {@code HttpsURLConnectionImpl} does not report its own request properties
 * back before connecting — the only trustworthy observation is what the server actually receives.
 */
class ClusterJwksRetrieverTest {

  private static final String JWKS_BODY = "{\"keys\":[]}";

  @TempDir Path tempDir;

  private CertificateIdentity clusterCa;
  private Path caCertPath;
  private Path tokenPath;
  private HttpsServer server;
  private HttpServer plaintextServer;
  private final AtomicReference<String> observedAuthorization = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    clusterCa = CertificateIdentity.generateSelfSigned();
    caCertPath = tempDir.resolve("ca.crt");
    clusterCa.saveAsPEM(tempDir.resolve("ca.key"), caCertPath);

    tokenPath = tempDir.resolve("token");
    Files.writeString(tokenPath, "pod-credential");

    server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(serverSslContext()));
    server.createContext("/openid/v1/jwks", jwksHandler());
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
    if (plaintextServer != null) {
      plaintextServer.stop(0);
    }
  }

  @Test
  void fetchesTheJwksWhenGivenTheClusterCa() throws Exception {
    String content =
        retriever(caCertPath.toString(), tokenPath.toString())
            .retrieveResource(jwksUrl())
            .getContent();

    assertThat(content).isEqualTo(JWKS_BODY);
  }

  /**
   * The regression this class exists for: without the cluster CA the handshake fails, which is what
   * silently denied every caller in production.
   */
  @Test
  void failsTheHandshakeWithoutTheClusterCa() {
    assertThatThrownBy(() -> retriever("", tokenPath.toString()).retrieveResource(jwksUrl()))
        .isInstanceOf(SSLHandshakeException.class);
  }

  @Test
  void authenticatesTheFetchWithThePodsOwnToken() throws Exception {
    retriever(caCertPath.toString(), tokenPath.toString()).retrieveResource(jwksUrl());

    assertThat(observedAuthorization.get()).isEqualTo("Bearer pod-credential");
  }

  @Test
  void picksUpARotatedTokenOnALaterFetch() throws Exception {
    // Duration.ZERO so the source re-reads on every call, standing in for the refresh interval
    // elapsing between two of RemoteJWKSet's periodic refreshes.
    ClusterJwksRetriever retriever =
        ClusterJwksRetriever.build(
            caCertPath.toString(), tokenPath.toString(), Duration.ZERO, 2_000, 2_000, 51_200);

    retriever.retrieveResource(jwksUrl());
    assertThat(observedAuthorization.get()).isEqualTo("Bearer pod-credential");

    Files.writeString(tokenPath, "rotated-credential");
    retriever.retrieveResource(jwksUrl());

    assertThat(observedAuthorization.get()).isEqualTo("Bearer rotated-credential");
  }

  @Test
  void fetchesAnonymouslyWhenNoTokenIsConfigured() throws Exception {
    retriever(caCertPath.toString(), "").retrieveResource(jwksUrl());

    assertThat(observedAuthorization.get()).isNull();
  }

  /**
   * The token is a bearer credential, so a mistyped plaintext {@code jwks-uri} must not leak it.
   */
  @Test
  void neverSendsTheCredentialOverPlaintext() throws Exception {
    plaintextServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    plaintextServer.createContext("/openid/v1/jwks", jwksHandler());
    plaintextServer.start();
    URL plaintextUrl =
        new URL("http://localhost:" + plaintextServer.getAddress().getPort() + "/openid/v1/jwks");

    retriever(caCertPath.toString(), tokenPath.toString()).retrieveResource(plaintextUrl);

    assertThat(observedAuthorization.get()).isNull();
  }

  /**
   * A CA path that cannot be read is an operator error that would otherwise surface only as every
   * caller being denied, so it fails while the resolver is being built instead.
   */
  @Test
  void refusesToBuildWithAnUnreadableCaBundle() {
    String missing = tempDir.resolve("absent.crt").toString();

    assertThatThrownBy(() -> retriever(missing, tokenPath.toString()))
        .isInstanceOf(IOException.class);
  }

  private ClusterJwksRetriever retriever(String caPath, String tokenFile) throws Exception {
    return ClusterJwksRetriever.build(
        caPath, tokenFile, Duration.ofSeconds(60), 2_000, 2_000, 51_200);
  }

  private URL jwksUrl() throws IOException {
    return new URL("https://localhost:" + server.getAddress().getPort() + "/openid/v1/jwks");
  }

  private HttpHandler jwksHandler() {
    return (HttpExchange exchange) -> {
      observedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] body = JWKS_BODY.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    };
  }

  /** A server identity for {@code localhost}, signed by the stand-in cluster CA. */
  private SSLContext serverSslContext() throws Exception {
    KeyPair keyPair = TestCrypto.generateKeyPair();
    ExtensionsGenerator extensions = new ExtensionsGenerator();
    extensions.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
    extensions.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    extensions.addExtension(
        Extension.subjectAlternativeName,
        false,
        new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
    PKCS10CertificationRequest csr =
        new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=localhost"), keyPair.getPublic())
            .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate())
            .build(CertificateIdentity.signerFrom(keyPair.getPrivate()));
    X509Certificate certificate = clusterCa.signCertificationRequest(csr);

    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keyManagers(keyPair.getPrivate(), certificate), null, null);
    return context;
  }

  private KeyManager[] keyManagers(PrivateKey privateKey, X509Certificate certificate)
      throws Exception {
    char[] password = TestCrypto.generatePassword(16);
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setKeyEntry(
        "identity",
        privateKey,
        password,
        new Certificate[] {certificate, clusterCa.getCertificate()});
    KeyManagerFactory factory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore, password);
    return factory.getKeyManagers();
  }
}
