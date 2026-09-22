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
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@NonnullByDefault
public class ArtifactProvider {
  /**
   * gets all {@link Artifact} from a namespace that belongs to the provided kind and matches the
   * provided name. This method helps get all versions of an artifact. For example, if the name ==
   * 'test-configmap', then there may be 3 k8s resources called 'test-configmap-v001',
   * 'test-configmap-v002', 'test-configmap-v003' in the namespace. This method will return the
   * above 3 configmaps.
   *
   * <p>The filtering logic supports two techniques: 1. (Default) search for all resources of the
   * target Kind in the namespace. Create Artifact resources from them and return those that match
   * the provided name. This is the most accurate solution, but is resource inefficient, especially
   * in namespaces that have a lot of resources of the target Kind, since this does a kubectl get of
   * all resources under the covers. 2. (Optimized) If a label selector map is provided, then a
   * kubectl call is made using that label selector. This works well resource efficiency wise, since
   * kubectl get -l works better than doing a kubectl get and then filtering the result.
   *
   * @param manifest {@link KubernetesManifest}
   * @param name artifact name that is the same across one or more manifests
   * @param credentials kubernetes account
   * @param labelSelectors an optional list of labels that can help narrow the search for potential
   *     manifests
   * @return
   */
  public ImmutableList<Artifact> getArtifacts(
      KubernetesManifest manifest,
      String name,
      KubernetesCredentials credentials,
      KubernetesSelectorList labelSelectors) {
    ImmutableList<KubernetesManifest> candidateManifests;
    if (labelSelectors.isEmpty()) {
      candidateManifests = credentials.list(manifest.getKind(), manifest.getNamespace());
    } else {
      candidateManifests =
          credentials.list(manifest.getKind(), manifest.getNamespace(), labelSelectors);
    }

    return candidateManifests.stream()
        .sorted(Comparator.comparing(KubernetesManifest::getCreationTimestamp))
        .map(m -> KubernetesManifestAnnotater.getArtifact(m, credentials.getAccountName()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(a -> Strings.nullToEmpty(a.getName()).equals(name))
        .collect(toImmutableList());
  }
}
