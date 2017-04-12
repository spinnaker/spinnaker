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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.security.ApacheSsl;
import com.netflix.spinnaker.halyard.config.model.v1.security.UiSecurity;
import com.netflix.spinnaker.halyard.core.resource.v1.StringResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ApacheSpinnakerProfileFactory extends TemplateBackedProfileFactory {
  private static String SSL_TEMPLATE = String.join("\n",
      "  SSLEngine on",
      "  SSLCertificateFile \"{%cert-file%}\"",
      "  SSLCertificateKeyFile \"{%key-file%}\"");

  private static String SPINNAKER_TEMPLATE = String.join("\n",
      "<VirtualHost {%deck-host%}:{%deck-port%}>",
      "{%ssl%}",
      "  DocumentRoot /opt/deck/html",
      "",
      "  <Directory \"/opt/deck/html/\">",
      "     Require all granted",
      "  </Directory>",
      "</VirtualHost>");

  @Override
  protected String getTemplate() {
    return SPINNAKER_TEMPLATE;
  }

  @Override
  protected List<String> requiredFiles(DeploymentConfiguration deploymentConfiguration) {
   return processRequiredFiles(deploymentConfiguration.getSecurity().getUiSecurity());
  }

  @Override
  protected Map<String, String> getBindings(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    TemplatedResource resource = new StringResource(SSL_TEMPLATE);
    Map<String, String> bindings = new HashMap<>();
    UiSecurity uiSecurity = deploymentConfiguration.getSecurity().getUiSecurity();
    ApacheSsl apacheSsl = uiSecurity.getSsl();
    bindings.put("cert-file", apacheSsl.getSslCertificateFile());
    bindings.put("key-file", apacheSsl.getSslCertificateKeyFile());
    String ssl = resource.setBindings(bindings).toString();
    bindings.clear();
    bindings.put("ssl", ssl);
    bindings.put("deck-host", endpoints.getServices().getDeck().getHost());
    bindings.put("deck-port", endpoints.getServices().getDeck().getPort() + "");
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
