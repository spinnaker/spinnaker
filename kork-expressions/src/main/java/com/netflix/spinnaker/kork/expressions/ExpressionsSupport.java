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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.expressions.whitelisting.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Provides utility support for SpEL integration Supports registering SpEL functions, ACLs to
 * classes (via whitelisting)
 */
public class ExpressionsSupport {
  private static final ObjectMapper mapper = new ObjectMapper();
  private final Logger LOGGER = LoggerFactory.getLogger(ExpressionsSupport.class);
  private final Map<String, List<Class<?>>> registeredHelperFunctions = new HashMap<>();
  private final Set<Class<?>> allowedReturnTypes;

  public ExpressionsSupport(Class<?>... extraAllowedReturnTypes) {
    this.registeredHelperFunctions.put("toJson", Collections.singletonList(Object.class));
    Stream.of(
            "alphanumerical", "readJson", "toInt", "toFloat", "toBoolean", "toBase64", "fromBase64")
        .forEach(
            fn -> {
              this.registeredHelperFunctions.put(fn, Collections.singletonList(String.class));
            });

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
  }

  /** Internally registers a SpEL method to an evaluation context */
  private static void registerFunction(
      StandardEvaluationContext context, String name, Class<?>... types)
      throws NoSuchMethodException {
    context.registerFunction(name, ExpressionsSupport.class.getDeclaredMethod(name, types));
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

  /*
   * HELPER FUNCTIONS: These functions are explicitly registered with each invocation To add a new
   * helper function, append the function below and update ExpressionHelperFunctions and
   * registeredHelperFunctions
   */

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
      throw new SpelHelperFunctionException(String.format("#toJson(%s) failed", o.toString()), e);
    }
  }

  /**
   * Attemps to read json from a text String. Will throw a parsing exception on bad json
   *
   * @param text text to read as json
   * @return the json representation of the text
   */
  public static Object readJson(String text) {
    try {
      if (text.startsWith("[")) {
        return mapper.readValue(text, List.class);
      }

      return mapper.readValue(text, Map.class);
    } catch (Exception e) {
      throw new SpelHelperFunctionException(String.format("#readJson(%s) failed", text), e);
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
    ReturnTypeRestrictor returnTypeRestrictor = new ReturnTypeRestrictor(allowedReturnTypes);

    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
    evaluationContext.setTypeLocator(new WhitelistTypeLocator());
    evaluationContext.setMethodResolvers(
        Collections.singletonList(new FilteredMethodResolver(returnTypeRestrictor)));
    evaluationContext.setPropertyAccessors(
        Arrays.asList(
            new MapPropertyAccessor(allowUnknownKeys),
            new FilteredPropertyAccessor(returnTypeRestrictor)));

    try {
      for (Map.Entry<String, List<Class<?>>> m : registeredHelperFunctions.entrySet()) {
        registerFunction(evaluationContext, m.getKey(), m.getValue().toArray(new Class<?>[0]));
      }
    } catch (NoSuchMethodException e) {
      // Indicates a function was not properly registered. This should not happen. Please fix the
      // faulty
      // function
      LOGGER.error("Failed to register helper functions for rootObject {}", rootObject, e);
    }

    return evaluationContext;
  }
}
