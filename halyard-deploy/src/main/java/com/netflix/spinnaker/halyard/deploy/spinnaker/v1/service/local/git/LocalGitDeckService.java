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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.git;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalGitDeckService extends DeckService implements LocalGitService<DeckService.Deck> {
  String deskSettingsPath = "settings.js";
  String deckPath = "~/.spinnaker/" + deskSettingsPath;

  String startCommand = String
      .join("\n", "export SETTINGS_PATH=" + deckPath, "yarn > /dev/null", "yarn start");

  @Autowired
  String gitRoot;

  @Autowired
  ArtifactService artifactService;

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    boolean authEnabled = deploymentConfiguration.getSecurity().getAuthn().isEnabled();
    return new Settings(deploymentConfiguration.getSecurity().getUiSecurity())
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setHost(authEnabled ? "0.0.0.0" : getDefaultHost())
        .setEnabled(true);
  }

  public String getArtifactId(String deploymentName) {
    return LocalGitService.super.getArtifactId(deploymentName);
  }

  @Override
  public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Profile> getProfiles(DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    result.add(deckProfileFactory.getProfile(deskSettingsPath, deckPath, deploymentConfiguration, endpoints));
    return result;
  }

  @Override
  public void commitWrapperScripts() {
    Map<String, Object> bindings = new HashMap<>();
    bindings.put("git-root", getGitRoot());
    bindings.put("scripts-dir", getScriptsDir());
    bindings.put("artifact", getArtifact().getName());
    bindings.put("start-command", getStartCommand());
    TemplatedResource scriptResource = new StringReplaceJarResource("/git/deck-start.sh");
    scriptResource.setBindings(bindings);
    String script = scriptResource.toString();

    new RemoteAction()
        .setScript(script)
        .commitScript(Paths.get(getScriptsDir(), getArtifact().getName() + "-start.sh"));

    scriptResource = new StringReplaceJarResource("/git/stop.sh");
    scriptResource.setBindings(bindings);
    script = scriptResource.toString();

    new RemoteAction()
        .setScript(script)
        .commitScript(Paths.get(getScriptsDir(), getArtifact().getName() + "-stop.sh"));
  }
}
