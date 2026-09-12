/*
 * Copyright 2026 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURISHA256Builder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.List;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SqlArtifactStoreStorerTest {

  @ParameterizedTest(
      name = "testApplicationsRegex application = {0}, applicationsRegex = {1}, enabled = {2}")
  @MethodSource("applicationsRegexArgs")
  void testApplicationsRegex(String application, String applicationsRegex, boolean enabled) {
    // RETURNS_DEEP_STUBS so the fluent insertInto(...).values(...).onDuplicateKeyIgnore().execute()
    // chain resolves to further mocks instead of null when the "enabled" case actually calls it.
    DSLContext jooq = mock(DSLContext.class, RETURNS_DEEP_STUBS);
    AuthenticatedRequest.set(Header.APPLICATION, application);
    SqlArtifactStoreStorer storer =
        new SqlArtifactStoreStorer(jooq, new ArtifactStoreURISHA256Builder(), applicationsRegex);

    Artifact result =
        storer.store(
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                .reference("aGVsbG8gd29ybGQK")
                .build());

    if (enabled) {
      // Reaching REMOTE_BASE64 means the insert chain executed without throwing.
      assertEquals(ArtifactTypes.REMOTE_BASE64.getMimeType(), result.getType());
    } else {
      verifyNoInteractions(jooq);
      assertEquals(ArtifactTypes.EMBEDDED_BASE64.getMimeType(), result.getType());
    }
  }

  private static Stream<Arguments> applicationsRegexArgs() {
    List<String> apps = List.of("app-one", "app-two", "app-three", "app-five.*");
    String allowRegex = "^(" + String.join("|", apps) + ")$";
    String denyRegex = "^(?!(" + String.join("|", apps) + ")$).*";

    return Stream.of(
        Arguments.of("any", null, true),
        Arguments.of("app-one", allowRegex, true),
        Arguments.of("app-four", allowRegex, false),
        Arguments.of("app-one", denyRegex, false),
        Arguments.of("app-four", denyRegex, true));
  }

  @Test
  public void testInvalidEmbeddedBase64StillSucceeds() {
    DSLContext jooq = mock(DSLContext.class);
    AuthenticatedRequest.set(Header.APPLICATION, "my-application");
    SqlArtifactStoreStorer storer =
        new SqlArtifactStoreStorer(jooq, new ArtifactStoreURISHA256Builder(), null);
    String expectedReference = "${ #nonbase64spel() }";

    Artifact artifact =
        storer.store(
            Artifact.builder()
                .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
                .reference(expectedReference)
                .build());

    assertEquals(expectedReference, artifact.getReference());
    assertEquals(ArtifactTypes.EMBEDDED_BASE64.getMimeType(), artifact.getType());
    verifyNoInteractions(jooq);
  }
}
