/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.lambda.service;

import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.service.config.LambdaServiceConfig;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import groovy.util.logging.Slf4j;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Slf4j
public class LambdaService {

  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final String region;
  private final int TIMEOUT_MINUTES;
  private final int RETRIES;
  private final Clock clock = Clock.systemDefaultZone();
  private final ObjectMapper mapper;

  public LambdaService(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      ObjectMapper mapper,
      LambdaServiceConfig lambdaServiceConfig) {

    this.amazonClientProvider = amazonClientProvider;
    this.account = account;
    this.region = region;
    this.mapper = mapper;
    this.TIMEOUT_MINUTES = lambdaServiceConfig.getRetry().getTimeout();
    this.RETRIES = lambdaServiceConfig.getRetry().getRetries();
  }

  public List<Map<String, Object>> getAllFunctions() {
    List<FunctionConfiguration> functions = listAllFunctionConfigurations();
    List<Map<String, Object>> hydratedFunctionList =
        Collections.synchronizedList(new ArrayList<>());
    functions.stream()
        .forEach(
            f -> {
              Map<String, Object> functionAttributes = new ConcurrentHashMap<>();
              addBaseAttributes(functionAttributes, f.getFunctionName());
              addRevisionsAttributes(functionAttributes, f.getFunctionName());
              addAliasAndEventSourceMappingConfigurationAttributes(
                  functionAttributes, f.getFunctionName());
              addTargetGroupAttributes(functionAttributes, f.getFunctionName());
              hydratedFunctionList.add(functionAttributes);
            });

    // if addBaseAttributes returned null, the name won't be included. There is a chance other
    // resources still have
    // associations to the deleted lambda
    return hydratedFunctionList.stream()
        .filter(lf -> lf.get("functionName") != null)
        .collect(Collectors.toList());
  }

  public Map<String, Object> getFunctionByName(String functionName) throws InterruptedException {
    List<Callable<Void>> functionTasks = Collections.synchronizedList(new ArrayList<>());
    Map<String, Object> functionAttributes = new ConcurrentHashMap<>();
    addBaseAttributes(functionAttributes, functionName);
    if (functionAttributes.isEmpty()) {
      // return quick so we don't make extra api calls for a delete lambda
      return null;
    }
    addRevisionsAttributes(functionAttributes, functionName);
    addAliasAndEventSourceMappingConfigurationAttributes(functionAttributes, functionName);
    addTargetGroupAttributes(functionAttributes, functionName);
    return functionAttributes;
  }

  public List<FunctionConfiguration> listAllFunctionConfigurations() {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<FunctionConfiguration> lstFunction = new ArrayList<>();
    do {
      ListFunctionsRequest listFunctionsRequest = new ListFunctionsRequest();
      if (nextMarker != null) {
        listFunctionsRequest.setMarker(nextMarker);
      }

      ListFunctionsResult listFunctionsResult =
          retry(
              "listFunctions",
              () -> lambda.listFunctions(listFunctionsRequest),
              RETRIES,
              TIMEOUT_MINUTES);

      if (listFunctionsResult == null) {
        break;
      }

      lstFunction.addAll(listFunctionsResult.getFunctions());
      nextMarker = listFunctionsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return lstFunction;
  }

  private Void addBaseAttributes(Map<String, Object> functionAttributes, String functionName) {
    GetFunctionResult result = getFunctionResult(functionName);
    if (result == null) {
      return null;
    }
    Map<String, Object> attr = mapper.convertValue(result.getConfiguration(), Map.class);
    attr.put("account", account.getName());
    attr.put("region", region);
    attr.put("code", result.getCode());
    attr.put("tags", result.getTags());
    attr.put("concurrency", result.getConcurrency());
    attr.values().removeAll(Collections.singleton(null));
    functionAttributes.putAll(attr);
    return null;
  }

  private GetFunctionResult getFunctionResult(String functionName) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    GetFunctionRequest request = new GetFunctionRequest().withFunctionName(functionName);
    return retry("getFunctionRequest", () -> lambda.getFunction(request), RETRIES, TIMEOUT_MINUTES);
  }

  private Void addRevisionsAttributes(Map<String, Object> functionAttributes, String functionName) {
    Map<String, String> revisions = listFunctionRevisions(functionName);
    functionAttributes.put("revisions", revisions);
    return null;
  }

  private Map<String, String> listFunctionRevisions(String functionName) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    Map<String, String> listRevionIds = new HashMap<>();
    do {
      ListVersionsByFunctionRequest listVersionsByFunctionRequest =
          new ListVersionsByFunctionRequest();
      listVersionsByFunctionRequest.setFunctionName(functionName);
      if (nextMarker != null) {
        listVersionsByFunctionRequest.setMarker(nextMarker);
      }

      ListVersionsByFunctionResult listVersionsByFunctionResult =
          retry(
              "listVersionsByFunction",
              () -> lambda.listVersionsByFunction(listVersionsByFunctionRequest),
              RETRIES,
              TIMEOUT_MINUTES);
      if (listVersionsByFunctionResult == null) {
        return listRevionIds;
      }
      for (FunctionConfiguration x : listVersionsByFunctionResult.getVersions()) {
        listRevionIds.put(x.getRevisionId(), x.getVersion());
      }
      nextMarker = listVersionsByFunctionResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return listRevionIds;
  }

