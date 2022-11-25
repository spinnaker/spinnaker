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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudrun.converter.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.CLEANUP_ARTIFACTS;

import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.deploy.converters.CloudrunAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.cloudrun.description.manifest.CloudrunCleanupArtifactsDescription;
import com.netflix.spinnaker.clouddriver.cloudrun.op.artifact.CloudrunCleanupArtifactsOperation;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter;
import java.util.Map;
import org.springframework.stereotype.Component;

@CloudrunOperation(CLEANUP_ARTIFACTS)
@Component
public class CloudrunCleanupArtifactsConverter
    extends AbstractAtomicOperationsCredentialsConverter<CloudrunNamedAccountCredentials> {

  @Override
  public AtomicOperation<DeploymentResult> convertOperation(Map<String, Object> input) {
    return new CloudrunCleanupArtifactsOperation(convertDescription(input));
  }

  @Override
  public CloudrunCleanupArtifactsDescription convertDescription(Map<String, Object> input) {
    return CloudrunAtomicOperationConverterHelper.convertDescription(
        input, this, CloudrunCleanupArtifactsDescription.class);
  }
}
