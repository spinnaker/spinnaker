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

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class GateBoot667ProfileFactoryTest {

  private GateBoot667ProfileFactory factory;
  private ServiceSettings mockServiceSettings;
  private Security mockSecurity;
  private Authn mockAuthn;
  private OAuth2 oAuth2;

  @BeforeEach
  void setUp() {
    factory = new GateBoot667ProfileFactory();
    oAuth2 = new OAuth2();
    oAuth2.setEnabled(true);
    oAuth2.setProvider(OAuth2.Provider.GOOGLE);
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
    GateProfileFactory.GateConfig config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getGoogle());

    oAuth2.setProvider(OAuth2.Provider.GITHUB);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getGithub());

    oAuth2.setProvider(OAuth2.Provider.AZURE);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getAzure());

    oAuth2.setProvider(OAuth2.Provider.ORACLE);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getOracle());

    oAuth2.setProvider(OAuth2.Provider.OTHER);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getOther());

    oAuth2.setProvider(OAuth2.Provider.GITHUB);
    config = factory.getGateConfig(mockServiceSettings, mockSecurity);
    assertNotNull(
        config.getSpring().getSecurity().getOAuth2().getClient().getRegistration().getGithub());
  }
}
