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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesCacheUtils;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesSpinnakerKindMap.SpinnakerKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public abstract class KubernetesDeployer<T> {
  @Autowired
  protected ObjectMapper objectMapper;

  @Autowired
  private KubernetesCacheUtils cacheUtils;

  @Autowired
  protected KubectlJobExecutor jobExecutor;

  private T convertManifest(KubernetesManifest manifest) {
    return objectMapper.convertValue(manifest, getDeployedClass());
  }

  public DeploymentResult deployAugmentedManifest(KubernetesV2Credentials credentials, KubernetesManifest manifest) {
    T resource = convertManifest(manifest);
    deploy(credentials, resource);

    DeploymentResult result = new DeploymentResult();
    result.setDeployedNames(new ArrayList<>(Collections.singleton(manifest.getNamespace() + ":" + manifest.getFullResourceName())));
    result.setDeployedNamesByLocation(new HashMap<>(Collections.singletonMap(manifest.getNamespace(), Collections.singletonList(manifest.getFullResourceName()))));

    return result;
  }

  abstract public Class<T> getDeployedClass();

  abstract public KubernetesKind kind();
  abstract public KubernetesApiVersion apiVersion();
  abstract public boolean versioned();
  abstract public SpinnakerKind spinnakerKind();
  abstract public boolean isStable(T resource);

  void deploy(KubernetesV2Credentials credentials, T resource) {
    jobExecutor.deployManifest(credentials, objectMapper.convertValue(resource, KubernetesManifest.class));
  }
}
