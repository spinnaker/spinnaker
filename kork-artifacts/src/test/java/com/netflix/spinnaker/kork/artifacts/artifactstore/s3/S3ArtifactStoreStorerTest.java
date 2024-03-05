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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURISHA256Builder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class S3ArtifactStoreStorerTest {
  @Test
  public void testExceptionPathOfObjectExists() {
    S3Client client = mock(S3Client.class);
    when(client.headObject(any(HeadObjectRequest.class)))
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

  @ParameterizedTest(
      name = "testApplicationsRegex application = {0}, applicationsRegex = {1}, enabled = {2}")
  @MethodSource("applicationsRegexArgs")
  void testApplicationsRegex(String application, String applicationsRegex, boolean enabled) {
    S3Client client = mock(S3Client.class);
    AuthenticatedRequest.set(Header.APPLICATION, application);
    S3ArtifactStoreStorer artifactStoreStorer =
        new S3ArtifactStoreStorer(
            client, "my-bucket", new ArtifactStoreURISHA256Builder(), applicationsRegex);
    artifactStoreStorer.store(
        Artifact.builder()
            .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
            .reference("aGVsbG8gd29ybGQK")
            .build());
    // If enabled, expect a call to check if the object exists in the artifact store
    verify(client, times(enabled ? 1 : 0)).headObject(any(HeadObjectRequest.class));
    verifyNoMoreInteractions(client);
  }

  private static Stream<Arguments> applicationsRegexArgs() {
    List<String> apps = List.of("app-one", "app-two", "app-three", "app-five.*");

    // The artifact store is enabled only for exact matches (e.g. not partial
    // matches) of applications in the list.
    String allowRegex = "^(" + String.join("|", apps) + ")$";

    // To test applicationsRegex as a "deny list", meaning that the artifact
    // store is enabled for all apps except those in the list, use a negative
    // lookahead (?!).  See https://stackoverflow.com/a/14147470/9572.
    String denyRegex = "^(?!(" + String.join("|", apps) + ")$).*";

    return Stream.of(
        Arguments.of("any", null, true),
        Arguments.of("app-one", allowRegex, true),
        Arguments.of("app-four", allowRegex, false),
        Arguments.of("one", allowRegex, false),
        Arguments.of("app-one-more", allowRegex, false),
        Arguments.of("app-five", allowRegex, true),
        Arguments.of("app-five-more", allowRegex, true),
        Arguments.of("app-one", denyRegex, false),
        Arguments.of("app-four", denyRegex, true),
        Arguments.of("one", denyRegex, true),
        Arguments.of("app-one-more", denyRegex, true),
        Arguments.of("app-five", denyRegex, false),
        Arguments.of("app-five-more", denyRegex, false));
  }

  @Test
  public void testInvalidEmbeddedBase64StillSucceeds() {
    S3Client client = mock(S3Client.class);
    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    S3ArtifactStoreStorer artifactStore =
        new S3ArtifactStoreStorer(client, "my-bucket", new ArtifactStoreURISHA256Builder(), null);
    String expectedReference = "${ #nonbase64spel() }";
    Artifact artifact =
        artifactStore.store(
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                .reference(expectedReference)
                .build());
    assertEquals(expectedReference, artifact.getReference());
    assertEquals(ArtifactTypes.EMBEDDED_BASE64.getMimeType(), artifact.getType());
  }
}
