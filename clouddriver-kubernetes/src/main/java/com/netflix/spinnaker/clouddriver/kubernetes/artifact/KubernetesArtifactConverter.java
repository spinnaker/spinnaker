/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

public abstract class KubernetesArtifactConverter {
  // Prevent subclassing from outside the package
  KubernetesArtifactConverter() {}

  public static KubernetesArtifactConverter getInstance(boolean versioned) {
    return versioned
        ? KubernetesVersionedArtifactConverter.INSTANCE
        : KubernetesUnversionedArtifactConverter.INSTANCE;
  }

  public abstract Artifact toArtifact(
      ArtifactProvider artifactProvider, KubernetesManifest manifest, String account);

  public abstract String getDeployedName(Artifact artifact);

  protected final String getType(KubernetesManifest manifest) {
    return String.join("/", KubernetesCloudProvider.ID, manifest.getKind().toString());
  }
}
