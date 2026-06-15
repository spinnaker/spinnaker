/*
 * Copyright 2026 Vjda<victorjadiaz@outlook.com>
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "saml.enabled=true",
      "saml.issuer-id=spinnaker-test",
      "saml.sign-requests=true",
      "saml.signing-credentials[0].privateKeyLocation=private_key.pem",
      "saml.signing-credentials[0].certificateLocation=certificate.pem",
      "x509.enabled=true",
      "management.endpoints.web.exposure.include=beans"
    },
    classes = Main.class)
class SAMLAndX509ConfigurationIntegrationTest extends AbstractSAMLConfigurationIntegrationTest {

  @Autowired private ListableBeanFactory beanFactory;

  @Test
  void contextLoadsWithDistinctOrderedSecurityFilterChains() {
    assertSamlPropertiesLoaded();

    Map<String, SecurityFilterChain> securityFilterChains =
        beanFactory.getBeansOfType(SecurityFilterChain.class);

    assertThat(securityFilterChains)
        .containsKeys(
            "schemaSecurityFilterChain", "securityFilterChain", "x509SecurityFilterChain");

    Map<SecurityFilterChain, String> beanNameByInstance = new IdentityHashMap<>();
    securityFilterChains.forEach((beanName, chain) -> beanNameByInstance.put(chain, beanName));

    List<String> orderedChainNames =
        beanFactory
            .getBeanProvider(SecurityFilterChain.class)
            .orderedStream()
            .map(beanNameByInstance::get)
            .toList();

    assertThat(orderedChainNames)
        .containsSubsequence(
            "schemaSecurityFilterChain", "securityFilterChain", "x509SecurityFilterChain");
  }

  @Test
  void samlRedirectStillWorksWhenX509IsEnabled() throws Exception {
    assertSamlRedirectFlowWorks();
  }
}
