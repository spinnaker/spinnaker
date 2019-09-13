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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.validator.servergroup;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.RESIZE_SERVER_GROUP;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.servergroup.KubernetesResizeServerGroupDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.validator.KubernetesValidationUtil;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.ProviderVersion;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@KubernetesOperation(RESIZE_SERVER_GROUP)
@Component
public class KubernetesResizeServerGroupValidator
    extends DescriptionValidator<KubernetesResizeServerGroupDescription> {
  @Autowired AccountCredentialsProvider provider;

  @Override
  public void validate(
      List priorDescriptions, KubernetesResizeServerGroupDescription description, Errors errors) {
    KubernetesValidationUtil util =
        new KubernetesValidationUtil("deployKubernetesManifest", errors);
    if (!util.validateV2Credentials(
        provider,
        description.getAccount(),
        description.getCoordinates().getKind(),
        description.getCoordinates().getNamespace())) {
      return;
    }

    util.validateNotEmpty("capacity", description.getCapacity());
  }

  @Override
  public boolean acceptsVersion(ProviderVersion version) {
    return version == ProviderVersion.v2;
  }
}
