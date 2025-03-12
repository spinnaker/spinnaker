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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.core.RemoteAction;
import com.netflix.spinnaker.halyard.core.resource.v1.StringReplaceJarResource;
import com.netflix.spinnaker.halyard.core.resource.v1.TemplatedResource;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalGitDeckService extends DeckService implements LocalGitService<DeckService.Deck> {

  @Autowired private HalconfigDirectoryStructure halconfigDirectoryStructure;

  String deckSettingsPath = "settings.js";
  String deckSettingsLocalPath = "settings-local.js";
  String homeDotSpinnakerPath = "~/.spinnaker/";
  String deckPath = Paths.get(homeDotSpinnakerPath, deckSettingsPath).toString();

  String startCommand =
      String.join("\n", "export SETTINGS_PATH=" + deckPath, "yarn > /dev/null", "yarn start");

  @Autowired String gitRoot;

  @Autowired ArtifactService artifactService;

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    Security security = deploymentConfiguration.getSecurity();
    if (security.getUiSecurity().getSsl().isEnabled()) {
      setEnvTrue("DECK_HTTPS");
      setEnv("DECK_CERT", security.getUiSecurity().getSsl().getSslCertificateFile());
      setEnv("DECK_KEY", security.getUiSecurity().getSsl().getSslCertificateKeyFile());
      setEnv("DECK_CA_CERT", security.getUiSecurity().getSsl().getSslCACertificateFile());
    }
    if (security.getAuthn().isEnabled()) {
      setEnvTrue("AUTH_ENABLED");
      setEnv("DECK_HOST", "0.0.0.0");
    }
    if (security.getAuthz().isEnabled()) {
      setEnvTrue("FIAT_ENABLED");
    }
    return new Settings(security.getUiSecurity())
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setHost(security.getAuthn().isEnabled() ? "0.0.0.0" : getDefaultHost())
        .setEnabled(true);
  }

  private void setEnvTrue(String var) {
    setStartCommand(String.join("\n", "export " + var + "=true", getStartCommand()));
  }

  private void setEnv(String var, String value) {
    if (StringUtils.isNotEmpty(value)) {
      setStartCommand(String.join("\n", "export " + var + "=" + value, getStartCommand()));
    }
  }

  public String getArtifactId(String deploymentName) {
    return LocalGitService.super.getArtifactId(deploymentName);
  }

  @Override
  public void collectLogs(DeploymentDetails details, SpinnakerRuntimeSettings runtimeSettings) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    Profile deckProfile =
        deckProfileFactory.getProfile(
            deckSettingsPath, deckPath, deploymentConfiguration, endpoints);

    String deploymentName = deploymentConfiguration.getName();
    Path userProfilePath = halconfigDirectoryStructure.getUserProfilePath(deploymentName);
    Optional<Profile> settingsLocalProfile =
        this.customProfile(
            deploymentConfiguration,
            endpoints,
            Paths.get(userProfilePath.toString(), deckSettingsLocalPath),
            deckSettingsLocalPath);
    settingsLocalProfile.ifPresent(p -> deckProfile.appendContents(p.getContents()));

    result.add(deckProfile);
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

  protected Optional<String> customProfileOutputPath(String profileName) {
    if (profileName.equals(deckSettingsLocalPath)) {
      return Optional.of(homeDotSpinnakerPath + "settings-local-ignored.js");
    } else {
      return Optional.empty();
    }
  }
}
