/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

abstract class AbstractCloudFoundryAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  Optional<CloudFoundrySpace> findSpace(String region, CloudFoundryClient client) {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion(region);

    // fully populates the space guid which is what Cloud Foundry's API expects as an input, not the name.
    Optional<CloudFoundrySpace> spaceOptional = client.getOrganizations()
      .findByName(space.getOrganization().getName())
      .map(org -> client.getSpaces().findByName(org.getId(), space.getName()));

    spaceOptional.ifPresent(spaceCase -> {
      if (!(space.getName().equals(spaceCase.getName()) &&
        space.getOrganization().getName().equals(spaceCase.getOrganization().getName()))) {
        throw new CloudFoundryApiException("Org or Space name not in correct case");
      }
    });

    return spaceOptional;
  }

  protected CloudFoundryClient getClient(Map<?, ?> input) {
    CloudFoundryCredentials credentials = getCredentialsObject(input.get("credentials").toString());
    return credentials.getClient();
  }

  void downloadAndProcessManifest(ArtifactDownloader downloader, Artifact manifest, Consumer<Map> processManifest) {
    try {
      InputStream manifestInput = downloader.download(manifest);
      Yaml parser = new Yaml(new SafeConstructor());
      processManifest.accept(parser.load(manifestInput));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
