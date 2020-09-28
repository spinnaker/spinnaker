/*
 * Copyright 2017 Google, Inc.
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

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.DEPLOY_MANIFEST;

import com.google.common.base.Strings;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.artifact.ResourceVersioner;
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.converters.KubernetesAtomicOperationConverterHelper;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.manifest.KubernetesDeployManifestOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsConverter;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@KubernetesOperation(DEPLOY_MANIFEST)
@Component
public class KubernetesDeployManifestConverter
    extends AbstractAtomicOperationsCredentialsConverter<KubernetesNamedAccountCredentials> {

  private static final String KIND_VALUE_LIST = "list";
  private static final String KIND_LIST_ITEMS_KEY = "items";

  private final ResourceVersioner resourceVersioner;

  @Autowired
  public KubernetesDeployManifestConverter(
      CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository,
      ResourceVersioner resourceVersioner) {
    this.setCredentialsRepository(credentialsRepository);
    this.resourceVersioner = resourceVersioner;
  }

  @Override
  public AtomicOperation<OperationResult> convertOperation(Map<String, Object> input) {
    return new KubernetesDeployManifestOperation(convertDescription(input), resourceVersioner);
  }

  @Override
  public KubernetesDeployManifestDescription convertDescription(Map<String, Object> input) {
    KubernetesDeployManifestDescription mainDescription =
        KubernetesAtomicOperationConverterHelper.convertDescription(
            input, this, KubernetesDeployManifestDescription.class);
    return convertListDescription(mainDescription);
  }

  /**
   * If present, converts a KubernetesManifest of kind List into a list of KubernetesManifest
   * objects.
   *
   * @param mainDescription deploy manifest description as received.
   * @return updated description.
   */
  @SuppressWarnings("unchecked")
  private KubernetesDeployManifestDescription convertListDescription(
      KubernetesDeployManifestDescription mainDescription) {

    if (mainDescription.getManifests() == null) {
      return mainDescription;
    }

    List<KubernetesManifest> updatedManifestList =
        mainDescription.getManifests().stream()
            .flatMap(
                singleManifest -> {
                  if (singleManifest == null
                      || Strings.isNullOrEmpty(singleManifest.getKindName())
                      || !singleManifest.getKindName().equalsIgnoreCase(KIND_VALUE_LIST)) {
                    return Stream.of(singleManifest);
                  }

                  Collection<Object> items =
                      (Collection<Object>) singleManifest.get(KIND_LIST_ITEMS_KEY);

                  if (items == null) {
                    return Stream.of();
                  }

                  return items.stream()
                      .map(i -> getObjectMapper().convertValue(i, KubernetesManifest.class));
                })
            .collect(Collectors.toList());

    mainDescription.setManifests(updatedManifestList);

    return mainDescription;
  }
}