  private Void addAliasAndEventSourceMappingConfigurationAttributes(
      Map<String, Object> functionAttributes, String functionName) {
    List<AliasConfiguration> aliasConfigurationList = listAliasConfiguration(functionName);
    functionAttributes.put("aliasConfigurations", aliasConfigurationList);

    // TODO: should we also process these concurrently?
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurationsList =
        listEventSourceMappingConfiguration(functionName);
    for (AliasConfiguration currAlias : aliasConfigurationList) {
      List<EventSourceMappingConfiguration> currAliasEvents =
          listEventSourceMappingConfiguration(currAlias.getAliasArn());
      eventSourceMappingConfigurationsList.addAll(currAliasEvents);
    }
    functionAttributes.put("eventSourceMappings", eventSourceMappingConfigurationsList);
    return null;
  }

  private List<AliasConfiguration> listAliasConfiguration(String functionName) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<AliasConfiguration> aliasConfigurations = new ArrayList<>();
    do {
      ListAliasesRequest listAliasesRequest = new ListAliasesRequest();
      listAliasesRequest.setFunctionName(functionName);
      if (nextMarker != null) {
        listAliasesRequest.setMarker(nextMarker);
      }

      ListAliasesResult listAliasesResult =
          retry(
              "listAliases",
              () -> lambda.listAliases(listAliasesRequest),
              RETRIES,
              TIMEOUT_MINUTES);
      if (listAliasesResult == null) {
        return aliasConfigurations;
      }
      for (AliasConfiguration x : listAliasesResult.getAliases()) {
        aliasConfigurations.add(x);
      }
      nextMarker = listAliasesResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return aliasConfigurations;
  }

  private List<EventSourceMappingConfiguration> listEventSourceMappingConfiguration(
      String functionName) {
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = new ArrayList<>();
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    do {
      ListEventSourceMappingsRequest listEventSourceMappingsRequest =
          new ListEventSourceMappingsRequest();
      listEventSourceMappingsRequest.setFunctionName(functionName);

      if (nextMarker != null) {
        listEventSourceMappingsRequest.setMarker(nextMarker);
      }

      ListEventSourceMappingsResult listEventSourceMappingsResult =
          retry(
              "listEventSourceMappings",
              () -> lambda.listEventSourceMappings(listEventSourceMappingsRequest),
              RETRIES,
              TIMEOUT_MINUTES);
      if (listEventSourceMappingsResult == null) {
        return eventSourceMappingConfigurations;
      }

      for (EventSourceMappingConfiguration x :
          listEventSourceMappingsResult.getEventSourceMappings()) {
        eventSourceMappingConfigurations.add(x);
      }
      nextMarker = listEventSourceMappingsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);

    return eventSourceMappingConfigurations;
  }

  private Void addTargetGroupAttributes(
      Map<String, Object> functionAttributes, String functionName) {
    List<String> targetGroups = getTargetGroupNames(functionName);
    functionAttributes.put("targetGroups", targetGroups);
    return null;
  }

  private List<String> getTargetGroupNames(String functionName) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    List<String> targetGroupNames = new ArrayList<>();
    Predicate<Statement> isAllowStatement =
        statement -> statement.getEffect().toString().equals(Statement.Effect.Allow.toString());
    Predicate<Statement> isLambdaInvokeAction =
        statement ->
            statement.getActions().stream()
                .anyMatch(action -> action.getActionName().equals("lambda:InvokeFunction"));
    Predicate<Statement> isElbPrincipal =
        statement ->
            statement.getPrincipals().stream()
                .anyMatch(
                    principal -> principal.getId().equals("elasticloadbalancing.amazonaws.com"));

    try {
      GetPolicyResult result =
          retry(
              "getPolicy",
              () -> lambda.getPolicy(new GetPolicyRequest().withFunctionName(functionName)),
              RETRIES,
              TIMEOUT_MINUTES);
      if (result == null) {
        return targetGroupNames;
      }
      String json = result.getPolicy();
      Policy policy = Policy.fromJson(json);

      targetGroupNames =
          policy.getStatements().stream()
              .filter(isAllowStatement.and(isLambdaInvokeAction).and(isElbPrincipal))
              .flatMap(statement -> statement.getConditions().stream())
              .filter(
                  condition ->
                      condition.getType().equals("ArnLike")
                          && condition.getConditionKey().equals("AWS:SourceArn"))
              .flatMap(condition -> condition.getValues().stream())
              .flatMap(value -> ArnUtils.extractTargetGroupName(value).stream())
              .collect(Collectors.toList());

    } catch (ResourceNotFoundException e) {
      // ignore the exception.
    }

    return targetGroupNames;
  }

  @Nullable
  private <T> T retry(String requestName, Supplier<T> fn, int maxRetries, int timeoutMinutes) {
    int retries = 0;
    long startTime = clock.instant().toEpochMilli();
    while (true) {
      long currentTime = clock.instant().toEpochMilli();
      if (currentTime > (startTime + TimeUnit.MINUTES.toMillis(timeoutMinutes))) {
        throw new SpinnakerException(
            "Failed to complete sdk method 'lambda:" + requestName + "' before the timeout.");
      }
      try {
        return fn.get();
      } catch (ResourceNotFoundException notFoundException) {
        return null;
      } catch (TooManyRequestsException | ServiceException e) {
        if (retries >= (maxRetries - 1)) {
          throw e;
        }
        if (e instanceof ServiceException) {
          retries++;
        }
      } catch (Exception e) {
        throw e;
      }
    }
  }
}
