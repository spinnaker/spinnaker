/*
 * Copyright 2026 Netflix, Inc.
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

import com.netflix.spinnaker.kork.crypto.test.CertificateIdentity;
import com.netflix.spinnaker.kork.crypto.test.TestCrypto;
import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.SpinnakerService;
import com.netflix.spinnaker.security.s2s.SpinnakerServiceMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * End-to-end guard for the trust boundary {@link X509SubjectServiceCallerResolver} depends on.
 *
 * <p>The resolver itself does no crypto; it trusts that the servlet container only exposes the
 * {@code jakarta.servlet.request.X509Certificate} attribute for a certificate that passed the mTLS
 * handshake. This test proves that assumption holds under the {@code want} client-auth mode
 * recommended for Gate's mixed traffic, so that a self-signed / untrusted client certificate cannot
 * be used to spoof a service identity:
 *
 * <ul>
 *   <li>a client cert chaining to the configured truststore completes the handshake and is exposed
 *       as the peer cert (which the resolver then maps to a {@link SpinnakerService});
 *   <li>an untrusted (different-CA) client cert aborts the handshake, so it never becomes a peer
 *       cert and the resolver is never given a chance to attribute it;
 *   <li>a client that presents no cert still connects (that is what {@code want} means) and the
 *       resolver returns {@link Optional#empty()}, deferring to the next resolver / anonymous path.
 * </ul>
 *
 * <p>This exercises JSSE's {@code X509TrustManager} directly rather than a full embedded container,
 * but the trust decision under test is the same one every JSSE-backed container (Tomcat/Jetty)
 * delegates to.
 */
class X509SubjectServiceCallerResolverMtlsIntegrationTest {

  private static final char[] KEYSTORE_PASSWORD = "changeit".toCharArray();

  private final X509SubjectServiceCallerResolver resolver =
      new X509SubjectServiceCallerResolver(
          Pattern.compile("CN=([^,]+)"), new SpinnakerServiceMapper("spin-"));

  private ExecutorService serverExecutor;
  private CertificateIdentity trustedCa;
  private CertificateIdentity untrustedCa;
  private Leaf serverCert;

  @BeforeEach
  void setUp() throws Exception {
    serverExecutor = Executors.newSingleThreadExecutor();
    trustedCa = CertificateIdentity.generateSelfSigned();
    untrustedCa = CertificateIdentity.generateSelfSigned();
    serverCert = signLeaf(trustedCa, "gate", KeyPurposeId.id_kp_serverAuth);
  }

  @AfterEach
  void tearDown() {
    serverExecutor.shutdownNow();
  }

  @Test
  void trustedClientCertIsExposedAndResolvesToService() throws Exception {
    Leaf client = signLeaf(trustedCa, "orca", KeyPurposeId.id_kp_clientAuth);

    HandshakeResult result = handshake(client);

    assertThat(result.failed()).isFalse();
    assertThat(result.peerCertificates()).isNotNull();

    Optional<ServiceCaller> caller = resolver.resolve(requestWith(result.peerCertificates()));
    assertThat(caller).map(ServiceCaller::service).contains(SpinnakerService.ORCA);
  }

  @Test
  void untrustedClientCertAbortsHandshakeAndNeverReachesResolver() throws Exception {
    // A cert whose CN would otherwise map to a real service, but signed by a CA the server does not
    // trust: it must be rejected at the transport layer, never surfacing as a peer certificate.
    Leaf spoofer = signLeaf(untrustedCa, "orca", KeyPurposeId.id_kp_clientAuth);

    HandshakeResult result = handshake(spoofer);

    assertThat(result.failed()).isTrue();
    assertThat(result.peerCertificates()).isNull();

    // With no verified peer cert, the resolver has nothing to attribute.
    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }

  @Test
  void clientWithNoCertConnectsButResolvesToEmpty() throws Exception {
    HandshakeResult result = handshake(null);

    // want mode: absence of a client cert is not a handshake failure.
    assertThat(result.failed()).isFalse();
    assertThat(result.peerCertificates()).isNull();

    assertThat(resolver.resolve(new MockHttpServletRequest())).isEmpty();
  }

