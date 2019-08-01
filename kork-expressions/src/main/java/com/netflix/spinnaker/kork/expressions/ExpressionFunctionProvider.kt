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
package com.netflix.spinnaker.kork.expressions

interface ExpressionFunctionProvider {
  val namespace: String?
  val functions: Functions

  data class Functions(
    val functionsDefinitions: Collection<FunctionDefinition>
  ) {
    constructor(vararg functionsDefinitions: FunctionDefinition) :
      this(listOf(*functionsDefinitions))
  }

  data class FunctionDefinition(
    val name: String,
    val parameters: List<FunctionParameter>
  ) {
    constructor(name: String, vararg functionParameters: FunctionParameter) :
      this(name, listOf(*functionParameters))
  }

  data class FunctionParameter(
    val type: Class<*>,
    val name: String,
    val description: String
  )
}
