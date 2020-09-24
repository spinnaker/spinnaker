/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.data.task.SagaId;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionAuthorizer;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationException;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.AbstractSagaAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.sagas.SnapshotAtomicOperationInput.SnapshotAtomicOperationInputCommand;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.saga.persistence.SagaRepository;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.AllowedAccountsValidator;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ResolvableType;
import org.springframework.validation.Errors;

@Slf4j
public class OperationsService {

  private final Splitter COMMA_SPLITTER = Splitter.on(",");

  private final AtomicOperationsRegistry atomicOperationsRegistry;
  private final DescriptionAuthorizer descriptionAuthorizer;
  private final Collection<AllowedAccountsValidator> allowedAccountValidators;
  private final List<AtomicOperationDescriptionPreProcessor>
      atomicOperationDescriptionPreProcessors;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final Optional<SagaRepository> sagaRepository;
  private final Registry registry;
  private final ObjectMapper objectMapper;

  private final Id validationErrorsCounterId;

  public OperationsService(
      AtomicOperationsRegistry atomicOperationsRegistry,
      DescriptionAuthorizer descriptionAuthorizer,
      Optional<Collection<AllowedAccountsValidator>> allowedAccountValidators,
      Optional<List<AtomicOperationDescriptionPreProcessor>>
          atomicOperationDescriptionPreProcessors,
      AccountCredentialsRepository accountCredentialsRepository,
      Optional<SagaRepository> sagaRepository,
      Registry registry,
      ObjectMapper objectMapper) {
    this.atomicOperationsRegistry = atomicOperationsRegistry;
    this.descriptionAuthorizer = descriptionAuthorizer;
    this.allowedAccountValidators = allowedAccountValidators.orElse(Collections.emptyList());
    this.atomicOperationDescriptionPreProcessors =
        atomicOperationDescriptionPreProcessors.orElse(Collections.emptyList());
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.sagaRepository = sagaRepository;
    this.registry = registry;
    this.objectMapper = objectMapper;

    validationErrorsCounterId = registry.createId("validationErrors");
  }

  @Nonnull
  public List<AtomicOperation> collectAtomicOperations(@Nonnull List<Map<String, Map>> inputs) {
    return collectAtomicOperations(null, inputs);
  }

  @Nonnull
  public List<AtomicOperation> collectAtomicOperations(
      @Nullable String cloudProvider, @Nonnull List<Map<String, Map>> inputs) {
    List<AtomicOperationBindingResult> results = convert(cloudProvider, inputs);

    List<AtomicOperation> atomicOperations = new ArrayList<>();
    results.forEach(
        bindingResult -> {
          if (bindingResult.errors.hasErrors()) {
            throw new DescriptionValidationException(bindingResult.errors);
          }
          atomicOperations.add(bindingResult.atomicOperation);
        });
    return atomicOperations;
  }

  private List<AtomicOperationBindingResult> convert(
      @Nullable String cloudProvider, @Nonnull List<Map<String, Map>> inputs) {

    String username = AuthenticatedRequest.getSpinnakerUser().orElse("unknown");
    List<String> allowedAccounts =
        COMMA_SPLITTER.splitToList(AuthenticatedRequest.getSpinnakerAccounts().orElse(""));

    List<Object> descriptions = new ArrayList<>();
    return inputs.stream()
        .flatMap(
            input ->
                input.entrySet().stream()
                    .map(
                        e -> {
                          final String descriptionName = e.getKey();
                          final Map descriptionInput = e.getValue();
                          final OperationInput operationInput =
                              objectMapper.convertValue(descriptionInput, OperationInput.class);
                          final String provider =
                              Optional.ofNullable(cloudProvider)
                                  .orElse(operationInput.cloudProvider);

                          AtomicOperationConverter converter =
                              atomicOperationsRegistry.getAtomicOperationConverter(
                                  descriptionName, provider);

                          // TODO(rz): What if a preprocessor fails due to a downstream error? How
                          // does this affect retrying?
                          Map processedInput =
                              processDescriptionInput(
                                  atomicOperationDescriptionPreProcessors,
                                  converter,
                                  descriptionInput);

                          Object description = converter.convertDescription(processedInput);

                          descriptions.add(description);

                          DescriptionValidationErrors errors =
                              new DescriptionValidationErrors(description);

                          DescriptionValidator validator =
                              atomicOperationsRegistry.getAtomicOperationDescriptionValidator(
                                  DescriptionValidator.getValidatorName(descriptionName), provider);

                          if (validator == null) {
                            String operationName =
                                Optional.ofNullable(description)
                                    .map(it -> it.getClass().getSimpleName())
                                    .orElse("UNKNOWN");
                            log.warn(
                                "No validator found for operation {} and cloud provider {}",
                                operationName,
                                provider);
                          } else {
                            // TODO(rz): Assert description is T
                            validator.validate(descriptions, description, errors);
                          }

                          allowedAccountValidators.forEach(
                              it -> it.validate(username, allowedAccounts, description, errors));

                          // TODO(rz): Assert `description` is T
                          descriptionAuthorizer.authorize(description, errors);

                          // TODO(rz): This is so bad. We convert the description input twice (once
                          // above) and then once inside of this convertOperation procedure. This
                          // means that we do a bunch of serde work twice without needing to.
                          AtomicOperation atomicOperation =
                              converter.convertOperation(processedInput);
                          if (atomicOperation == null) {
                            throw new AtomicOperationNotFoundException(descriptionName);
                          }

                          if (atomicOperation instanceof SagaContextAware) {
                            ((SagaContextAware) atomicOperation)
                                .setSagaContext(
                                    new SagaContextAware.SagaContext(
                                        cloudProvider, descriptionName, descriptionInput));
                          }

                          if (errors.hasErrors()) {
                            registry
                                .counter(
                                    validationErrorsCounterId.withTag(
                                        "operation", atomicOperation.getClass().getSimpleName()))
                                .increment();
                          }

                          return new AtomicOperationBindingResult(atomicOperation, errors);
                        }))
        .collect(Collectors.toList());
  }

