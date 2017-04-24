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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.TemplateBackedProfileFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ApachePassphraseProfileFactory extends TemplateBackedProfileFactory {
  private static String PASSPHRASE_TEMPLATE = "#!/usr/bin/env bash\n"
      + "echo {%passphrase%}\n";

  @Override
  protected String getTemplate() {
    return PASSPHRASE_TEMPLATE;
  }

  @Override
  protected boolean showEditWarning() {
    return false;
  }

  @Override
  protected Map<String, String> getBindings(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    Map<String, String> bindings = new HashMap<>();
    ApacheSsl ssl = deploymentConfiguration.getSecurity().getUiSecurity().getSsl();
    bindings.put("passphrase", ssl.getSslCertificatePassphrase());
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
