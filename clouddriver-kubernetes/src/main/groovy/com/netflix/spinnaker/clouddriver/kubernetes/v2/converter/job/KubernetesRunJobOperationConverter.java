/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.converter.job;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesV2ArtifactProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.job.KubernetesRunJobOperationDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.job.KubernetesRunJobOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.RUN_JOB;

@KubernetesOperation(RUN_JOB)
@Component
public class KubernetesRunJobOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private KubernetesResourcePropertyRegistry registry;

  private KubernetesV2ArtifactProvider artifactProvider;

  public KubernetesRunJobOperationConverter(KubernetesResourcePropertyRegistry registry, KubernetesV2ArtifactProvider artifactProvider) {
    this.registry = registry;
    this.artifactProvider = artifactProvider;
  }

  @Override
  public AtomicOperation convertOperation(Map input){
    return new KubernetesRunJobOperation(convertDescription(input), registry, artifactProvider);
  }


  @Override
  public KubernetesRunJobOperationDescription convertDescription(Map input){
    return (KubernetesRunJobOperationDescription) KubernetesAtomicOperationConverterHelper
      .convertDescription(input, this, KubernetesRunJobOperationDescription.class);
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}
