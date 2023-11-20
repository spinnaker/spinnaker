/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.spinnaker.kork.artifacts.artifactstore.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURISHA256Builder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ArtifactStoreStorerTest {
  @Test
  public void testExceptionPathOfObjectExists() {
    S3Client client = mock(S3Client.class);
    when(client.headObject((HeadObjectRequest) Mockito.any()))
        .thenThrow(S3Exception.builder().statusCode(400).build());
    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    S3ArtifactStoreStorer artifactStoreStorer =
        new S3ArtifactStoreStorer(client, "my-bucket", new ArtifactStoreURISHA256Builder(), null);
    String expectedExceptionMessage = "Failed to query artifact due to invalid request";
    SpinnakerException e =
        Assertions.assertThrows(
            SpinnakerException.class,
            () -> {
              artifactStoreStorer.store(
                  Artifact.builder()
                      .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                      .reference("aGVsbG8gd29ybGQK")
                      .build());
            });
    assertEquals(expectedExceptionMessage, e.getMessage());
  }
}
