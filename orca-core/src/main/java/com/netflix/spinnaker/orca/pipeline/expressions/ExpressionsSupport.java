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

package com.netflix.spinnaker.orca.pipeline.expressions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.expressions.whitelisting.FilteredMethodResolver;
import com.netflix.spinnaker.orca.pipeline.expressions.whitelisting.FilteredPropertyAccessor;
import com.netflix.spinnaker.orca.pipeline.expressions.whitelisting.MapPropertyAccessor;
import com.netflix.spinnaker.orca.pipeline.expressions.whitelisting.WhitelistTypeLocator;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import com.netflix.spinnaker.orca.pipeline.util.HttpClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Provides utility support for SPEL integration
 * Supports registering SPEL functions, ACLs to classes (via whitelisting)
 */
public class ExpressionsSupport {
  final static ParserContext parserContext = new TemplateParserContext("${", "}");
  private static final Logger LOGGER = LoggerFactory.getLogger(ExpressionsSupport.class);
  private static final ObjectMapper mapper = new ObjectMapper();
  private static AtomicReference<ContextFunctionConfiguration> helperFunctionConfigurationAtomicReference = new AtomicReference<>();
  private static Map<String, List<Class<?>>> registeredHelperFunctions = new HashMap<>();

  private static List<String> DEPLOY_STAGE_NAMES = Arrays.asList("deploy", "createServerGroup", "cloneServerGroup", "rollingPush");

  ExpressionsSupport(ContextFunctionConfiguration contextFunctionConfiguration) {
    helperFunctionConfigurationAtomicReference.set(contextFunctionConfiguration);
  }

  static {
    registeredHelperFunctions.put("alphanumerical", Collections.singletonList(String.class));
    registeredHelperFunctions.put("toJson", Collections.singletonList(Object.class));
    registeredHelperFunctions.put("readJson", Collections.singletonList(String.class));
    registeredHelperFunctions.put("toInt", Collections.singletonList(String.class));
    registeredHelperFunctions.put("toFloat", Collections.singletonList(String.class));
    registeredHelperFunctions.put("toBoolean", Collections.singletonList(String.class));
    registeredHelperFunctions.put("toBase64", Collections.singletonList(String.class));
    registeredHelperFunctions.put("fromBase64", Collections.singletonList(String.class));
  }

  /**
   * Internally registers a Spel method to an evaluation context
   */
  private static void registerFunction(StandardEvaluationContext context, String name, Class<?> ...types) throws NoSuchMethodException {
    context.registerFunction(name, ExpressionsSupport.class.getDeclaredMethod(name, types));
  }

  /**
   * Creates a configured Spel evaluation context
   * @param rootObject the root object to transform
   * @param allowUnknownKeys flag to control what helper functions are available
   * @return an evaluation context hooked with helper functions and correct ACL via whitelisting
   */
  public static StandardEvaluationContext newEvaluationContext(Object rootObject, boolean allowUnknownKeys) {
    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(rootObject);
    evaluationContext.setTypeLocator(new WhitelistTypeLocator());
    evaluationContext.setMethodResolvers(Collections.singletonList(new FilteredMethodResolver()));
    evaluationContext.setPropertyAccessors(Arrays.asList(new MapPropertyAccessor(allowUnknownKeys), new FilteredPropertyAccessor()));

    try {
      for (Map.Entry<String, List<Class<?>>> m : registeredHelperFunctions.entrySet()) {
        registerFunction(evaluationContext, m.getKey(), m.getValue().toArray(new Class<?>[m.getValue().size()]));
      }

      if (allowUnknownKeys) {
        // lazily function registering
        registerFunction(evaluationContext, "fromUrl", String.class);
        registerFunction(evaluationContext, "jsonFromUrl", String.class);
        registerFunction(evaluationContext, "propertiesFromUrl", String.class);
        registerFunction(evaluationContext, "stage", Object.class, String.class);
        registerFunction(evaluationContext, "stageExists", Object.class, String.class);
        registerFunction(evaluationContext, "judgment", Object.class, String.class);
        registerFunction(evaluationContext, "judgement", Object.class, String.class);
        registerFunction(evaluationContext, "deployedServerGroups", Object.class, String[].class);
      }
    } catch (NoSuchMethodException e) {
      // Indicates a function was not properly registered. This should not happen. Please fix the faulty function
      LOGGER.error("Failed to register helper functions for rootObject {}", rootObject, e);
    }

    return evaluationContext;
  }

