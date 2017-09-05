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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.APPLICATION;
import static com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys.LogicalKind.CLUSTER;

@Slf4j
public class KubernetesCacheDataConverter {
  public static CacheData fromResource(String account, ObjectMapper mapper, Object resource) {
    KubernetesManifest manifest = mapper.convertValue(resource, KubernetesManifest.class);
    KubernetesKind kind = manifest.getKind();
    KubernetesApiVersion apiVersion = manifest.getApiVersion();
    String name = manifest.getName();
    String namespace = manifest.getNamespace();

    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>()
        .put("kind", kind)
        .put("apiVersion", apiVersion)
        .put("name", name)
        .put("namespace", namespace)
        .put("fullResourceName", manifest.getFullResourceName())
        .put("manifest", manifest)
        .build();

    KubernetesManifestSpinnakerRelationships spinnakerRelationships = KubernetesManifestAnnotater.getManifestRelationships(manifest);
    Map<String, Collection<String>> relationships = new HashMap<>();

    String application = spinnakerRelationships.getApplication();
    if (StringUtils.isEmpty(application)) {
      log.info("Skipping not-spinnaker-owned resource " + namespace + ":" + manifest.getFullResourceName());
      return null;
    }

    relationships.put(APPLICATION.toString(), Collections.singletonList(Keys.application(application)));

    String cluster = spinnakerRelationships.getCluster();
    if (!StringUtils.isEmpty(cluster)) {
      relationships.put(CLUSTER.toString(), Collections.singletonList(Keys.cluster(account, application, cluster)));
    }

    String key = Keys.infrastructure(kind, apiVersion, account, application, namespace, name);
    return new DefaultCacheData(key, attributes, relationships);
  }
}
