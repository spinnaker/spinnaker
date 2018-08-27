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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApacheSsl;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeckDockerProfileFactory extends DeckProfileFactory {
  @Autowired
  AccountService accountService;

  @Override
  public String commentPrefix() {
    return "// ";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.DECK;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    ServiceSettings deckSettings = endpoints.getServiceSettings(Type.DECK);
    ServiceSettings gateSettings = endpoints.getServiceSettings(Type.GATE);
    ApacheSsl apacheSsl= deploymentConfiguration.getSecurity().getUiSecurity().getSsl();
    Map<String, String> env = profile.getEnv();

    if (apacheSsl.isEnabled()) {
      env.put("DECK_HOST", deckSettings.getHost());
      env.put("DECK_PORT", deckSettings.getPort() + "");
      env.put("API_HOST", gateSettings.getBaseUrl());
      env.put("DECK_CERT", apacheSsl.getSslCertificateFile());
      env.put("DECK_KEY", apacheSsl.getSslCertificateKeyFile());
      env.put("PASSPHRASE", apacheSsl.getSslCertificatePassphrase());
    }

    env.put("AUTH_ENABLED", Boolean.toString(deploymentConfiguration.getSecurity().getAuthn().isEnabled()));
    env.put("FIAT_ENABLED", Boolean.toString(deploymentConfiguration.getSecurity().getAuthz().isEnabled()));
  }
}
