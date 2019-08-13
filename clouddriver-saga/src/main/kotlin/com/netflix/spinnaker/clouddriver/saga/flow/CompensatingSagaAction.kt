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
package com.netflix.spinnaker.clouddriver.saga.flow

import com.google.common.annotations.Beta
import com.netflix.spinnaker.clouddriver.saga.SagaCommand
import com.netflix.spinnaker.clouddriver.saga.SagaRollbackCommand
import com.netflix.spinnaker.clouddriver.saga.models.Saga

/**
 * A [SagaAction] that has a companion [rollback] method.
 */
@Beta
interface CompensatingSagaAction<T : SagaCommand, R : SagaRollbackCommand> : SagaAction<T> {
  fun rollback(command: R, saga: Saga): SagaAction.Result
}
