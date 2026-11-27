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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.junit.jupiter.api.Test;

class UploadArtifactToS3DescriptionTest {

  @Test
  void shouldUseAccountRestrictions() {
    assertFalse(new UploadArtifactToS3Description().requiresApplicationRestriction());
  }

  @Test
  void shouldStoreAllRequiredFields() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setRegion("us-west-2");
    description.setBucket("my-bucket");
    description.setObjectKeyPrefix("releases/v1.0");
    description.setArtifact(Artifact.builder().build());

    assertEquals("us-west-2", description.getRegion());
    assertEquals("my-bucket", description.getBucket());
    assertEquals("releases/v1.0", description.getObjectKeyPrefix());
    assertNotNull(description.getArtifact());
  }

  @Test
  void shouldHaveDefaultEmptyObjectKeyPrefix() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();

    assertEquals("", description.getObjectKeyPrefix());
  }

  @Test
  void shouldHaveDefaultDeleteStaleFilesFalse() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();

    assertFalse(description.isDeleteStaleFiles());
  }

  @Test
  void shouldSetDeleteStaleFiles() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setDeleteStaleFiles(true);

    assertTrue(description.isDeleteStaleFiles());
  }

  @Test
  void shouldHaveDefaultCacheControlNull() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();

    assertNull(description.getCacheControl());
  }

  @Test
  void shouldSetCacheControl() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setCacheControl("max-age=31536000, public");

    assertEquals("max-age=31536000, public", description.getCacheControl());
  }

  @Test
  void shouldHaveDefaultContentTypeNull() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();

    assertNull(description.getContentType());
  }

  @Test
  void shouldSetContentType() {
    UploadArtifactToS3Description description = new UploadArtifactToS3Description();
    description.setContentType("text/plain");

    assertEquals("text/plain", description.getContentType());
  }
}
