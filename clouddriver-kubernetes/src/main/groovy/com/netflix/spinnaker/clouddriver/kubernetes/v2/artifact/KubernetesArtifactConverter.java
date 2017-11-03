/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.model.ArtifactProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

import java.util.Arrays;

public abstract class KubernetesArtifactConverter {
  abstract public Artifact toArtifact(ArtifactProvider artifactProvider, KubernetesManifest manifest);
  abstract public KubernetesCoordinates toCoordinates(Artifact artifact);
  abstract public String getDeployedName(Artifact artifact);

  protected String getType(KubernetesManifest manifest) {
    return String.join("/",
        KubernetesCloudProvider.getID(),
        manifest.getApiVersion().toString() + "|" + manifest.getKind().toString()
    );
  }

  private String[] getLatterType(Artifact artifact) {
    String[] split = artifact.getType().split("/", -1);
    if (split.length < 2) {
      throw new IllegalArgumentException("Not a kubernetes artifact: " + artifact);
    }

    if (!split[0].equals(KubernetesCloudProvider.getID())) {
      throw new IllegalArgumentException("Not a kubernetes artifact: " + artifact);
    }

    split = String.join("/", Arrays.copyOfRange(split, 1, split.length)).split("\\|");

    if (split.length != 2) {
      throw new IllegalArgumentException("Not a kubernetes artifact: " + artifact);
    }

    return split;
  }

  protected KubernetesApiVersion getApiVersion(Artifact artifact) {
    return KubernetesApiVersion.fromString(getLatterType(artifact)[0]);
  }

  protected KubernetesKind getKind(Artifact artifact) {
    return KubernetesKind.fromString(getLatterType(artifact)[1]);
  }

  protected String getNamespace(Artifact artifact) {
    return artifact.getLocation();
  }
}
