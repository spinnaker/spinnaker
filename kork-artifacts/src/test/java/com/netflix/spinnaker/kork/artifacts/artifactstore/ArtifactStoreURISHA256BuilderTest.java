/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.hash.Hashing;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ArtifactStoreURISHA256BuilderTest {
  @Test
  public void testSimpleURI() {
    ArtifactStoreURIBuilder builder = new ArtifactStoreURISHA256Builder();
    ArtifactReferenceURI uri = builder.buildURIFromPaths("application", "foo");
    String expectedURIString = "ref://application/foo";
    String expectedPaths = "application/foo";
    assertEquals(expectedURIString, uri.uri());
    assertEquals(expectedPaths, uri.paths());
  }

  @Test
  public void testProperSHA() {
    ArtifactStoreURIBuilder builder = new ArtifactStoreURISHA256Builder();
    String reference = "hello world";
    String expectedSHA =
        Hashing.sha256().hashBytes(reference.getBytes(StandardCharsets.UTF_8)).toString();
    Artifact artifact = Artifact.builder().type("embedded/base64").reference(reference).build();
    ArtifactReferenceURI uri = builder.buildArtifactURI("application", artifact);
    String expectedURIString = "ref://application/" + expectedSHA;
    String expectedPaths = "application/" + expectedSHA;
    assertEquals(expectedURIString, uri.uri());
    assertEquals(expectedPaths, uri.paths());
  }
}
