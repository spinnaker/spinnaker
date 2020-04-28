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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.security.UiSecurity;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.*;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.ApachePassphraseProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.ApachePortsProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.ApacheSpinnakerProfileFactory;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck.DeckProfileFactory;
import java.nio.file.Paths;
import java.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public abstract class DeckService extends SpinnakerService<DeckService.Deck> {
  @Autowired protected DeckProfileFactory deckProfileFactory;

  @Autowired ApachePassphraseProfileFactory apachePassphraseProfileFactory;

  @Autowired ApachePortsProfileFactory apachePortsProfileFactory;

  @Autowired ApacheSpinnakerProfileFactory apacheSpinnakerProfileFactory;

  String htmlPath = "/opt/deck/html/";

  @Override
  public Class<Deck> getEndpointClass() {
    return Deck.class;
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.DECK;
  }

  @Override
  public Type getType() {
    return Type.DECK;
  }

  protected Optional<String> customProfileOutputPath(String profileName) {
    if (profileName.equals("settings.js") || profileName.equals("settings-local.js")) {
      return Optional.of(htmlPath + profileName);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public List<Profile> getProfiles(
      DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    List<Profile> result = new ArrayList<>();
    String apache2Path = "/etc/apache2/";
    String sitePath = "/etc/apache2/sites-available/";
    String filename = "settings.js";
    String path = Paths.get(htmlPath, filename).toString();
    result.add(deckProfileFactory.getProfile(filename, path, deploymentConfiguration, endpoints));

    filename = "passphrase";
    path = Paths.get(apache2Path, filename).toString();
    result.add(
        apachePassphraseProfileFactory
            .getProfile("apache2/" + filename, path, deploymentConfiguration, endpoints)
            .setExecutable(true));

    filename = "ports.conf";
    path = Paths.get(apache2Path, filename).toString();
    result.add(
        apachePortsProfileFactory.getProfile(
            "apache2/" + filename, path, deploymentConfiguration, endpoints));

    filename = "spinnaker.conf";
    path = Paths.get(sitePath, filename).toString();
    result.add(
        apacheSpinnakerProfileFactory.getProfile(
            "apache2/" + filename, path, deploymentConfiguration, endpoints));

    return result;
  }

  protected DeckService() {
    super();
  }

  public interface Deck {}

  @EqualsAndHashCode(callSuper = true)
  @Data
  public static class Settings extends ServiceSettings {
    Integer port = 9000;
    String address = "localhost";
    String host = "0.0.0.0";
    String scheme = "http";
    String healthEndpoint = null;
    Boolean enabled = true;
    Boolean safeToUpdate = true;
    Boolean monitored = false;
    Boolean sidecar = false;
    Integer targetSize = 1;
    Boolean skipLifeCycleManagement = false;
    Map<String, String> env = new HashMap<>();

    public Settings() {}

    public Settings(UiSecurity uiSecurity) {
      setOverrideBaseUrl(uiSecurity.getOverrideBaseUrl());
      if (uiSecurity.getSsl().isEnabled()) {
        scheme = "https";
      }
    }
  }
}