  /**
   * Runs a single mTLS handshake against a loopback {@link SSLServerSocket} configured with {@code
   * setWantClientAuth(true)} and a truststore containing only {@link #trustedCa}, using {@code
   * clientCert} (or none when null) as the client identity. Returns what the server observed.
   */
  private HandshakeResult handshake(Leaf clientCert) throws Exception {
    SSLContext serverContext = serverContext();
    try (SSLServerSocket serverSocket =
        (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(0)) {
      serverSocket.setWantClientAuth(true);
      int port = serverSocket.getLocalPort();

      Future<HandshakeResult> serverSide = serverExecutor.submit(acceptAndReadPeer(serverSocket));

      SSLContext clientContext = clientContext(clientCert);
      boolean clientFailed = false;
      try (SSLSocket socket =
          (SSLSocket) clientContext.getSocketFactory().createSocket("localhost", port)) {
        socket.startHandshake();
        OutputStream out = socket.getOutputStream();
        out.write(42);
        out.flush();
      } catch (IOException e) {
        clientFailed = true;
      }

      HandshakeResult serverResult = serverSide.get(15, TimeUnit.SECONDS);
      return new HandshakeResult(
          serverResult.peerCertificates(), serverResult.failed() || clientFailed);
    }
  }

  private static Callable<HandshakeResult> acceptAndReadPeer(SSLServerSocket serverSocket) {
    return () -> {
      try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
        InputStream in = socket.getInputStream();
        in.read(); // force the handshake to complete before we inspect the session
        try {
          X509Certificate[] chain = toX509(socket.getSession().getPeerCertificates());
          return new HandshakeResult(chain, false);
        } catch (SSLPeerUnverifiedException unverified) {
          return new HandshakeResult(null, false);
        }
      } catch (IOException handshakeFailed) {
        return new HandshakeResult(null, true);
      }
    };
  }

  private SSLContext serverContext() throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(
        keyManagers(serverCert),
        new TrustManager[] {pkixTrustManagerFor(trustedCa.getCertificate())},
        null);
    return context;
  }

  private SSLContext clientContext(Leaf clientCert) throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(
        clientCert == null ? null : keyManagers(clientCert), new TrustManager[] {TRUST_ALL}, null);
    return context;
  }

  private KeyManager[] keyManagers(Leaf leaf) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, null);
    keyStore.setKeyEntry(
        "identity",
        leaf.privateKey(),
        KEYSTORE_PASSWORD,
        new Certificate[] {leaf.certificate(), leaf.issuer()});
    KeyManagerFactory factory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(keyStore, KEYSTORE_PASSWORD);
    return factory.getKeyManagers();
  }

  private static X509TrustManager pkixTrustManagerFor(X509Certificate anchor) throws Exception {
    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    trustStore.setCertificateEntry("anchor", anchor);
    TrustManagerFactory factory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(trustStore);
    for (TrustManager trustManager : factory.getTrustManagers()) {
      if (trustManager instanceof X509TrustManager x509) {
        return x509;
      }
    }
    throw new IllegalStateException("No X509TrustManager available");
  }

  private Leaf signLeaf(CertificateIdentity issuer, String commonName, KeyPurposeId eku)
      throws Exception {
    KeyPair keyPair = TestCrypto.generateKeyPair();
    ExtensionsGenerator extensions = new ExtensionsGenerator();
    extensions.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
    extensions.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(eku));
    PKCS10CertificationRequest csr =
        new JcaPKCS10CertificationRequestBuilder(
                new X500Name("CN=" + commonName), keyPair.getPublic())
            .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensions.generate())
            .build(CertificateIdentity.signerFrom(keyPair.getPrivate()));
    X509Certificate certificate = issuer.signCertificationRequest(csr);
    return new Leaf(keyPair.getPrivate(), certificate, issuer.getCertificate());
  }

  private static HttpServletRequest requestWith(X509Certificate[] chain) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute("jakarta.servlet.request.X509Certificate", chain);
    return request;
  }

  private static X509Certificate[] toX509(Certificate[] certificates) {
    X509Certificate[] chain = new X509Certificate[certificates.length];
    for (int i = 0; i < certificates.length; i++) {
      chain[i] = (X509Certificate) certificates[i];
    }
    return chain;
  }

  private static final X509TrustManager TRUST_ALL =
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };

  private record Leaf(PrivateKey privateKey, X509Certificate certificate, X509Certificate issuer) {}

  private record HandshakeResult(X509Certificate[] peerCertificates, boolean failed) {}
}
