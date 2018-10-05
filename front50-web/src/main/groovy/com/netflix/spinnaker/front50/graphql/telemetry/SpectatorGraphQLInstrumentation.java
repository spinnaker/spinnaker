/*
 * Copyright 2018 Netflix, Inc.
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
 */
package com.netflix.spinnaker.front50.graphql.telemetry;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.*;
import graphql.language.Document;
import graphql.validation.ValidationError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Component
public class SpectatorGraphQLInstrumentation implements Instrumentation {

  private final Registry registry;

  @Autowired
  public SpectatorGraphQLInstrumentation(Registry registry) {
    this.registry = registry;
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
    long startTime = currentTimeMillis();
    return (result, t) -> {
      String resultTag = result.getErrors().isEmpty() ? "success" : "failure";
      Id invocationId = registry.createId(format("graphql.%s.invocations", parameters.getOperation()))
        .withTag("result", resultTag);
      Id timingId = registry.createId(format("graphql.%s.timing", parameters.getOperation()))
        .withTag("result", resultTag);

      registry.counter(invocationId).increment();
      registry.timer(timingId).record(currentTimeMillis() - startTime, MILLISECONDS);
    };
  }

  @Override
  public InstrumentationContext<Document> beginParse(InstrumentationExecutionParameters parameters) {
    return (result, t) -> { };
  }

  @Override
  public InstrumentationContext<List<ValidationError>> beginValidation(InstrumentationValidationParameters parameters) {
    return (result, t) -> { };
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginDataFetch(InstrumentationDataFetchParameters parameters) {
    return (result, t) -> { };
  }

  @Override
  public InstrumentationContext<CompletableFuture<ExecutionResult>> beginExecutionStrategy(InstrumentationExecutionStrategyParameters parameters) {
    return (result, t) -> { };
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginField(InstrumentationFieldParameters parameters) {
    return (result, t) -> { };
  }

  @Override
  public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
    return (result, t) -> { };
  }
}
