/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.api.expressions;

import com.netflix.spinnaker.kork.annotations.DeprecationInfo;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Provides a contract for adding new function definitions for SpEL evaluation.
 *
 * <p>The SpEL expression evaluator expects the the function implementations are included in the
 * same concrete class as the {@link ExpressionFunctionProvider}, with method names matching those
 * defined in the {@code getFunctions()} definitions.
 *
 * <pre>{@code
 * class HelloFunctionProvider : ExpressionFunctionProvider {
 *   override fun getNamespace(): String? = "netflix"
 *   override fun getFunctions(): Functions =
 *     Functions(
 *       "hello",
 *       FunctionParameter(
 *         Execution::class.java,
 *         "execution",
 *         "The pipeline execution object that this function is being invoked on"
 *       )
 *     )
 *
 *   @JvmStatic
 *   fun hello(execution: Execution): String =
 *     "Hello, ${execution.id}"
 * }
 * }</pre>
 *
 * The above function provider could then be called in a SpEL expression:
 *
 * <p>{@code ${netflix.hello()}}
 */
public interface ExpressionFunctionProvider extends SpinnakerExtensionPoint {

  /**
   * Optional. Typically, a namespace should be provided if you are providing a non-core function.
   * The namespace value typically would be the name of your organization (e.g. {@code netflix} or
   * {@code myteamname}.
   */
  @Nullable
  String getNamespace();

  /** A collection of {@link FunctionDefinition}s. */
  Functions getFunctions();

  /** A wrapper for a collection of {@link FunctionDefinition} objects. */
  class Functions {

    /** A collection of {@link FunctionDefinition}. */
    private final Collection<FunctionDefinition> functionsDefinitions;

    public Functions(Collection<FunctionDefinition> functionsDefinitions) {
      this.functionsDefinitions = functionsDefinitions;
    }

    public Functions(FunctionDefinition... functionDefinitions) {
      this(Arrays.asList(functionDefinitions));
    }

    public Collection<FunctionDefinition> getFunctionsDefinitions() {
      return functionsDefinitions;
    }
  }

  /**
   * A single function definition. This defines the name and input parameter contract for
   * interacting with the function.
   */
  class FunctionDefinition {

    /** The name of the function, without a namespace value. */
    private final String name;

    /** Developer-friendly description of the function. */
    private final String description;

    /** A list of {@link FunctionParameter}s. */
    private final List<FunctionParameter> parameters;

    /** End-user friendly documentation of the function, will be surfaced to the UI via Deck. */
    @Nullable private final FunctionDocumentation documentation;

    @Deprecated
    @DeprecationInfo(
        reason = "Please use the overload with description",
        since = "1.18",
        eol = "1.25",
        replaceWith = "FunctionDefinition(name, \"\", functionParameters)")
    public FunctionDefinition(String name, FunctionParameter... functionParameters) {
      this(name, "", Arrays.asList(functionParameters));
    }

    public FunctionDefinition(
        String name,
        String description,
        List<FunctionParameter> parameters,
        @Nullable FunctionDocumentation documentation) {
      this.name = name;
      this.description = description;
      this.parameters = parameters;
      this.documentation = documentation;
    }

    public FunctionDefinition(String name, String description, FunctionParameter... parameters) {
      this(name, description, Arrays.asList(parameters));
    }

    public FunctionDefinition(String name, String description, List<FunctionParameter> parameters) {
      this(name, description, parameters, null);
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public List<FunctionParameter> getParameters() {
      return parameters;
    }

    @Nullable
    public FunctionDocumentation getDocumentation() {
      return documentation;
    }
  }

  /** The definition of a single function parameter. */
  class FunctionParameter {

    /** The parameter value class type. */
    private final Class<?> type;

    /** The human-friendly, yet machine-readable, name of the parameter. */
    private final String name;

    /** A user-friendly description of the parameter. */
    private final String description;

    public FunctionParameter(Class<?> type, String name, String description) {
      this.type = type;
      this.name = name;
      this.description = description;
    }

    public Class<?> getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * Documentation for a function.
   *
   * <p>This documentation is used by Deck to display in-line docs for a SpEL function.
   */
  class FunctionDocumentation {

    /** Documentation text. Can contain Markdown. */
    private final String documentation;

    /** List of example usages of the function. */
    @Nullable private final List<FunctionUsageExample> examples;

    public FunctionDocumentation(String documentation) {
      this.documentation = documentation;
      this.examples = null;
    }

    public FunctionDocumentation(String documentation, List<FunctionUsageExample> examples) {
      this.documentation = documentation;
      this.examples = examples;
    }

    public FunctionDocumentation(String documentation, FunctionUsageExample... examples) {
      this.documentation = documentation;
      this.examples = Arrays.asList(examples);
    }

    public String getDocumentation() {
      return documentation;
    }

    @Nullable
    public List<FunctionUsageExample> getExamples() {
      return examples;
    }
  }

  /**
   * Function usage example.
   *
   * <p>This is used by Deck to display in-line docs for a SpEL function.
   */
  class FunctionUsageExample {

    /** Example usage, e.g. "#stage('bake in us-east-1').hasSucceeded" */
    private final String usage;

    /**
     * Explanation of the usage sample. Markdown is supported.
     *
     * <p>e.g. "checks if the bake stage has completed successfully"
     */
    private final String description;

    public FunctionUsageExample(String usage, String description) {
      this.usage = usage;
      this.description = description;
    }

    public String getUsage() {
      return usage;
    }

    public String getDescription() {
      return description;
    }
  }
}
