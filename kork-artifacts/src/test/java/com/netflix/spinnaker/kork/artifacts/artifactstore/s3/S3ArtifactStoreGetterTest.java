/*
 * Copyright 2024 Salesforce, Inc.
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

import static com.netflix.spinnaker.kork.artifacts.artifactstore.s3.S3ArtifactStore.ENFORCE_PERMS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.PermissionEvaluator;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.Tag;

public class S3ArtifactStoreGetterTest {

  @Test
  public void testGetAuthenticatedWithUser() {
    // Verify that having a user set is sufficient to authenticate against that
    // user.
    //
    // Currently, S3ArtifactStoreGetter.hasAuthorization uses
    // SecurityContextHolder.getContext().getAuthentication().  In this test
    // that's null.  In orca at least, there are some calls to
    // get/hasAuthorization where this is also true, but there is a user
    // available in AuthenticatedRequest.

    // given:
    String application = "my-application";
    AuthenticatedRequest.set(Header.USER, "my-user");

    S3Client client = mock(S3Client.class);

    GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
    byte[] responseByteArray = {};
    ResponseBytes<GetObjectResponse> responseBytes =
        ResponseBytes.fromByteArray(getObjectResponse, responseByteArray);

    when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

    Tag tag = Tag.builder().key(ENFORCE_PERMS_KEY).value(application).build();
    GetObjectTaggingResponse getObjectTaggingResponse =
        GetObjectTaggingResponse.builder().tagSet(List.of(tag)).build();
    when(client.getObjectTagging(any(GetObjectTaggingRequest.class)))
        .thenReturn(getObjectTaggingResponse);

    PermissionEvaluator permissionEvaluator = mock(PermissionEvaluator.class);

    // FIXME: The current behavior is to call permissionEvaluator.hasPermission
    // with a null Authentication object.  The correct behavior is to
    // authenticate against AuthenticatedRequest.getSpinnakerUser().
    //
    // It's arbitrary whether to give permission or not (i.e. return true or
    // false).  Choose true since there are then no exceptions to deal with.
    when(permissionEvaluator.hasPermission(
            isNull(), eq(application), eq("application"), eq("READ")))
        .thenReturn(true);

    S3ArtifactStoreGetter artifactStoreGetter =
        new S3ArtifactStoreGetter(client, permissionEvaluator, "my-bucket");

    ArtifactReferenceURI uri = mock(ArtifactReferenceURI.class);

    // when
    Artifact artifact = artifactStoreGetter.get(uri);

    // then
    assertThat(artifact).isNotNull();

    // FIXME: Again, the correct behavior is to authenticate against
    // AuthenticatedRequest.getSpinnakerUser().
    verify(permissionEvaluator)
        .hasPermission(isNull(), eq(application), eq("application"), eq("READ"));
  }
}
