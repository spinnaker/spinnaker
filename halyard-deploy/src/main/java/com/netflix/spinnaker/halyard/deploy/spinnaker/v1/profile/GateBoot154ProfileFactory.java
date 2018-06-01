/*
 * Copyright 2017 Google, Inc.
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

@Component
public class GateBoot154ProfileFactory extends GateProfileFactory {

  @Override
  protected GateConfig getGateConfig(ServiceSettings gate, Security security) {
    GateConfig config = new GateConfig(gate, security);

    if (security.getAuthn().getOauth2().isEnabled()) {
      config.security.oauth2 = security.getAuthn().getOauth2();
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
