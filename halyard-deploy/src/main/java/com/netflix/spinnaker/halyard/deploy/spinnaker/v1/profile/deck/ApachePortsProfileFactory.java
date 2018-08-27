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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.TemplateBackedProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ApachePortsProfileFactory extends TemplateBackedProfileFactory {
  private static String PORTS_TEMPLATE = String.join("\n",
      "Listen {%deck-host%}:{%deck-port%}",
      "",
      "<IfModule ssl_module>",
      "  Listen 443",
      "  SSLPassPhraseDialog exec:/etc/apache2/passphrase",
      "</IfModule>",
      "",
      "<IfModule mod_gnutls.c>",
      "  Listen 443",
      "</IfModule>");

  @Override
  protected String getTemplate() {
    return PORTS_TEMPLATE;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);
    profile.setUser(ApacheSettings.APACHE_USER);
  }

  @Override
  protected Map<String, Object> getBindings(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("deck-host", endpoints.getServiceSettings(Type.DECK).getHost());
    bindings.put("deck-port", endpoints.getServiceSettings(Type.DECK).getPort() + "");
    return bindings;
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.DECK;
  }

  @Override
  protected String commentPrefix() {
    return "## ";
  }
}
