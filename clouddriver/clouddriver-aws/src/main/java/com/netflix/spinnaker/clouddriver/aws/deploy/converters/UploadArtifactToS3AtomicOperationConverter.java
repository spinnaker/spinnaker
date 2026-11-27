/*
 * Copyright 2026 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.UploadArtifactToS3AtomicOperation;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Converter for the Upload Artifact to S3 operation.
 *
 * <p>Converts the input map from Orca into an {@link UploadArtifactToS3Description} and creates the
 * corresponding {@link UploadArtifactToS3AtomicOperation}.
 */
@AmazonOperation(AtomicOperations.UPLOAD_ARTIFACT_TO_OBJECT_STORAGE)
@Component("uploadArtifactToS3AtomicOperationConverter")
public class UploadArtifactToS3AtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final AmazonClientProvider amazonClientProvider;
  private final ArtifactDownloader artifactDownloader;

  public UploadArtifactToS3AtomicOperationConverter(
      AmazonClientProvider amazonClientProvider, ArtifactDownloader artifactDownloader) {
    this.amazonClientProvider = amazonClientProvider;
    this.artifactDownloader = artifactDownloader;
  }

  @Override
  public AtomicOperation<Map<String, Object>> convertOperation(Map<String, Object> input) {
    return new UploadArtifactToS3AtomicOperation(
        convertDescription(input), amazonClientProvider, artifactDownloader);
  }

  @Override
  public UploadArtifactToS3Description convertDescription(Map<String, Object> input) {
    UploadArtifactToS3Description description =
        getObjectMapper().convertValue(input, UploadArtifactToS3Description.class);
    description.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return description;
  }
}