  /*
    HELPER FUNCTIONS: These functions are explicitly registered with each invocation
    To add a new helper function, append the function below and update ExpressionHelperFunctions and registeredHelperFunctions
   */

  /**
   * Parses a string to an integer
   * @param str represents an int
   * @return an integer
   */
  public static Integer toInt(String str) {
    return Integer.valueOf(str);
  }

  /**
   * Parses a string to a float
   * @param str represents an float
   * @return an float
   */
  public static Float toFloat(String str) {
    return Float.valueOf(str);
  }

  /**
   * Parses a string to a boolean
   * @param str represents an boolean
   * @return a boolean
   */
  public static Boolean toBoolean(String str) {
    return Boolean.valueOf(str);
  }

  /**
   * Encodes a string to base64
   * @param text plain string
   * @return converted string
   */
  public static String toBase64(String text) {
    return Base64.getEncoder().encodeToString(text.getBytes());
  }

  /**
   * Attempts to decode a base64 string
   * @param text plain string
   * @return decoded string
   */
  public static String fromBase64(String text) throws UnsupportedEncodingException {
    return new String(Base64.getDecoder().decode(text), "UTF-8");
  }

  /**
   * Converts a String to alpha numeric
   * @param str string to convert
   * @return converted string
   */
  static String alphanumerical(String str) {
    return str.replaceAll("[^A-Za-z0-9]", "");
  }

  /**
   * @param o represents an object to convert to json
   * @return json representation of the said object
   */
  static String toJson(Object o) {
    try {
      String converted = mapper.writeValueAsString(o);
      if (converted!= null && converted.contains(parserContext.getExpressionPrefix())) {
        throw new SpelHelperFunctionException("result for toJson cannot contain an expression");
      }

      return converted;
    } catch (Exception e) {
      throw new SpelHelperFunctionException(String.format("#toJson(%s) failed", o.toString()), e);
    }
  }

  /**
   * Returns the text response from url
   * @param url used to perform the http get response
   * @return string result from get request
   */
  static String fromUrl(String url) {
    try {
      URL u = helperFunctionConfigurationAtomicReference.get().getUrlRestrictions().validateURI(url).toURL();
      return HttpClientUtils.httpGetAsString(u.toString());
    } catch (Exception e) {
      throw new SpelHelperFunctionException(String.format("#from(%s) failed", url), e);
    }
  }

