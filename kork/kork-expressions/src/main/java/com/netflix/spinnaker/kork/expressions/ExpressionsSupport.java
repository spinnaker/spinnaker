/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.kork.expressions;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.entities.EntityPropertyAccessor;
import com.netflix.spinnaker.kork.expressions.allowlist.AllowListTypeLocator;
import com.netflix.spinnaker.kork.expressions.allowlist.FilteredMethodResolver;
import com.netflix.spinnaker.kork.expressions.allowlist.FilteredPropertyAccessor;
import com.netflix.spinnaker.kork.expressions.allowlist.MapPropertyAccessor;
import com.netflix.spinnaker.kork.expressions.allowlist.ReturnTypeRestrictor;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import com.netflix.spinnaker.kork.expressions.functions.ArtifactStoreFunctions;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Provides utility support for SpEL integration Supports registering SpEL functions, ACLs to
 * classes (via allow list)
 */
public class ExpressionsSupport {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionsSupport.class);
  private static final ObjectMapper mapper = new ObjectMapper();

  private final Set<Class<?>> allowedReturnTypes;
  private final List<ExpressionFunctionProvider> expressionFunctionProviders;
  private final ExpressionProperties expressionProperties;
  private final EntityPropertyAccessor entityPropertyAccessor;
  private final FilteredPropertyAccessor filteredPropertyAccessor;
  private final FilteredMethodResolver filteredMethodResolver;
  private final MapPropertyAccessor falseMapPropertyAccessor;
  private final MapPropertyAccessor trueMapPropertyAccessor;
  private final AllowListTypeLocator allowListTypeLocator;

  public ExpressionsSupport(
      Class<?> extraAllowedReturnType, ExpressionProperties expressionProperties) {
    this(new Class[] {extraAllowedReturnType}, null, null, expressionProperties);
  }

  public ExpressionsSupport(
      Class<?>[] extraAllowedReturnTypes,
      List<ExpressionFunctionProvider> extraExpressionFunctionProviders,
      PluginManager pluginManager,
      ExpressionProperties expressionProperties) {
    this.expressionProperties = expressionProperties;

    allowedReturnTypes =
        new HashSet<>(
            Arrays.asList(
                Collection.class,
                Map.class,
                SortedMap.class,
                List.class,
                Set.class,
                SortedSet.class,
                ArrayList.class,
                LinkedList.class,
                HashSet.class,
                LinkedHashSet.class,
                HashMap.class,
                LinkedHashMap.class,
                TreeMap.class,
                TreeSet.class));
    Collections.addAll(allowedReturnTypes, extraAllowedReturnTypes);

    expressionFunctionProviders =
        new ArrayList<>(
            Arrays.asList(
                new ArtifactStoreFunctions(),
                new JsonExpressionFunctionProvider(),
                new StringExpressionFunctionProvider()));

    if (extraExpressionFunctionProviders != null) {
      expressionFunctionProviders.addAll(extraExpressionFunctionProviders);
    }

    // TODO(rz): Once plugins are no longer an incubating feature, extraExpressionFunctionProviders
    //  var could be removed
    if (pluginManager != null) {
      expressionFunctionProviders.addAll(
          pluginManager.getExtensions(ExpressionFunctionProvider.class));
    }

    if (expressionProperties.getDoNotEvalSpel().isEnabled()) {
      allowedReturnTypes.add(NotEvaluableExpression.class);
      expressionFunctionProviders.add(new FlowExpressionFunctionProvider());
    }
    ReturnTypeRestrictor returnTypeRestrictor = new ReturnTypeRestrictor(allowedReturnTypes);
    this.entityPropertyAccessor =
        new EntityPropertyAccessor(
            this.expressionProperties.getPropertyExpansionTypes(),
            this.expressionProperties.getAggressiveExpansionKeys());
    this.filteredPropertyAccessor = new FilteredPropertyAccessor(returnTypeRestrictor);
    this.filteredMethodResolver = new FilteredMethodResolver(returnTypeRestrictor);
    this.falseMapPropertyAccessor = new MapPropertyAccessor(false);
    this.trueMapPropertyAccessor = new MapPropertyAccessor(true);
    this.allowListTypeLocator = new AllowListTypeLocator();
  }

  public List<ExpressionFunctionProvider> getExpressionFunctionProviders() {
    return expressionFunctionProviders;
  }

  private static void registerFunction(
      StandardEvaluationContext context,
      String registrationName,
      Class<?> cls,
      String methodName,
      Class<?>... types) {
    try {
      context.registerFunction(registrationName, cls.getDeclaredMethod(methodName, types));
    } catch (NoSuchMethodException e) {
      LOGGER.error("Failed to register helper function", e);
      throw new RuntimeException(
          "Failed to register helper function '"
              + registrationName
              + "' from '"
              + cls.getName()
              + "#"
              + methodName
              + "'",
          e);
    }
  }

  /**
   * Creates a configured SpEL evaluation context
   *
   * @param rootObject the root object to transform
   * @param allowUnknownKeys flag to control what helper functions are available
   * @return an evaluation context hooked with helper functions and correct ACL via allow list
   */
  public StandardEvaluationContext buildEvaluationContext(
      Object rootObject, boolean allowUnknownKeys) {
    StandardEvaluationContext evaluationContext =
        createEvaluationContext(rootObject, allowUnknownKeys);

    registerExpressionProviderFunctions(evaluationContext);

    return evaluationContext;
  }

  private StandardEvaluationContext createEvaluationContext(
      Object rootObject, boolean allowUnknownKeys) {

    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
    evaluationContext.setTypeLocator(allowListTypeLocator);
    evaluationContext.setTypeConverter(
        new ArtifactUriToReferenceConverter(ArtifactStore.getInstance()));

    evaluationContext.setMethodResolvers(Collections.singletonList(filteredMethodResolver));
    evaluationContext.setPropertyAccessors(
        Arrays.asList(
            entityPropertyAccessor,
            allowUnknownKeys ? trueMapPropertyAccessor : falseMapPropertyAccessor,
            filteredPropertyAccessor));

    return evaluationContext;
  }

  private void registerExpressionProviderFunctions(StandardEvaluationContext evaluationContext) {
    for (ExpressionFunctionProvider p : expressionFunctionProviders) {
      for (ExpressionFunctionProvider.FunctionDefinition function :
          p.getFunctions().getFunctionsDefinitions()) {
        String namespacedFunctionName = function.getName();
        if (p.getNamespace() != null) {
          namespacedFunctionName = format("%s_%s", p.getNamespace(), namespacedFunctionName);
        }

        Class[] functionTypes =
            function.getParameters().stream()
                .map(ExpressionFunctionProvider.FunctionParameter::getType)
                .toArray(Class[]::new);

        registerFunction(
            evaluationContext,
            namespacedFunctionName,
            p.getExtensionClass(),
            function.getName(),
            functionTypes);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class FlowExpressionFunctionProvider implements ExpressionFunctionProvider {
    /**
     * @param o represents an object to restrict expressions evaluation
     * @return not evaluable expression
     */
    public static NotEvaluableExpression doNotEval(Object o) {
      return new NotEvaluableExpression(o);
    }

    @Override
    public String getNamespace() {
      return null;
    }

    @Override
    public Functions getFunctions() {
      return new Functions(
          new FunctionDefinition(
              "doNotEval",
              "Restrict expressions evaluation for an object",
              new FunctionParameter(
                  Object.class, "value", "An object to restrict expressions evaluation")));
    }
  }

  @SuppressWarnings("unused")
  public static class JsonExpressionFunctionProvider implements ExpressionFunctionProvider {
    /**
     * @param o represents an object to convert to json
     * @return json representation of the said object
     */
    public static String toJson(Object o) {
      try {
        if (o instanceof NotEvaluableExpression) {
          return mapper.writeValueAsString(((NotEvaluableExpression) o).getExpression());
        }

        String converted = mapper.writeValueAsString(o);
        if (converted != null && converted.contains("${")) {
          throw new SpelHelperFunctionException("result for toJson cannot contain an expression");
        }

        return converted;
      } catch (Exception e) {
        throw new SpelHelperFunctionException(format("#toJson(%s) failed", o.toString()), e);
      }
    }

    @Override
    public String getNamespace() {
      return null;
    }

    @Override
    public Functions getFunctions() {
      return new Functions(
          new FunctionDefinition(
              "toJson",
              "Converts an object to JSON string",
              new FunctionParameter(
                  Object.class, "value", "An Object to marshall to a JSON String")));
    }
  }

  @SuppressWarnings("unused")
  public static class StringExpressionFunctionProvider implements ExpressionFunctionProvider {
    /**
     * Parses a string to an integer
     *
     * @param str represents an int
     * @return an integer
     */
    public static Integer toInt(String str) {
      return Integer.valueOf(str);
    }

    /**
     * Parses a string to a float
     *
     * @param str represents an float
     * @return an float
     */
    public static Float toFloat(String str) {
      return Float.valueOf(str);
    }

    /**
     * Parses a string to a boolean
     *
     * @param str represents an boolean
     * @return a boolean
     */
    public static Boolean toBoolean(String str) {
      return Boolean.valueOf(str);
    }

    /**
     * Encodes a string to base64
     *
     * @param text plain string
     * @return converted string
     */
    public static String toBase64(String text) {
      return Base64.getEncoder().encodeToString(text.getBytes());
    }

    /**
     * Attempts to decode a base64 string
     *
     * @param text plain string
     * @return decoded string
     */
    public static String fromBase64(String text) {
      return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    /**
     * Converts a String to alpha numeric
     *
     * @param str string to convert
     * @return converted string
     */
    public static String alphanumerical(String str) {
      return str.replaceAll("[^A-Za-z0-9]", "");
    }

    @Override
    public String getNamespace() {
      return null;
    }

    @Override
    public Functions getFunctions() {
      return new Functions(
          new FunctionDefinition(
              "toInt",
              "Converts a string to integer",
              new FunctionParameter(String.class, "value", "A String value to convert to an int")),
          new FunctionDefinition(
              "toFloat",
              "Converts a string to float",
              new FunctionParameter(String.class, "value", "A String value to convert to a float")),
          new FunctionDefinition(
              "toBoolean",
              "Converts a string value to boolean",
              new FunctionParameter(
                  String.class, "value", "A String value to convert to a boolean")),
          new FunctionDefinition(
              "toBase64",
              "Encodes a string to base64 string",
              new FunctionParameter(String.class, "value", "A String value to base64 encode")),
          new FunctionDefinition(
              "fromBase64",
              "Decodes a base64 string",
              new FunctionParameter(
                  String.class, "value", "A base64-encoded String value to decode")),
          new FunctionDefinition(
              "alphanumerical",
              "Removes all non-alphanumeric characters from a string",
              new FunctionParameter(
                  String.class,
                  "value",
                  "A String value to strip of all non-alphanumeric characters")));
    }
  }
}
