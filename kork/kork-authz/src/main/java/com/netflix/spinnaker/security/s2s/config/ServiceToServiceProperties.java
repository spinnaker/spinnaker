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

package com.netflix.spinnaker.security.s2s.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Install-specific configuration for service-to-service authentication ({@code authz.s2s}).
 *
 * <p>Deliberately narrow: it selects <em>how</em> a caller's identity is read and how strictly to
 * act on it. It contains no trusted-caller list and no grants — which service may call which
 * endpoint is codified via {@link com.netflix.spinnaker.security.s2s.AllowServiceCallers} and
 * cannot be widened from configuration.
 *
 * <p>Disabled by default, so an upgrade is a no-op until an operator opts in.
 */
@Data
@ConfigurationProperties("authz.s2s")
public class ServiceToServiceProperties {

  /** How a peer's transport identity is read. */
  public enum Provider {
    /** No service-caller identity is read (default). */
    NONE,
    /** CA/mTLS: read the peer's X.509 client-certificate subject DN. */
    X509_SUBJECT,
    /** Service mesh / reverse proxy: read an injected header (Envoy XFCC by default). */
    HEADER,
    /** Kubernetes: verify a projected ServiceAccount token presented on a header. */
    K8S_SA_TOKEN
  }

  /**
   * Master switch. When false, no caller is resolved and no policy is enforced. When true, {@link
   * com.netflix.spinnaker.security.s2s.AllowServiceCallers} is always enforced (disallowed callers
   * are denied with 403) — there is no log-only mode.
   */
  private boolean enabled = false;

  /** The transport mechanism used to authenticate peers. */
  private Provider provider = Provider.NONE;

  /**
   * Optional deployment prefix stripped from a resolved name before matching the {@link
   * com.netflix.spinnaker.security.s2s.SpinnakerService} enum (e.g. {@code spin-} so a
   * ServiceAccount {@code spin-orca} maps to {@code ORCA}).
   */
  private String serviceNamePrefix = "spin-";

  /**
   * For {@link Provider#X509_SUBJECT}: a regex whose first capturing group extracts the service
   * name from the peer certificate's subject DN. Defaults to the Common Name.
   */
  private String subjectRegex = "CN=([^,]+)";

  /**
   * For {@link Provider#HEADER}: the header carrying the mesh-verified peer identity. Defaults to
   * Envoy's forwarded client certificate header, from which the {@code URI=} (SPIFFE ID) field is
   * read.
   */
  private String header = "X-Forwarded-Client-Cert";

  @NestedConfigurationProperty private K8s k8s = new K8s();

  /** Options for {@link Provider#K8S_SA_TOKEN}. */
  @Data
  public static class K8s {
    /**
     * The header on which callers present their projected ServiceAccount token.
     *
     * <p>Deliberately outside the {@code X-SPINNAKER-*} namespace: {@code
     * AuthenticatedRequestFilter} copies every inbound {@code X-SPINNAKER-*} header into the MDC,
     * from where it is written to logs and re-attached to this service's own outbound requests by
     * {@code SpinnakerRequestHeaderInterceptor}. Naming this header {@code X-SPINNAKER-*} would
     * therefore both log a bearer credential in plaintext and forward the caller's identity onto
     * unrelated downstream hops, defeating the per-hop model described on {@code
     * ServiceCallerContext}. Override with care for the same reason.
     */
    private String tokenHeader = "X-Service-Identity-Token";

    /**
     * Path to this pod's audience-bound projected ServiceAccount token, used on the
     * <em>calling</em> side to authenticate outbound requests to other Spinnaker services. Must be
     * a dedicated projected volume whose {@code audience} matches {@link #audiences} — the default
     * {@code /var/run/secrets/kubernetes.io/serviceaccount/token} is bound to the Kubernetes API
     * server's audience and is deliberately <em>not</em> used here.
     *
     * <p>Absent file means the caller simply sends no token (and will be denied by peers that
     * require one), rather than failing at startup, so this stays safe to deploy ahead of the
     * volume being mounted.
     */
    private String tokenPath = "/var/run/secrets/spinnaker/identity/token";

