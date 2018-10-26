/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.servicebroker.AtomicOperations;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@CloudFoundryOperation(AtomicOperations.DEPLOY_SERVICE)
@Component
public class DeployCloudFoundryServiceAtomicOperationConverter extends AbstractCloudFoundryAtomicOperationConverter {

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFoundryServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public DeployCloudFoundryServiceDescription convertDescription(Map input) {
    DeployCloudFoundryServiceDescription converted = getObjectMapper().convertValue(input, DeployCloudFoundryServiceDescription.class);
    converted.setClient(getClient(input));
    converted.setSpace(findSpace(converted.getRegion(), converted.getClient())
      .orElseThrow(() -> new IllegalArgumentException("Unable to find space '" + converted.getRegion() + "'.")));

    String parameterString = (String) input.get("parameters");
    if (!StringUtils.isBlank(parameterString)) {
      try {
        converted.setParameterMap(getObjectMapper()
          .readValue(parameterString, new TypeReference<Map<String, Object>>() {
          }));
      } catch (IOException e) {
        throw new IllegalArgumentException("Unable to convert parameters to map: '" + e.getMessage() + "'.");
      }
    }

    return converted;
  }
}
