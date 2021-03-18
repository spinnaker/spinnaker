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

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;

/**
 * Used to bridge AtomicOperations with Sagas.
 *
 * <p>Unfortunately, AtomicOperations and their descriptions are pretty well decoupled from their
 * original input. This makes it difficult to retry operations without re-sending the entire
 * operation ayload.
 */
public interface SagaContextAware {
  void setSagaContext(@Nonnull SagaContext sagaContext);

  @Nullable
  SagaContext getSagaContext();

  @Data
  class SagaContext {
    private String cloudProvider;
    private String descriptionName;
    private Map originalInput;
    private String sagaId;

    public SagaContext(String cloudProvider, String descriptionName, Map originalInput) {
      this.cloudProvider = cloudProvider;
      this.descriptionName = descriptionName;
      this.originalInput = originalInput;
    }
  }
}
