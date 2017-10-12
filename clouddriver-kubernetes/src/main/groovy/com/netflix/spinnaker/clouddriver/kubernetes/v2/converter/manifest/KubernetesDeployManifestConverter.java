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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.manifest;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest.KubernetesManifestDeployer;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.DEPLOY_MANIFEST;

@KubernetesOperation(DEPLOY_MANIFEST)
@Component
public class KubernetesDeployManifestConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Autowired
  private KubernetesResourcePropertyRegistry registry;

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new KubernetesManifestDeployer(convertDescription(input), registry);
  }

  @Override
  public KubernetesManifestOperationDescription convertDescription(Map input) {
    return (KubernetesManifestOperationDescription) KubernetesAtomicOperationConverterHelper
        .convertDescription(input, this, KubernetesManifestOperationDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}
