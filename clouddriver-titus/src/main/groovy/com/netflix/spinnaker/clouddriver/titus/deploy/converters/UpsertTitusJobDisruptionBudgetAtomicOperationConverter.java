/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.converters;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusOperation;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertJobDisruptionBudgetDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.ops.UpsertTitusJobDisruptionBudgetAtomicOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@TitusOperation(AtomicOperations.UPSERT_DISRUPTION_BUDGET)
@Component
class UpsertTitusJobDisruptionBudgetAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final TitusClientProvider titusClientProvider;
  private ObjectMapper objectMapper;

  @Autowired
  UpsertTitusJobDisruptionBudgetAtomicOperationConverter(TitusClientProvider titusClientProvider, ObjectMapper objectMapper) {
    this.titusClientProvider = titusClientProvider;
    this.objectMapper = objectMapper;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new UpsertTitusJobDisruptionBudgetAtomicOperation(titusClientProvider, convertDescription(input));
  }

  @Override
  public UpsertJobDisruptionBudgetDescription convertDescription(Map input) {
    UpsertJobDisruptionBudgetDescription converted = objectMapper.convertValue(input, UpsertJobDisruptionBudgetDescription.class);
    converted.setCredentials(getCredentialsObject(input.get("credentials").toString()));
    return converted;
  }
}
