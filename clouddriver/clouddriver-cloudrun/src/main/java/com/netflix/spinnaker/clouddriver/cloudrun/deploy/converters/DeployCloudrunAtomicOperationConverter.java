/*
 * Copyright 2022 OpsMx Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.description.DeployCloudrunDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.ops.DeployCloudrunAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter;
import groovy.util.logging.Slf4j;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CloudrunOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
@Slf4j
public class DeployCloudrunAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsConverter<CloudrunNamedAccountCredentials> {

  @Autowired private ObjectMapper objectMapper;

  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudrunAtomicOperation(convertDescription(input));
  }

  public DeployCloudrunDescription convertDescription(Map input) {

    DeployCloudrunDescription description =
        CloudrunAtomicOperationConverterHelper.convertDescription(
            input, this, DeployCloudrunDescription.class);
    if (input.get("application") != null) {
      description.setApplication((String) input.get("application"));
    }
    return description;
  }

  @Override
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public void setObjectMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }
}
