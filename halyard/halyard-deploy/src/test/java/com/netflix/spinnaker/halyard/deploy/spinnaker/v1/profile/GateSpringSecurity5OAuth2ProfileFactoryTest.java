/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.halyard.config.model.v1.security.ApiSecurity;
import com.netflix.spinnaker.halyard.config.model.v1.security.Authn;
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.config.model.v1.security.SpringSsl;
import com.netflix.spinnaker.halyard.config.model.v1.security.X509;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GateSpringSecurity5OAuth2ProfileFactoryTest {

  private GateSpringSecurity5OAuth2ProfileFactory factory;
  private ServiceSettings mockServiceSettings;
  private Security mockSecurity;
  private Authn mockAuthn;
  private OAuth2 oAuth2;

  /**
   * Sets up the test environment before each test execution. Initializes required mock objects and
   * test fixture to ensure a clean test state before each test case.
   */
  @BeforeEach
  void setUp() {
    factory = new GateSpringSecurity5OAuth2ProfileFactory();
    oAuth2 = new OAuth2();
    oAuth2.setEnabled(true);

    mockServiceSettings = mock(ServiceSettings.class);
    mockSecurity = mock(Security.class);
    mockAuthn = mock(Authn.class);
    ApiSecurity mockApiSecurity = mock(ApiSecurity.class);
    when(mockSecurity.getAuthn()).thenReturn(mockAuthn);
    when(mockAuthn.getOauth2()).thenReturn(oAuth2);
    X509 x509 = new X509();
    when(mockAuthn.getX509()).thenReturn(x509);

    when(mockSecurity.getApiSecurity()).thenReturn(mockApiSecurity);
    SpringSsl springSsl = mock(SpringSsl.class);
    when(mockApiSecurity.getSsl()).thenReturn(springSsl);
  }

  @Test
  void testGetGateConfigWithOAuth2Enabled() {

    // google
    oAuth2.setProvider(OAuth2.Provider.GOOGLE);
    GateProfileFactory.GateConfig config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getGoogle());

    assertNull(
        config
            .getSpring()
            .getSecurity()
            .getOAuth2()
            .getClient()
            .getRegistration()
            .getGoogle()
            .get("redirect-uri"));
    // github
    oAuth2.setProvider(OAuth2.Provider.GITHUB);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getGithub());

    // azure
    oAuth2.setProvider(OAuth2.Provider.AZURE);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getAzure());

    // oracle
    oAuth2.setProvider(OAuth2.Provider.ORACLE);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getOracle());

    // other
    oAuth2.setProvider(OAuth2.Provider.OTHER);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getOther());
  }

  @Test
  void testGetGateConfigWithOAuth2ForRedirectUri() {
    oAuth2.setProvider(OAuth2.Provider.GOOGLE);
    oAuth2.getClient().setPreEstablishedRedirectUri("https://my-real-gate-address.com:8084/login");
    GateProfileFactory.GateConfig config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getGoogle());
    assertNotNull(
        config
            .getSpring()
            .getSecurity()
            .getOAuth2()
            .getClient()
            .getRegistration()
            .getGoogle()
            .get("redirect-uri"));
    assertEquals(
        "\"https://my-real-gate-address.com:8084/login\"",
        config
            .getSpring()
            .getSecurity()
            .getOAuth2()
            .getClient()
            .getRegistration()
            .getGoogle()
            .get("redirect-uri"));
  }
}
