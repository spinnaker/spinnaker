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
import com.netflix.spinnaker.kork.expressions.whitelisting.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Provides utility support for SpEL integration Supports registering SpEL functions, ACLs to
 * classes (via whitelisting)
 */
public class ExpressionsSupport {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionsSupport.class);
  private static final ObjectMapper mapper = new ObjectMapper();

  private final Set<Class<?>> allowedReturnTypes;
  private final List<ExpressionFunctionProvider> expressionFunctionProviders;

  public ExpressionsSupport(Class<?> extraAllowedReturnType) {
    this(new Class[] {extraAllowedReturnType}, null);
  }

  public ExpressionsSupport(
      Class<?>[] extraAllowedReturnTypes,
      List<ExpressionFunctionProvider> extraExpressionFunctionProviders) {

    this.allowedReturnTypes =
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
    Collections.addAll(this.allowedReturnTypes, extraAllowedReturnTypes);

    this.expressionFunctionProviders =
        new ArrayList<>(
            Arrays.asList(
                new JsonExpressionFunctionProvider(), new StringExpressionFunctionProvider()));
    if (extraExpressionFunctionProviders != null) {
      this.expressionFunctionProviders.addAll(extraExpressionFunctionProviders);
    }
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
   * @return an evaluation context hooked with helper functions and correct ACL via whitelisting
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
    ReturnTypeRestrictor returnTypeRestrictor = new ReturnTypeRestrictor(allowedReturnTypes);

    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
    evaluationContext.setTypeLocator(new WhitelistTypeLocator());
    evaluationContext.setMethodResolvers(
        Collections.singletonList(new FilteredMethodResolver(returnTypeRestrictor)));
    evaluationContext.setPropertyAccessors(
        Arrays.asList(
            new MapPropertyAccessor(allowUnknownKeys),
            new FilteredPropertyAccessor(returnTypeRestrictor)));

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
            p.getClass(),
            function.getName(),
            functionTypes);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class JsonExpressionFunctionProvider implements ExpressionFunctionProvider {
    @Override
    public String getNamespace() {
      return null;
    }

    @Override
    public Functions getFunctions() {
      return new Functions(
          new FunctionDefinition(
              "toJson",
              new FunctionParameter(
                  Object.class, "value", "An Object to marshall to a JSON String")));
    }

    /**
     * @param o represents an object to convert to json
     * @return json representation of the said object
     */
    public static String toJson(Object o) {
      try {
        String converted = mapper.writeValueAsString(o);
        if (converted != null && converted.contains("${")) {
          throw new SpelHelperFunctionException("result for toJson cannot contain an expression");
        }

        return converted;
      } catch (Exception e) {
        throw new SpelHelperFunctionException(format("#toJson(%s) failed", o.toString()), e);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class StringExpressionFunctionProvider implements ExpressionFunctionProvider {
    @Override
    public String getNamespace() {
      return null;
    }

    @Override
    public Functions getFunctions() {
      return new Functions(
          new FunctionDefinition(
              "toInt",
              new FunctionParameter(String.class, "value", "A String value to convert to an int")),
          new FunctionDefinition(
              "toFloat",
              new FunctionParameter(String.class, "value", "A String value to convert to a float")),
          new FunctionDefinition(
              "toBoolean",
              new FunctionParameter(
                  String.class, "value", "A String value to convert to a boolean")),
          new FunctionDefinition(
              "toBase64",
              new FunctionParameter(String.class, "value", "A String value to base64 encode")),
          new FunctionDefinition(
              "fromBase64",
              new FunctionParameter(
                  String.class, "value", "A base64-encoded String value to decode")),
          new FunctionDefinition(
              "alphanumerical",
              new FunctionParameter(
                  String.class,
                  "value",
                  "A String value to strip of all non-alphanumeric characters")));
    }

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
  }
}
