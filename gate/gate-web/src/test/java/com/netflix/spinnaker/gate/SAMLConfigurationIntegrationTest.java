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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "saml.enabled=true",
      "saml.issuer-id=spinnaker-test", // MUST match client-id in keycloak
      "saml.sign-requests=true", // Keycloak forces spring to sign requests even if keycloak doesn't
      // validate the signature.
      "saml.signing-credentials[0].privateKeyLocation=private_key.pem",
      "saml.signing-credentials[0].certificateLocation=certificate.pem",
      "management.endpoints.web.exposure.include=beans" // used as an authenticated endpoint to
      // validate auth stuff
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
