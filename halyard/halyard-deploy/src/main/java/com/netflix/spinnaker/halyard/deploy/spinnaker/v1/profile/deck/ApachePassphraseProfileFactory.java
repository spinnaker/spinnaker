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
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.TemplateBackedProfileFactory;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ApachePassphraseProfileFactory extends TemplateBackedProfileFactory {
  private static String PASSPHRASE_TEMPLATE = "#!/usr/bin/env bash\n" + "echo {%passphrase%}\n";

  @Override
  protected String getTemplate() {
    return PASSPHRASE_TEMPLATE;
  }

  @Override
  protected boolean showEditWarning() {
    return false;
  }

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);
    profile.setUser(ApacheSettings.APACHE_USER);
  }

  @Override
  protected Map<String, Object> getBindings(
      DeploymentConfiguration deploymentConfiguration,
      Profile profile,
      SpinnakerRuntimeSettings endpoints) {
    Map<String, Object> bindings = new HashMap<>();
    ApacheSsl ssl = deploymentConfiguration.getSecurity().getUiSecurity().getSsl();
    if (EncryptedSecret.isEncryptedSecret(ssl.getSslCertificatePassphrase())
        && !supportsSecretDecryption(deploymentConfiguration.getName())) {
      bindings.put("passphrase", secretSessionManager.decrypt(ssl.getSslCertificatePassphrase()));
    } else {
      bindings.put("passphrase", ssl.getSslCertificatePassphrase());
    }
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
