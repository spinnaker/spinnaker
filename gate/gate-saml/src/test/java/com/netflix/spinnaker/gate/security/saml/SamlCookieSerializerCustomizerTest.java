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

package com.netflix.spinnaker.gate.security.saml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Asserts the actual emitted {@code Set-Cookie} header, rather than an internal field, for {@link
 * SAMLConfiguration.WebSecurityConfig#defaultCookieSerializerCustomizer}.
 *
 * <p>The default must keep omitting {@code SameSite} (long-standing behaviour that existing SAML
 * deployments depend on), while an explicitly configured {@code
 * server.servlet.session.cookie.same-site} must now win — Spring Boot applies that property
 * <em>before</em> customizer beans, so a hard-coded value here would silently override it.
 */
class SamlCookieSerializerCustomizerTest {

  @Test
  @DisplayName("by default SameSite is omitted entirely")
  void defaultOmitsSameSite() {
    assertThat(setCookieHeaderFor(new ServerProperties())).doesNotContain("SameSite");
  }

  @Test
  @DisplayName("an explicit same-site: none is honoured, for a cross-site IdP")
  void explicitNoneIsHonoured() {
    assertThat(setCookieHeaderFor(serverPropertiesWithSameSite(Cookie.SameSite.NONE)))
        .contains("SameSite=None");
  }

  @Test
  @DisplayName("an explicit same-site: lax is honoured")
  void explicitLaxIsHonoured() {
    assertThat(setCookieHeaderFor(serverPropertiesWithSameSite(Cookie.SameSite.LAX)))
        .contains("SameSite=Lax");
  }

  @Test
  @DisplayName("an explicit same-site: omitted still omits the attribute")
  void explicitOmittedOmitsSameSite() {
    assertThat(setCookieHeaderFor(serverPropertiesWithSameSite(Cookie.SameSite.OMITTED)))
        .doesNotContain("SameSite");
  }

  private static ServerProperties serverPropertiesWithSameSite(Cookie.SameSite sameSite) {
    ServerProperties serverProperties = new ServerProperties();
    serverProperties.getServlet().getSession().getCookie().setSameSite(sameSite);
    return serverProperties;
  }

  /** Runs the customizer over a real serializer and returns the header it writes. */
  private static String setCookieHeaderFor(ServerProperties serverProperties) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    SAMLConfiguration.WebSecurityConfig.defaultCookieSerializerCustomizer(serverProperties)
        .customize(serializer);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    serializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, "session-id"));

    return response.getHeader("Set-Cookie");
  }
}
