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

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApiSecurity;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang.StringUtils;

import java.util.List;

public abstract class GateProfileFactory extends SpringProfileFactory {

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.GATE;
  }

  @Override
  public void setProfile(Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);
    Security security = deploymentConfiguration.getSecurity();
    List<String> requiredFiles = backupRequiredFiles(security.getApiSecurity(), deploymentConfiguration.getName());
    requiredFiles.addAll(backupRequiredFiles(security.getAuthn(), deploymentConfiguration.getName()));
    requiredFiles.addAll(backupRequiredFiles(security.getAuthz(), deploymentConfiguration.getName()));
    GateConfig gateConfig = getGateConfig(endpoints.getServiceSettings(Type.GATE), security);
    gateConfig.getCors().setAllowedOriginsPattern(security.getApiSecurity());
    profile.appendContents(yamlToString(gateConfig))
        .appendContents(profile.getBaseContents())
        .setRequiredFiles(requiredFiles);
  }

  protected abstract GateConfig getGateConfig(ServiceSettings gate, Security security);

  @EqualsAndHashCode(callSuper = true)
  @Data
  protected static class GateConfig extends SpringProfileConfig {
    Cors cors = new Cors();
    SpringConfig spring;
    SamlConfig saml;
    LdapConfig ldap;
    X509Config x509;
    GoogleConfig google = new GoogleConfig();

    GateConfig(ServiceSettings gate, Security security) {
      super(gate);
      server.ssl = security.getApiSecurity().getSsl();
    }

    @Data
    static class Cors {
      private String allowedOriginsPattern;

      void setAllowedOriginsPattern(ApiSecurity apiSecurity) {
        String corsAccessPattern = apiSecurity.getCorsAccessPattern();
        if (!StringUtils.isEmpty(corsAccessPattern)) {
          allowedOriginsPattern = corsAccessPattern;
        }
      }
    }

    @Data
    static class GoogleConfig {
      IAPConfig iap;
    }
  }
}
