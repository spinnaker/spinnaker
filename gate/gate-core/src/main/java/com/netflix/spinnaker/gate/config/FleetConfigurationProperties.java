/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.gate.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for running this Gate as one instance of a <em>fleet</em> of Spinnaker instances
 * sitting behind a single global URL.
 *
 * <pre>
 * fleet:
 *   enabled: true
 *   global-base-url: https://spinnaker.example.com
 *   instance-id: inst-1
 * </pre>
 *
 * <p>Disabled by default; an operator must explicitly opt in. When enabled, {@code
 * FleetDirectAccessFilter} redirects session-authenticated non-admins who reach this instance's own
 * hostname directly back to {@link #globalBaseUrl}, so ordinary users only ever see the global URL.
 *
 * <p>This is URL/routing hygiene, <strong>not</strong> a security boundary — see {@code
 * gate/docs/fleet.md}. Fiat remains the authorization boundary for every request.
 */
@Data
@ConfigurationProperties("fleet")
public class FleetConfigurationProperties {

  /** Master switch — no fleet beans are registered unless this is {@code true}. */
  private boolean enabled = false;

  /**
   * The fleet's single user-facing base URL (scheme + host [+ port]), e.g. {@code
   * https://spinnaker.example.com}. Requests whose effective host matches this are treated as
   * having arrived through the fleet edge; everything else is a direct-to-instance hit. Also the
   * redirect target for non-admins. Required when {@link #enabled} is true.
   */
  private String globalBaseUrl;

  /**
   * This instance's fleet identity, e.g. {@code inst-1}. Used only for logging and for Deck's
   * {@code fleet.instanceId} setting; routing is driven entirely by the per-instance session cookie
   * name at the edge.
   */
  private String instanceId;

  /**
   * Request paths exempt from the direct-access guardrail, in Ant syntax.
   *
   * <p>The default is deliberately derived from the {@code permitAll} set already configured in
   * {@link AuthConfig#configure}, plus the SAML endpoints: every path here is <em>already</em>
   * reachable without authentication, so exempting it grants a non-admin nothing new. The SAML
   * exemptions matter because a per-instance Assertion Consumer Service means the IdP POSTs the
   * assertion directly to this instance's hostname — without them, a non-admin could never complete
   * login.
   *
   * <p>Note that the configured {@code saml.login-processing-url} is always exempt in addition to
   * this list, so customising it does not require editing these defaults.
   */
  private List<String> exemptPaths =
      List.of(
          "/error",
          "/favicon.ico",
          "/health",
          "/health/**",
          "/auth/user",
          "/auth/loggedOut",
          "/auth/logout",
          "/plugins/deck/**",
          "/webhooks/**",
          "/notifications/callbacks/**",
          "/managed/notifications/callbacks/**",
          "/saml/**",
          "/saml2/**",
          "/login/saml2/**");
}
