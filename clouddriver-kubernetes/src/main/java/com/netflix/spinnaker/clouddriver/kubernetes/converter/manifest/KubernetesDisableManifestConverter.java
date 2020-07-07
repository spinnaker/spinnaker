/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.converter.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.DISABLE_MANIFEST;

import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesEnableDisableManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.op.manifest.KubernetesDisableManifestOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.stereotype.Component;

@KubernetesOperation(DISABLE_MANIFEST)
@Component
public class KubernetesDisableManifestConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new KubernetesDisableManifestOperation(convertDescription(input));
  }

  @Override
  public KubernetesEnableDisableManifestDescription convertDescription(Map input) {
    return KubernetesAtomicOperationConverterHelper.convertDescription(
        input, this, KubernetesEnableDisableManifestDescription.class);
  }
}
