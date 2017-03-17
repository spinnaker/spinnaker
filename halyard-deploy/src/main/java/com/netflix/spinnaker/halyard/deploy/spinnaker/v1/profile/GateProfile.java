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
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerEndpoints;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.GateService;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@Component
public class GateProfile extends SpringProfile {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.GATE;
  }

  @Override
  public ProfileConfig generateFullConfig(ProfileConfig config, DeploymentConfiguration deploymentConfiguration, SpinnakerEndpoints endpoints) {
    config = super.generateFullConfig(config, deploymentConfiguration, endpoints);
    GateConfig gateConfig = new GateConfig(endpoints.getServices().getGate(), deploymentConfiguration.getSecurity());
    gateConfig.getCors().setAllowedOriginsPattern(deploymentConfiguration.getSecurity(), endpoints.getServices().getDeck());
    return config.extendConfig(config.getPrimaryConfigFile(), yamlToString(gateConfig));
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  private static class GateConfig extends SpringProfileConfig {
    Cors cors = new Cors();

    GateConfig(GateService gate, Security security) {
      super(gate);
      spring = new SpringConfig(security);
    }

    @Data
    static class Cors {
      private String allowedOriginsPattern;

      void setAllowedOriginsPattern(Security security, DeckService deck) {
        String domain = security.getUiDomain();
        domain = domain.replace(".", "\\.");

        boolean ssl = security.getSsl().isEnabled();
        String protocol = ssl ? "https" : "http";

        String port = Integer.toString(deck.getPort());


        allowedOriginsPattern = "^" + protocol + "://"
            + "(?:" + domain + ")"
            + "(?::" + port + ")?/?$";
      }
    }
  }
}
