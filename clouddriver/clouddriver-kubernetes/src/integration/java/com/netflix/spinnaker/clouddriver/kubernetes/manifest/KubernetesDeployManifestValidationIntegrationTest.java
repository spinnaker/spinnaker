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

package com.netflix.spinnaker.clouddriver.kubernetes.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.clouddriver.controllers.OperationsController;
import com.netflix.spinnaker.clouddriver.data.task.InMemoryTaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationException;
import com.netflix.spinnaker.clouddriver.kubernetes.converter.manifest.KubernetesDeployManifestConverter;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.validator.manifest.KubernetesDeployManifestValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationsRegistry;
import com.netflix.spinnaker.clouddriver.orchestration.OperationsService;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KubernetesDeployManifestValidationIntegrationTest {
  @ParameterizedTest
  @ValueSource(strings = {"", ",\"manifests\":null"})
  void missingManifestsReturnBadRequestWithoutStartingAnOperation(String manifestsField)
      throws Exception {
    CredentialsRepository<KubernetesNamedAccountCredentials> credentialsRepository =
        mock(CredentialsRepository.class);
    when(credentialsRepository.getOne("test-account"))
        .thenReturn(mock(KubernetesNamedAccountCredentials.class));
    KubernetesDeployManifestConverter converter =
        new KubernetesDeployManifestConverter(credentialsRepository, null);
    AtomicOperationsRegistry registry = mock(AtomicOperationsRegistry.class);
    when(registry.getAtomicOperationConverter("deployManifest", "kubernetes"))
        .thenReturn(converter);
    when(registry.getAtomicOperationDescriptionValidator("deployManifestValidator", "kubernetes"))
        .thenReturn(new KubernetesDeployManifestValidator());
    DescriptionAuthorizer authorizer = mock(DescriptionAuthorizer.class);
    when(authorizer.supports(any())).thenReturn(true);
    ExceptionMessageDecorator decorator = new ExceptionMessageDecorator(mock(ObjectProvider.class));
    OperationsService service =
        new OperationsService(
            registry,
            List.of(authorizer),
            Optional.empty(),
            mock(AccountCredentialsRepository.class),
            Optional.empty(),
            new NoopRegistry(),
            converter.getObjectMapper(),
            decorator);
    OrchestrationProcessor processor = mock(OrchestrationProcessor.class);
    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(
                new OperationsController(service, processor, new InMemoryTaskRepository(), 5))
            .setControllerAdvice(new GenericExceptionHandlers(decorator))
            .build();

    MvcResult result =
        mockMvc
            .perform(
                post("/kubernetes/ops")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "[{\"deployManifest\":{\"account\":\"test-account\""
                            + manifestsField
                            + "}}]"))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(result.getResolvedException()).isInstanceOf(DescriptionValidationException.class);
    assertThat(
            ((DescriptionValidationException) result.getResolvedException())
                .getAdditionalAttributes())
        .containsEntry(
            "errors",
            List.of("No resolved manifests were provided. Check manifest artifact resolution."));
    verifyNoInteractions(processor);
  }
}
