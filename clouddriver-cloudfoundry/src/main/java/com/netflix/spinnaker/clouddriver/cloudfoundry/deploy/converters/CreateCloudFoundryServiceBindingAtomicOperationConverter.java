/*
 * Copyright 2020 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.CreateCloudFoundryServiceBindingDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.CreateCloudFoundryServiceBindingAtomicOperation;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@CloudFoundryOperation(AtomicOperations.CREATE_SERVICE_BINDINGS)
@Component
public class CreateCloudFoundryServiceBindingAtomicOperationConverter
    extends AbstractCloudFoundryServerGroupAtomicOperationConverter {

  private final OperationPoller operationPoller;
  private final ArtifactDownloader artifactDownloader;

  public CreateCloudFoundryServiceBindingAtomicOperationConverter(
      @Qualifier("cloudFoundryOperationPoller") OperationPoller operationPoller,
      ArtifactDownloader artifactDownloader) {
    this.operationPoller = operationPoller;
    this.artifactDownloader = artifactDownloader;
  }

  @Nullable
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new CreateCloudFoundryServiceBindingAtomicOperation(
        operationPoller, convertDescription(input));
  }

  @Override
  public CreateCloudFoundryServiceBindingDescription convertDescription(Map input) {
    List<Map<String, Object>> requests =
        (List<Map<String, Object>>) input.get("serviceBindingRequests");
    for (Map<String, Object> request : requests) {
      if (request.get("artifact") != null) {
        Artifact artifact = getObjectMapper().convertValue(request.get("artifact"), Artifact.class);
        try (InputStream inputStream = artifactDownloader.download(artifact)) {
          Map<String, Object> paramMap = getObjectMapper().readValue(inputStream, Map.class);
          request.put("parameters", paramMap);
        } catch (Exception e) {
          throw new CloudFoundryApiException(
              "Could not convert service binding request parameters to json.");
        }
      }
    }
    input.put("serviceBindingRequests", requests);

    CreateCloudFoundryServiceBindingDescription description =
        getObjectMapper().convertValue(input, CreateCloudFoundryServiceBindingDescription.class);
    description.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    description.setClient(getClient(input));
    description.setServerGroupId(
        getServerGroupId(
            description.getServerGroupName(), description.getRegion(), description.getClient()));
    findSpace(description.getRegion(), description.getClient())
        .ifPresentOrElse(
            description::setSpace,
            () -> {
              throw new CloudFoundryApiException("Could not determine CloudFoundry Space.");
            });
    return description;
  }
}