    /**
     * How long a token read from {@link #tokenPath} is reused before re-reading the file.
     * Kubernetes rotates projected tokens in place well before expiry, so the file must be re-read
     * periodically; caching for the process lifetime would eventually send an expired token.
     */
    private int tokenRefreshSeconds = 60;

    /**
     * The namespace the calling ServiceAccounts live in; the token subject {@code
     * system:serviceaccount:<namespace>:<name>} must match, so a same-named SA in another namespace
     * cannot impersonate a Spinnaker service.
     */
    private String namespace = "spinnaker";

    /**
     * Expected audience ({@code aud}) — the audience the projected token is bound to (e.g. {@code
     * spinnaker}). <b>Required</b> when this provider is selected: without it the resolver is
     * disabled, so a token minted for another audience (e.g. the Kubernetes API server) cannot be
     * replayed against Spinnaker.
     */
    private List<String> audiences = new ArrayList<>();

    /**
     * JWKS URI whose public keys verify presented tokens. Defaults to the in-cluster API server,
     * which every pod can reach and which every ServiceAccount is already authorized to read: the
     * cluster ships a {@code system:service-account-issuer-discovery} ClusterRoleBinding for the
     * {@code system:serviceaccounts} group, so this needs no RBAC of its own.
     */
    private String jwksUri = "https://kubernetes.default.svc/openid/v1/jwks";

    /**
     * PEM CA bundle used to verify the JWKS endpoint's TLS certificate. The API server presents a
     * certificate signed by the cluster's own CA, which is <em>not</em> in the JVM's default
     * truststore, so without this every fetch fails the handshake and no caller is ever resolved.
     * Defaults to the bundle every pod already has projected.
     *
     * <p>Set to empty to use the JVM's default trust store, appropriate when {@link #jwksUri}
     * points at a publicly-trusted mirror of the cluster's discovery documents rather than the API
     * server.
     */
    private String jwksCaCertPath = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";

    /**
     * Path to this pod's own API-server-audience token, presented as a bearer credential when
     * fetching the JWKS. Required whenever the API server runs with {@code --anonymous-auth=false}
     * (it answers unauthenticated discovery requests with 401). This is the default projected token
     * — distinct from {@link #tokenPath}, which is the Spinnaker-audience token this service
     * presents to peers.
     *
     * <p>Set to empty to fetch anonymously, appropriate for a public mirror.
     */
    private String jwksTokenPath = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    /**
     * Expected token issuer ({@code iss}); when set, tokens with a different issuer are rejected.
     * Left unset by default because a cluster's issuer is frequently not its in-cluster API server
     * URL, so guessing it here would reject every valid token.
     */
    private String issuer;

    /**
     * Accepted JWS signature algorithm(s) for projected ServiceAccount tokens. Almost all clusters
     * mint RS256 tokens (the default); some are configured for ES256. Each entry is parsed with
     * {@code JWSAlgorithm.parse}; blanks are ignored and an empty list falls back to {@code RS256}.
     * A token signed with an algorithm not listed here is rejected.
     */
    private List<String> jwsAlgorithms = new ArrayList<>(List.of("RS256"));

    /**
     * Connect timeout, in milliseconds, for fetching the JWKS. Nimbus defaults to no timeout, which
     * would let an unreachable JWKS endpoint stall the request threads that {@code resolve} runs
     * on; a bounded default avoids that. {@code 0} means no timeout.
     */
    private int jwksConnectTimeoutMs = 2_000;

    /**
     * Read (socket) timeout, in milliseconds, for fetching the JWKS. As with the connect timeout,
     * Nimbus defaults to no timeout; a bounded default keeps a slow endpoint from stalling
     * resolution. {@code 0} means no timeout.
     */
    private int jwksReadTimeoutMs = 2_000;

    /**
     * Maximum accepted JWKS response size, in bytes; guards against a huge or hostile response
     * exhausting memory. {@code 0} means no limit.
     */
    private int jwksSizeLimitBytes = 51_200;
  }
}
