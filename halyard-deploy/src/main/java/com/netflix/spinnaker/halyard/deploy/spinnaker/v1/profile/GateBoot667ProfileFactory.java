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

import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import org.springframework.stereotype.Component;

/**
 * Factory class for creating Gate configuration profiles for versions 6.67.0 and above.
 *
 * <p>This class extends {@link GateProfileFactory} and provides specific configurations required
 * for Gate versions 6.67.0 and later. In these versions, a different set of properties is needed to
 * enable OAuth2 authentication.
 *
 * <p>The factory determines the appropriate security configuration (OAuth2, SAML, LDAP, IAP, X509)
 * based on the provided {@link Security} settings and constructs the {@link GateConfig}
 * accordingly.
 */
@Component
public class GateBoot667ProfileFactory extends GateProfileFactory {

  /**
   * Creates a {@link GateConfig} instance based on the provided security settings.
   *
   * <p>If OAuth2 authentication is enabled, a {@link SpringConfig} is set up. If SAML
   * authentication is enabled, a {@link SamlConfig} is set. If LDAP authentication is enabled, a
   * {@link LdapConfig} is set. If IAP authentication is enabled, a {@link IAPConfig} is set under
   * Google authentication. If X509 authentication is enabled, a {@link X509Config} is set.
   *
   * @param gate The service settings for Gate.
   * @param security The security configuration settings.
   * @return A configured {@link GateConfig} instance.
   */
  @Override
  protected GateConfig getGateConfig(ServiceSettings gate, Security security) {
    GateConfig config = new GateConfig(gate, security);

    if (security.getAuthn().getOauth2().isEnabled()) {
      config.setSpring(new SpringConfig(security.getAuthn().getOauth2()));
    } else if (security.getAuthn().getSaml().isEnabled()) {
      config.saml = new SamlConfig(security);
    } else if (security.getAuthn().getLdap().isEnabled()) {
      config.ldap = new LdapConfig(security);
    } else if (security.getAuthn().getIap().isEnabled()) {
      config.google.iap = new IAPConfig(security);
    }

    if (security.getAuthn().getX509().isEnabled()) {
      config.x509 = new X509Config(security);
    }

    return config;
  }
}
