/*
 * Copyright 2025 Harness, Inc.
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
 *
 */
package com.netflix.spinnaker.gate.security.saml;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.saml2.core.Saml2X509Credential;

class SecuritySamlPropertiesTest {

  @Test
  public void verifyCanLoadCerts() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    properties.setSigningCredentials(
        List.of(
            new SecuritySamlProperties.Credential(
                "classpath:private_key.pem", "classpath:certificate.pem")));
    List<Saml2X509Credential> signingCredentials = properties.getSigningCredentials();
    assertThat(signingCredentials.get(0).getPrivateKey().getAlgorithm()).isEqualTo("RSA");
  }

  @Test
  public void verifyCanLoadCertsFromAFileLocation() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    Path currentDir = Paths.get("");
    properties.setSigningCredentials(
        List.of(
            new SecuritySamlProperties.Credential(
                currentDir.toAbsolutePath() + "/src/test/resources/private_key.pem",
                currentDir.toAbsolutePath() + "/src/test/resources/certificate.pem")));
    List<Saml2X509Credential> signingCredentials = properties.getSigningCredentials();
    assertThat(signingCredentials.get(0).getPrivateKey().getAlgorithm()).isEqualTo("RSA");
  }
}
