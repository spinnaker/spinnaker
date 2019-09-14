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

package com.netflix.spinnaker.clouddriver.saga

import com.fasterxml.jackson.annotation.JsonTypeName

/**
 * Get the name of the provided [command] instance.
 *
 */
internal fun getStepCommandName(command: SagaCommand): String =
  getStepCommandName(command.javaClass)

/**
 * Get the name of the provided [commandClass].
 *
 * TODO(rz): Do we want our own annotation instead of relying on [JsonTypeName]?
 */
internal fun getStepCommandName(commandClass: Class<SagaCommand>): String =
  commandClass.getAnnotation(JsonTypeName::class.java)?.value ?: commandClass.simpleName
