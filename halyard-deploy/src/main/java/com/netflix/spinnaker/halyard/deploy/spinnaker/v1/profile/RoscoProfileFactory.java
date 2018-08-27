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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.HasImageProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.core.error.v1.HalException;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.registry.v1.ProfileRegistry;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
@Slf4j
public class RoscoProfileFactory extends SpringProfileFactory {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.ROSCO;
  }

  @Autowired
  ProfileRegistry profileRegistry;

  @Autowired
  HalconfigDirectoryStructure halconfigDirectoryStructure;

  @Autowired
  Yaml yamlParser;

  @Autowired
  ObjectMapper objectMapper;

  protected Providers getImageProviders(String version, String deploymentName) {
    Providers providers;

    try (InputStream is = profileRegistry.readProfile(getArtifact().getName(), version, "images.yml")) {
      Object obj = yamlParser.load(is);

      providers = objectMapper.convertValue(obj, Providers.class);
    } catch (IOException e) {
      throw new HalException(Problem.Severity.FATAL, "Unable to read images.yml for rosco: " + e.getMessage(), e);
    }

    File f = halconfigDirectoryStructure.getUserProfilePath(deploymentName).resolve("images-local.yml").toFile();
    Providers localProviders = null;

    if (f.exists()) {
      // Need just the $PROVIDER.bakeryDefaults.baseImages list items to merge in with the content from rosco/halconfig/images.yml.
      try (FileInputStream fis = new FileInputStream(f)) {
        Object localObj = yamlParser.load(fis);

        localProviders = objectMapper.convertValue(localObj, Providers.class);
      } catch (IOException e) {
        throw new HalException(Problem.Severity.FATAL, "Unable to read images-local.yml for rosco: " + e.getMessage(), e);
      }
    }

    if (localProviders != null) {
      augmentProvidersBaseImages(providers, localProviders);
    }

    return providers;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    Providers providers = deploymentConfiguration.getProviders();
    Providers otherProviders = getImageProviders(profile.getVersion(), deploymentConfiguration.getName());

    augmentProvidersBaseImages(providers, otherProviders);

    List<String> files = backupRequiredFiles(providers, deploymentConfiguration.getName());
    profile.appendContents(yamlToString(providers))
        .appendContents(profile.getBaseContents())
        .setRequiredFiles(files);
  }

  private void augmentProvidersBaseImages(Providers providers, Providers otherProviders) {
    NodeIterator iterator = providers.getChildren();
    Provider child = (Provider) iterator.getNext();
    while (child != null) {
      if (child instanceof HasImageProvider) {
        NodeIterator otherIterator = otherProviders.getChildren();
        NodeFilter providerFilter = new NodeFilter().setProvider(child.getNodeName());
        HasImageProvider otherChild = (HasImageProvider) otherIterator.getNext(providerFilter);
        if (otherChild == null) {
          log.warn("images.yml has no images stored for " + child.getNodeName());
        } else {
          log.info("Adding default images for " + child.getNodeName());
          ((HasImageProvider) child).getBakeryDefaults().addDefaultImages(otherChild.getBakeryDefaults().getBaseImages());
        }
      }

      child = (Provider) iterator.getNext();
    }
  }
}
