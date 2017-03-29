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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

@Component
public class GateProfileFactory extends SpringProfileFactory {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.GATE;
  }

  @Override
  public void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);
    GateConfig gateConfig = new GateConfig(endpoints.getServices().getGate(), deploymentConfiguration.getSecurity());
    gateConfig.getCors().setAllowedOrigins(deploymentConfiguration.getSecurity(), endpoints.getServices().getDeck());
    profile.appendContents(yamlToString(gateConfig))
        .appendContents(profile.getBaseContents());
  }

  @EqualsAndHashCode(callSuper = true)
  @Data
  private static class GateConfig extends SpringProfileConfig {
    Cors cors = new Cors();

    GateConfig(ServiceSettings gate, Security security) {
      super(gate);
      spring = new SpringConfig(security);
    }

    @Data
    static class Cors {
      private String allowedOrigins;

      void setAllowedOrigins(Security security, ServiceSettings deck) {
        boolean ssl = security.getSsl().isEnabled();
        String protocol = ssl ? "https" : "http";
        String domain = security.getUiDomain();
        String port = Integer.toString(deck.getPort());

        allowedOrigins = protocol + "://" + domain + ":" + port;
      }
    }
  }
}
