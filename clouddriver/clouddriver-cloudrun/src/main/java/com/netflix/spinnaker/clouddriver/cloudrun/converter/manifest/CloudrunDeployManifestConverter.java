/*
 * Copyright 2022 OpsMx, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.cloudrun.converter.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.DEPLOY_CLOUDRUN_MANIFEST;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters.CloudrunAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.op.manifest.CloudrunDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter;
import groovy.util.logging.Slf4j;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CloudrunOperation(DEPLOY_CLOUDRUN_MANIFEST)
@Component
@Slf4j
public class CloudrunDeployManifestConverter
    extends AbstractAtomicOperationsCredentialsConverter<CloudrunNamedAccountCredentials> {

  @Autowired private ObjectMapper objectMapper;

  public AtomicOperation convertOperation(Map input) {
    return new CloudrunDeployManifestOperation(convertDescription(input));
  }

  public CloudrunDeployManifestDescription convertDescription(Map input) {

    CloudrunDeployManifestDescription description =
        CloudrunAtomicOperationConverterHelper.convertDescription(
            input, this, CloudrunDeployManifestDescription.class);
    if (input.get("moniker") != null
        && ((Map<String, String>) input.get("moniker")).get("app") != null) {
      description.setApplication(((Map<String, String>) input.get("moniker")).get("app"));
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
