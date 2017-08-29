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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesAugmentedManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestAnnotater;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class KubernetesDeployer<T> {
  @Autowired
  protected ObjectMapper objectMapper;

  private void annotateRelationships(KubernetesAugmentedManifest pair) {
    KubernetesManifestAnnotater.annotateManifestWithRelationships(pair.getManifest(), pair.getRelationships());
  }

  private T convertManifest(KubernetesAugmentedManifest pair) {
    return objectMapper.convertValue((Map) pair.getManifest(), getDeployedClass());
  }

  private void setNamespaceIfMissing(KubernetesManifest manifest, String namespace) {
    if (StringUtils.isEmpty(manifest.getNamespace())) {
      manifest.setNamespace(namespace);
    }
  }

  public DeploymentResult deployManifestPair(KubernetesV2Credentials credentials, KubernetesAugmentedManifest pair) {
    KubernetesManifest manifest = pair.getManifest();
    setNamespaceIfMissing(manifest, credentials.getDefaultNamespace());

    annotateRelationships(pair);
    T resource = convertManifest(pair);
    deploy(credentials, resource);

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(new ArrayList<>(Collections.singleton(manifest.getNamespace() + ":" + manifest.getFullResourceName())));
    result.setServerGroupNameByRegion(new HashMap<>(Collections.singletonMap(manifest.getNamespace(), manifest.getFullResourceName())));

    return result;
  }

  abstract Class<T> getDeployedClass();

  abstract void deploy(KubernetesV2Credentials credentials, T resource);
}
