/*
 * Copyright 2026 Mcintosh.farm
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

package com.netflix.spinnaker.gate;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for SAML configuration using a Keycloak testcontainer.
 *
 * <p>SAML connection properties (IdP metadata URI, entity ID, signing credentials) are configured
 * via {@code spring.security.saml2.relyingparty.registration.*}. The IdP metadata URI is injected
 * dynamically via {@link AbstractSAMLConfigurationIntegrationTest#configureProperties} once the
 * Keycloak container has started.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "saml.enabled=true",
      "saml.login-processing-url=/saml/SSO",
      "spring.security.saml2.relyingparty.registration.SSO.acs.location={baseUrl}/saml/{registrationId}",
      // Entity ID for this relying party — must match the clientId registered in Keycloak.
      "spring.security.saml2.relyingparty.registration.SSO.entity-id=spinnaker-test",
      // Sign AuthnRequests — Keycloak requires a signed request even when it does not validate it.
      "spring.security.saml2.relyingparty.registration.SSO.assertingparty.singlesignon.sign-request=true",
      "spring.security.saml2.relyingparty.registration.SSO.signing.credentials[0].private-key-location=classpath:private_key.pem",
      "spring.security.saml2.relyingparty.registration.SSO.signing.credentials[0].certificate-location=classpath:certificate.pem",
      "management.endpoints.web.exposure.include=beans"
    },
    classes = Main.class)
class SAMLConfigurationIntegrationTest extends AbstractSAMLConfigurationIntegrationTest {

  @Test
  void contextLoads() {
    assertSamlPropertiesLoaded();
  }

  @Test
  void testRedirectAndWiringWorks() throws Exception {
    assertSamlRedirectFlowWorks();
  }

  @Test
  void testFullSamlAuthenticationFlowUsingABrowser() throws Exception {
    assertFullSamlAuthenticationFlowUsingABrowserWorks();
  }
}
