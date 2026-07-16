/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.kubernetes.config.KubernetesConfigurationProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesSpinnakerKindMap;
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesCachingProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

@Slf4j
public abstract class AbstractKubernetesCachingAgent implements Agent {

  public static final List<SpinnakerKind> SPINNAKER_UI_KINDS =
      Arrays.asList(
          SpinnakerKind.SERVER_GROUP_MANAGERS,
          SpinnakerKind.SERVER_GROUPS,
          SpinnakerKind.INSTANCES,
          SpinnakerKind.LOAD_BALANCERS,
          SpinnakerKind.SECURITY_GROUPS);

  protected final KubernetesConfigurationProperties configurationProperties;
  protected final KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap;

  @Nullable private final Front50ApplicationLoader front50ApplicationLoader;

  public AbstractKubernetesCachingAgent(
      KubernetesConfigurationProperties configurationProperties,
      KubernetesSpinnakerKindMap kubernetesSpinnakerKindMap,
      @Nullable Front50ApplicationLoader front50ApplicationLoader) {
    this.configurationProperties = configurationProperties;
    this.kubernetesSpinnakerKindMap = kubernetesSpinnakerKindMap;
    this.front50ApplicationLoader = front50ApplicationLoader;
  }

  protected abstract List<KubernetesKind> primaryKinds();

  /**
   * Filters the list of kinds returned from primaryKinds according to configuration.
   *
   * @return filtered list of primaryKinds.
   */
  protected List<KubernetesKind> filteredPrimaryKinds() {
    List<KubernetesKind> primaryKinds = primaryKinds();
    List<KubernetesKind> filteredPrimaryKinds;

    if (configurationProperties.getCache().isCacheAll()) {
      filteredPrimaryKinds = primaryKinds;

    } else if (configurationProperties.getCache().getCacheKinds() != null
        && configurationProperties.getCache().getCacheKinds().size() > 0) {
      // If provider config specifies what kinds to cache, use it
      filteredPrimaryKinds =
          configurationProperties.getCache().getCacheKinds().stream()
              .map(KubernetesKind::fromString)
              .filter(primaryKinds::contains)
              .collect(Collectors.toList());

    } else {
      // Only cache kinds used in Spinnaker's classic infrastructure screens, which are the kinds
      // mapped to Spinnaker kinds like ServerGroups, Instances, etc.
      filteredPrimaryKinds =
          SPINNAKER_UI_KINDS.stream()
              .map(kubernetesSpinnakerKindMap::translateSpinnakerKind)
              .flatMap(Collection::stream)
              .filter(primaryKinds::contains)
              .collect(Collectors.toList());
    }

    // Filter out explicitly omitted kinds in provider config
    if (configurationProperties.getCache().getCacheOmitKinds() != null
        && configurationProperties.getCache().getCacheOmitKinds().size() > 0) {
      List<KubernetesKind> omitKinds =
          configurationProperties.getCache().getCacheOmitKinds().stream()
              .map(KubernetesKind::fromString)
              .collect(Collectors.toList());
      filteredPrimaryKinds =
          filteredPrimaryKinds.stream()
              .filter(k -> !omitKinds.contains(k))
              .collect(Collectors.toList());
    }

    return filteredPrimaryKinds;
  }

  /**
   * method that determines if the provided manifest should be cached or not. It makes that
   * determination based on the following rules:
   *
   * <p>- if a manifest's caching properties has ignore == true, then it will not be cached.
   *
   * <p>- Otherwise, if account is configured to be "onlySpinnakerManaged", and
   * "moniker.spinnaker.io/application" annotation is empty, then it will not be cached.
   *
   * <p>- if {@link KubernetesConfigurationProperties.Cache#isCheckApplicationInFront50()} is true,
   * and the application name obtained from the manifest is not known to front50, then the manifest
   * will not be cached as long as it belongs to one of the logical relationship kinds specified in
   * {@link KubernetesCacheDataConverter#getLogicalRelationshipKinds()}.
   *
   * <p>- If none of the above criteria is satisfied, then the manifest will be cached.
   *
   * @param credentials account credentials
   * @return true, if manifest should be cached, false otherwise
   */
  protected Predicate<KubernetesManifest> shouldCacheManifest(KubernetesCredentials credentials) {
    return m -> {
      KubernetesCachingProperties props = KubernetesManifestAnnotater.getCachingProperties(m);
      log.debug("Manifest {} caching properties: {}", m, props);
      if (props.isIgnore()) {
        return false;
      }

      if (credentials.isOnlySpinnakerManaged() && props.getApplication().isEmpty()) {
        return false;
      }

      if (configurationProperties.getCache().isCheckApplicationInFront50()) {
        // only certain type of kinds are stored in cats_v1_applications table
        SpinnakerKind spinnakerKind =
            credentials.getKubernetesSpinnakerKindMap().translateKubernetesKind(m.getKind());
        log.debug(
            "{}: manifest: {}, kind: {}, spinnakerKind: {}, logicalRelationshipKinds: {}",
            getAgentType(),
            m.getFullResourceName(),
            m.getKind(),
            spinnakerKind,
            KubernetesCacheDataConverter.getLogicalRelationshipKinds());
        if (KubernetesCacheDataConverter.getLogicalRelationshipKinds().contains(spinnakerKind)) {
          if (front50ApplicationLoader == null) {
            return false;
          }

          String appNameFromMoniker = credentials.getNamer().deriveMoniker(m).getApp();

          boolean shouldCache =
              front50ApplicationLoader.getData().stream()
                  .anyMatch(app -> app.equalsIgnoreCase(appNameFromMoniker));

          log.debug(
              "{}: manifest: {}, application name: {}, shouldCache: {}",
              getAgentType(),
              m.getFullResourceName(),
              appNameFromMoniker,
              shouldCache);

          return shouldCache;
        }
      }
      return true;
    };
  }
}
