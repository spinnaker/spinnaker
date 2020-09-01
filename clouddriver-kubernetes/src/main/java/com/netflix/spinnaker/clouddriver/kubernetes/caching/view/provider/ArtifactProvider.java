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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.view.provider;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class ArtifactProvider {
  public ImmutableList<Artifact> getArtifacts(
      KubernetesKind kind, String name, String location, KubernetesCredentials credentials) {
    return credentials.list(kind, location).stream()
        .sorted(Comparator.comparing(KubernetesManifest::getCreationTimestamp))
        .map(m -> KubernetesManifestAnnotater.getArtifact(m, credentials.getAccountName()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(a -> Strings.nullToEmpty(a.getName()).equals(name))
        .collect(toImmutableList());
  }
}
