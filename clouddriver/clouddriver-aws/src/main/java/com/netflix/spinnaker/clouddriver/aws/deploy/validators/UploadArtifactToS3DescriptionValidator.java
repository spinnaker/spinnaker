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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validator for {@link UploadArtifactToS3Description}.
 *
 * <p>Validates that required fields are present: region, bucket, and artifact.
 */
@AmazonOperation(AtomicOperations.UPLOAD_ARTIFACT_TO_OBJECT_STORAGE)
@Component("uploadArtifactToS3DescriptionValidator")
public class UploadArtifactToS3DescriptionValidator
    extends AmazonDescriptionValidationSupport<UploadArtifactToS3Description> {

  private static final String KEY = "uploadArtifactToS3Description";

  @Override
  public void validate(
      List priorDescriptions, UploadArtifactToS3Description description, ValidationErrors errors) {

    if (description.getRegion() == null || description.getRegion().isEmpty()) {
      errors.rejectValue("region", KEY + ".region.empty");
    }

    if (description.getBucket() == null || description.getBucket().isEmpty()) {
      errors.rejectValue("bucket", KEY + ".bucket.empty");
    }

    if (description.getArtifact() == null) {
      errors.rejectValue("artifact", KEY + ".artifact.empty");
    }
  }
}
