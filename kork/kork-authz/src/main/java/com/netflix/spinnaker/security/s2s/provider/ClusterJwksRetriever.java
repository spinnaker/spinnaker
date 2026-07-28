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

import com.netflix.spinnaker.kork.crypto.TrustStores;
import com.netflix.spinnaker.security.s2s.client.ProjectedServiceAccountTokenSource;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Fetches the cluster's JWKS from the Kubernetes API server, which needs two things a stock {@link
 * DefaultResourceRetriever} does not do.
 *
 * <p><b>Trust.</b> The API server presents a certificate signed by the cluster's own CA, which is
 * not in the JVM's default truststore, so an ordinary fetch fails the TLS handshake and — because
 * verification then fails for every caller — denies all service-to-service traffic. The CA is read
 * from the bundle projected into every pod.
 *
 * <p><b>Authentication.</b> Clusters running {@code --anonymous-auth=false} answer unauthenticated
 * discovery requests with {@code 401}, so each fetch carries this pod's own ServiceAccount token as
 * a bearer credential. No extra RBAC is needed for this: Kubernetes ships a {@code
 * system:service-account-issuer-discovery} ClusterRoleBinding for the {@code
 * system:serviceaccounts} group, so every ServiceAccount may already read the JWKS.
 *
 * <p>The token is read per fetch rather than captured once, because kubelet rewrites projected
 * tokens in place as they approach expiry and {@link com.nimbusds.jose.jwk.source.RemoteJWKSet}
 * refreshes long after startup.
 */
class ClusterJwksRetriever extends DefaultResourceRetriever {

  private final ProjectedServiceAccountTokenSource credential;

  ClusterJwksRetriever(
      int connectTimeoutMs,
      int readTimeoutMs,
      int sizeLimitBytes,
      SSLSocketFactory sslSocketFactory,
      ProjectedServiceAccountTokenSource credential) {
    super(connectTimeoutMs, readTimeoutMs, sizeLimitBytes, true, sslSocketFactory);
    this.credential = credential;
  }

  /**
   * Builds a retriever for an in-cluster API server. A blank {@code caCertPath} keeps the JVM's
   * default trust store and a blank {@code tokenPath} fetches anonymously, which together suit a
   * publicly-trusted mirror of the discovery documents rather than the API server itself.
   */
  static ClusterJwksRetriever build(
      String caCertPath,
      String tokenPath,
      Duration tokenRefresh,
      int connectTimeoutMs,
      int readTimeoutMs,
      int sizeLimitBytes)
      throws GeneralSecurityException, IOException {
    return new ClusterJwksRetriever(
        connectTimeoutMs,
        readTimeoutMs,
        sizeLimitBytes,
        caCertPath == null || caCertPath.isBlank() ? null : socketFactoryTrusting(caCertPath),
        tokenPath == null || tokenPath.isBlank()
            ? ProjectedServiceAccountTokenSource.disabled()
            : new ProjectedServiceAccountTokenSource(Path.of(tokenPath), tokenRefresh));
  }

  private static SSLSocketFactory socketFactoryTrusting(String caCertPath)
      throws GeneralSecurityException, IOException {
    X509TrustManager trustManager =
        TrustStores.loadTrustManager(TrustStores.loadPEM(Path.of(caCertPath)));
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, new TrustManager[] {trustManager}, null);
    return sslContext.getSocketFactory();
  }

  @Override
  protected HttpURLConnection openConnection(URL url) throws IOException {
    HttpURLConnection connection = super.openConnection(url);
    // Only ever send the credential over TLS, so a misconfigured plaintext jwks-uri cannot leak it.
    if (connection instanceof HttpsURLConnection) {
      credential
          .get()
          .ifPresent(token -> connection.setRequestProperty("Authorization", "Bearer " + token));
    }
    return connection;
  }
}