  public List<AtomicOperation> collectAtomicOperationsFromSagas(Set<SagaId> sagaIds) {
    if (sagaRepository.isEmpty()) {
      return Collections.emptyList();
    }

    // Resuming a saga-backed AtomicOperation is kind of a pain. This is because AtomicOperations
    // and their descriptions are totally decoupled from their input & description name, so we
    // have to store additional state in the Saga and then use that to reconstruct
    // AtomicOperations. It'd make sense to refactor all of this someday.
    List<Object> seenDescriptions = new ArrayList<>();
    return sagaIds.stream()
        .map(id -> sagaRepository.get().get(id.getName(), id.getId()))
        .filter(Objects::nonNull)
        .filter(it -> !it.isComplete())
        .map(
            saga ->
                new SagaAndSnapshot(saga, saga.getEvent(SnapshotAtomicOperationInputCommand.class)))
        .filter(
            it -> {
              // Reduce the list of sagas attached to the task to one for each uniquely submitted
              // description. This is probably unnecessary long-term.
              if (seenDescriptions.contains(it.getSnapshot().getDescription())) {
                return false;
              }
              seenDescriptions.add(it.getSnapshot().getDescription());
              return true;
            })
        .flatMap(
            saga -> {
              List<AtomicOperationBindingResult> bindingResult =
                  convert(
                      saga.getSnapshot().getCloudProvider(),
                      Collections.singletonList(
                          Collections.singletonMap(
                              saga.getSnapshot().getDescriptionName(),
                              saga.getSnapshot().getDescriptionInput())));

              // We need to ensure the encapsulated saga instance gets the same ID.
              return bindingResult.stream()
                  .map(this::atomicOperationOrError)
                  .peek(
                      it -> {
                        if (it instanceof AbstractSagaAtomicOperation) {
                          // The saga context is always going to be set by this point, but y'know...
                          // safety. This should be done when the context is created, but I don't
                          // want to go down the path of refactoring the mess in `convert`, however
                          // it should be, so that the class is actually unit testable.
                          AbstractSagaAtomicOperation<?, ?, ?> op =
                              (AbstractSagaAtomicOperation<?, ?, ?>) it;
                          Optional.ofNullable(op.getSagaContext())
                              .ifPresent(context -> context.setSagaId(saga.getSaga().getId()));
                        }
                      });
            })
        .collect(Collectors.toList());
  }

  private AtomicOperation atomicOperationOrError(AtomicOperationBindingResult bindingResult) {
    if (bindingResult.errors.hasErrors()) {
      throw new DescriptionValidationException(bindingResult.errors);
    }
    return bindingResult.atomicOperation;
  }

  /**
   * Runs the provided descriptionInput through preprocessors.
   *
   * <p>Which preprocessors are used is determined by doing some reflection on the
   * AtomicOperationConverter's return type.
   */
  private static Map processDescriptionInput(
      Collection<AtomicOperationDescriptionPreProcessor> descriptionPreProcessors,
      AtomicOperationConverter converter,
      Map descriptionInput) {

    Method convertDescriptionMethod;
    try {
      convertDescriptionMethod = converter.getClass().getMethod("convertDescription", Map.class);
    } catch (NoSuchMethodException e) {
      throw new SystemException("Could not find convertDescription method on converter", e);
    }

    Class<?> convertDescriptionReturnType =
        ResolvableType.forMethodReturnType(convertDescriptionMethod).getRawClass();

    for (AtomicOperationDescriptionPreProcessor preProcessor : descriptionPreProcessors) {
      if (preProcessor.supports(convertDescriptionReturnType)) {
        descriptionInput = preProcessor.process(descriptionInput);
      }
    }

    return descriptionInput;
  }

  @Value
  public static class AtomicOperationBindingResult {
    private AtomicOperation atomicOperation;
    private Errors errors;
  }

  @Data
  private static class OperationInput {
    @Nullable private String credentials;
    @Nullable private String accountName;
    @Nullable private String account;
    @Nullable private String cloudProvider;

    @Nullable
    public String computeAccountName() {
      return Optional.ofNullable(credentials)
          .orElse(Optional.ofNullable(accountName).orElse(account));
    }
  }

  @Data
  @AllArgsConstructor
  private static class SagaAndSnapshot {
    Saga saga;
    SnapshotAtomicOperationInputCommand snapshot;
  }
}