  /**
   * Attemps to read json from a text String. Will throw a parsing exception on bad json
   * @param text text to read as json
   * @return the json representation of the text
   */
  static Object readJson(String text) {
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
   * Reads a json text
   * @param url url to get the json text
   * @return an object representing the json object
   */
  static Object jsonFromUrl(String url) {
    return readJson(fromUrl(url));
  }

  /**
   * Reads a properties file stored at a url
   * @param url the location of the properties file
   * @return a hashmap representing the properties file
   */
  static Map propertiesFromUrl(String url) {
    try {
      return readProperties(fromUrl(url));
    } catch (Exception e) {
      throw new SpelHelperFunctionException(String.format("#propertiesFromUrl(%s) failed", url), e);
    }
  }

  /**
   * Reads properties from a text
   * @param text text
   * @return a hashmap of the key-value pairs in the text
   * @throws IOException
   */
  static Map readProperties(String text) throws IOException {
    Map map = new HashMap();
    Properties properties = new Properties();
    properties.load(new ByteArrayInputStream(text.getBytes()));
    map.putAll(properties);
    return map;
  }

  /**
   * Finds a Stage by id
   * @param obj #root.execution
   * @param id the name or id of the stage to find
   * @return a stage specified by id
   */
  static Object stage(Object obj, String id) {
    if (obj instanceof Execution) {
      Execution execution = (Execution) obj;
      return execution.getStages()
        .stream()
        .filter(i -> id != null && (id.equals(i.getName()) || id.equals(i.getId())))
        .findFirst()
        .orElseThrow(
          () -> new SpelHelperFunctionException(
            String.format("Unable to locate [%s] using #stage(%s) in execution %s", id, id, execution.getId())
          )
        );
    }

    throw new SpelHelperFunctionException(String.format("Invalid first param to #stage(%s). must be an execution", id));
  }

  /**
   * Checks existence of a Stage by id
   * @param obj #root.execution
   * @param id the name or id of the stage to check existence
   * @return W
   */
  static boolean stageExists(Object obj, String id) {
    if (obj instanceof Execution) {
      Execution execution = (Execution) obj;
      return execution.getStages()
        .stream()
        .anyMatch(i -> id != null && (id.equals(i.getName()) || id.equals(i.getId())));
    }

    throw new SpelHelperFunctionException(String.format("Invalid first param to #stage(%s). must be an execution", id));
  }

  /**
   * Finds a stage by id and returns the judgment input text
   * @param obj #root.execution
   * @param id the name of the stage to find
   * @return the judgment input text
   */
  static String judgment(Object obj, String id) {
    if (obj instanceof Execution) {
      Execution execution = (Execution) obj;
      Stage stageWithJudgmentInput = execution.getStages()
        .stream()
        .filter(isManualStageWithManualInput(id))
        .findFirst()
        .orElseThrow(
          () -> new SpelHelperFunctionException(
            String.format("Unable to locate manual Judgment stage [%s] using #judgment(%s) in execution %s. " +
              "Stage doesn't exist or doesn't contain judgmentInput in its context ",
              id, id, execution.getId()
            )
          )
        );

      return (String) stageWithJudgmentInput.getContext().get("judgmentInput");
    }

    throw new SpelHelperFunctionException(
      String.format("Invalid first param to #judgment(%s). must be an execution", id)
    );
  }

  static List<Map<String, Object>> deployedServerGroups(Object obj, String...id) {
    if (obj instanceof Execution) {
      List<Map<String, Object>> deployedServerGroups = new ArrayList<>();
      ((Execution) obj).getStages()
        .stream()
        .filter(matchesDeployedStage(id))
        .forEach(stage -> {
          String region = (String) stage.getContext().get("region");
          if (region == null) {
            Map<String, Object> availabilityZones = (Map<String, Object>) stage.getContext().get("availabilityZones");
            if (availabilityZones != null) {
              region = availabilityZones.keySet().iterator().next();
            }
          }

          if (region != null) {
            Map<String, Object> deployDetails = new HashMap<>();
            deployDetails.put("account", stage.getContext().get("account"));
            deployDetails.put("capacity", stage.getContext().get("capacity"));
            deployDetails.put("parentStage", stage.getContext().get("parentStage"));
            deployDetails.put("region", region);
            List<Map> existingDetails = (List<Map>) stage.getContext().get("deploymentDetails");
            if (existingDetails != null) {
              existingDetails
                .stream()
                .filter(d -> deployDetails.get("region").equals(d.get("region")))
                .forEach(deployDetails::putAll);
            }

            List<Map> serverGroups = (List<Map>) ((Map) stage.getContext().get("deploy.server.groups")).get(region);
            if (serverGroups != null) {
              deployDetails.put("serverGroup", serverGroups.get(0));
            }

            deployedServerGroups.add(deployDetails);
          }
        });

      return deployedServerGroups;
    }

    throw new IllegalArgumentException("An execution is required for this function");
  }

  /**
   * Alias to judgment
   */
  static String judgement(Object obj, String id) {
    return judgment(obj, id);
  }

  private static Predicate<Stage> isManualStageWithManualInput(String id) {
    return i -> (id != null && id.equals(i.getName())) && (i.getContext() != null && i.getType().equals("manualJudgment") && i.getContext().get("judgmentInput") != null);
  }

  private static Predicate<Stage> matchesDeployedStage(String ...id) {
    List<String> idsOrNames = Arrays.asList(id);
    if (!idsOrNames.isEmpty()){
      return stage -> DEPLOY_STAGE_NAMES.contains(stage.getType()) &&
        stage.getContext().containsKey("deploy.server.groups") &&
        stage.getStatus() == ExecutionStatus.SUCCEEDED &&
        (idsOrNames.contains(stage.getName()) || idsOrNames.contains(stage.getId()));
    } else {
      return stage -> DEPLOY_STAGE_NAMES.contains(stage.getType()) &&
        stage.getContext().containsKey("deploy.server.groups") && stage.getStatus() == ExecutionStatus.SUCCEEDED;
    }
  }
}
