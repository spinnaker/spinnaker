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
import com.netflix.spinnaker.clouddriver.saga.models.Saga

/**
 * The completion handler is used as a way of registering Beans as a callback once a particular [Saga]
 * has been completed. Using this allows a Saga to finalize & return data in both successful and failed states.
 * Its results and actions are performed outside of the [Saga] event lifecycle and thus will not be persisted
 * and should not include any logic that has side effects.
 */
@Beta
interface SagaCompletionHandler<T> {
  fun handle(completedSaga: Saga): T?
}
