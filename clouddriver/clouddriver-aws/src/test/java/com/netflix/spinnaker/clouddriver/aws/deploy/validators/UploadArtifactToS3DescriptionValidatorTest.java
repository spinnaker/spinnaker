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

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.aws.TestCredential;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UploadArtifactToS3Description;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UploadArtifactToS3DescriptionValidatorTest {

  private UploadArtifactToS3DescriptionValidator validator;
  private ValidationErrors errors;

  @BeforeEach
  void setUp() {
    validator = new UploadArtifactToS3DescriptionValidator();
    errors = mock(ValidationErrors.class);
  }

  @Test
  void shouldPassValidationWithAllRequiredFields() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setArtifact(Artifact.builder().build());
    description.setCredentials(TestCredential.named("test"));

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors, never()).rejectValue(anyString(), anyString());
  }

  @Test
  void shouldRejectMissingRegion() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setBucket("my-bucket");
    description.setArtifact(Artifact.builder().build());
    description.setCredentials(TestCredential.named("test"));

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("region", "uploadArtifactToS3Description.region.empty");
  }

  @Test
  void shouldRejectMissingBucket() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setArtifact(Artifact.builder().build());
    description.setCredentials(TestCredential.named("test"));

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("bucket", "uploadArtifactToS3Description.bucket.empty");
  }

  @Test
  void shouldRejectMissingArtifact() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setCredentials(TestCredential.named("test"));

    validator.validate(Collections.emptyList(), description, errors);

    verify(errors).rejectValue("artifact", "uploadArtifactToS3Description.artifact.empty");
  }
}
