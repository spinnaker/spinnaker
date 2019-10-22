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
package com.netflix.spinnaker.kork.expressions

import org.pf4j.ExtensionPoint

/**
 * Provides a contract for adding new function definitions for SpEL evaluation.
 *
 * The SpEL expression evaluator expects the the function implementations are
 * included in the same concrete class as the [ExpressionFunctionProvider], with
 * method names matching those defined in the [functions] definitions.
 *
 * ```
 *  class HelloFunctionProvider : ExpressionFunctionProvider {
 *    override fun getNamespace(): String? = "netflix"
 *    override fun getFunctions(): Functions =
 *      Functions(
 *        "hello",
 *        FunctionParameter(
 *          Execution::class.java,
 *          "execution",
 *          "The pipeline execution object that this function is being invoked on"
 *        )
 *      )
 *
 *    @JvmStatic
 *    fun hello(execution: Execution): String =
 *      "Hello, ${execution.id}"
 *  }
 * ```
 *
 * The above function provider could then be called in a SpEL expression:
 *
 * ```
 * ${netflix.hello()}
 * ```
 */
interface ExpressionFunctionProvider : ExtensionPoint {
  /**
   * Optional. Typically, a namespace should be provided if you are providing
   * a non-core function. The namespace value typically would be the name of
   * your organization (e.g. `netflix` or `myteamname`.
   */
  val namespace: String?

  /**
   * A collection of [FunctionDefinition]s.
   */
  val functions: Functions

  /**
   * A wrapper for a collection of [FunctionDefinition] objects.
   *
   * @param functionsDefinitions A collection of [FunctionDefinition]
   */
  data class Functions(
    val functionsDefinitions: Collection<FunctionDefinition>
  ) {
    constructor(vararg functionsDefinitions: FunctionDefinition) :
      this(listOf(*functionsDefinitions))
  }

  /**
   * A single function definition. This defines the name and input parameter
   * contract for interacting with the function.
   *
   * @param name The name of the function, without a namespace value.
   * @param parameters A list of [FunctionParameter]s.
   */
  data class FunctionDefinition(
    val name: String,
    val parameters: List<FunctionParameter>
  ) {
    constructor(name: String, vararg functionParameters: FunctionParameter) :
      this(name, listOf(*functionParameters))
  }

  /**
   * The definition of a single function parameter.
   *
   * @param type The parameter value class type.
   * @param name The human-friendly, yet machine-readable, name of the parameter.
   * @param description A user-friendly description of the parameter.
   */
  data class FunctionParameter(
    val type: Class<*>,
    val name: String,
    val description: String
  )
}
