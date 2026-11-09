/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.kubernetes.validator.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeployManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubernetesDeployManifestValidatorTest {
  private final KubernetesDeployManifestValidator validator =
      new KubernetesDeployManifestValidator();
  private final KubernetesDeployManifestDescription description =
      new KubernetesDeployManifestDescription();

  @BeforeEach
  void setup() {
    KubernetesCredentials credentials = mock(KubernetesCredentials.class);
    when(credentials.getNamespaces()).thenReturn(ImmutableList.of("allowed"));
    KubernetesNamedAccountCredentials account = mock(KubernetesNamedAccountCredentials.class);
    when(account.getCredentials()).thenReturn(credentials);
    validator.provider = mock(AccountCredentialsProvider.class);
    when(validator.provider.getCredentials("test-account")).thenReturn(account);
    description.setAccount("test-account");
  }

  @Test
  void missingManifestsProduceAnActionableValidationError() {
    DescriptionValidationErrors errors = validate();

    assertThat(errors.getFieldErrors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.getField()).isEqualTo("manifests");
              assertThat(error.getCode()).isEqualTo("deployKubernetesManifest.manifests.empty");
              assertThat(error.getDefaultMessage()).contains("manifest artifact resolution");
            });
  }

  @Test
  void emptyManifestListRemainsAValidNoOp() {
    description.setManifests(Collections.emptyList());

    assertThat(validate().hasErrors()).isFalse();
  }

  @Test
  void trailingNullManifestIsIgnored() {
    description.setManifests(Arrays.asList(manifest("allowed"), null));

    assertThat(validate().hasErrors()).isFalse();
  }

  @Test
  void allNullManifestsRemainAValidNoOp() {
    description.setManifests(Collections.singletonList(null));

    assertThat(validate().hasErrors()).isFalse();
  }

  @Test
  void manifestNamespaceIsStillValidated() {
    description.setManifests(List.of(manifest("forbidden")));

    assertThat(validate().hasErrors()).isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  void legacyManifestIsAcceptedWhenManifestListIsMissing() {
    description.setManifest(manifest("allowed"));

    assertThat(validate().hasErrors()).isFalse();
  }

  @Test
  @SuppressWarnings("deprecation")
  void legacyManifestIsValidatedWhenManifestListIsEmpty() {
    description.setManifests(Collections.emptyList());
    description.setManifest(manifest("forbidden"));

    assertThat(validate().hasErrors()).isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  void nonEmptyManifestListTakesPrecedenceOverLegacyManifest() {
    description.setManifests(List.of(manifest("allowed")));
    description.setManifest(manifest("forbidden"));

    assertThat(validate().hasErrors()).isFalse();
  }

  private DescriptionValidationErrors validate() {
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);
    validator.validate(Collections.emptyList(), description, errors);
    return errors;
  }

  private KubernetesManifest manifest(String namespace) {
    KubernetesManifest manifest = new KubernetesManifest();
    manifest.put("metadata", Map.of("namespace", namespace));
    return manifest;
  }
}
