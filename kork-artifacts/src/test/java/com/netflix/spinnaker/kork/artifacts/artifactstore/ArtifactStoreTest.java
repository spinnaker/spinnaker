/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import org.junit.jupiter.api.Test;

class ArtifactStoreTest {

  private ArtifactStoreGetter artifactStoreGetter = mock(ArtifactStoreGetter.class);
  private ArtifactStoreStorer artifactStoreStorer = mock(ArtifactStoreStorer.class);
  private ArtifactStore artifactStore = new ArtifactStore(artifactStoreGetter, artifactStoreStorer);

  @Test
  void testArtifactStoreDelegatesToGetter() {
    ArtifactReferenceURI uri = mock(ArtifactReferenceURI.class);
    ArtifactDecorator artifactDecorator = mock(ArtifactDecorator.class);

    artifactStore.get(uri, artifactDecorator);

    verify(artifactStoreGetter).get(uri, artifactDecorator);
    verifyNoMoreInteractions(artifactStoreGetter);
    verifyNoInteractions(artifactStoreStorer);
  }

  @Test
  void testArtifactStoreDelegatesToStorer() {
    Artifact inputArtifact = Artifact.builder().build();
    Artifact storedArtifact = Artifact.builder().build();
    when(artifactStore.store(inputArtifact)).thenReturn(storedArtifact);

    Artifact retval = artifactStore.store(inputArtifact);
    assertThat(retval).isEqualTo(storedArtifact);

    verify(artifactStoreStorer).store(inputArtifact);
    verifyNoMoreInteractions(artifactStoreStorer);
    verifyNoInteractions(artifactStoreGetter);
  }
}
