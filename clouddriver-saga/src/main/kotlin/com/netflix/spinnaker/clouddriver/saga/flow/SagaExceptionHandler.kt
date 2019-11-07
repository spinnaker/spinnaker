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

import com.netflix.spinnaker.kork.annotations.Beta
import kotlin.Exception

/**
 * The [SagaExceptionHandler] is an optional interface for implementors to use when determining how to
 * handle an exception thrown during a [SagaFlow].  An example use-case would be if one wants to
 * flag a specific exception as retryable.
 */
@Beta
interface SagaExceptionHandler {
  fun handle(exception: Exception): Exception
}
