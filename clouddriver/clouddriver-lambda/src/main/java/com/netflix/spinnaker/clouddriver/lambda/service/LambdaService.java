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
import com.netflix.spinnaker.clouddriver.lambda.deploy.ops.LambdaClientProvider;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class LambdaService extends LambdaClientProvider {

  private final ObjectMapper mapper;

  public LambdaService(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      ObjectMapper mapper,
      LambdaServiceConfig lambdaServiceConfig) {
    super(region, account);
    super.operationsConfig = lambdaServiceConfig;
    super.amazonClientProvider = amazonClientProvider;
    this.mapper = mapper;
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
    AWSLambda lambda = getLambdaClient();
    String nextMarker = null;
    List<FunctionConfiguration> lstFunction = new ArrayList<>();
    do {
      ListFunctionsRequest listFunctionsRequest = new ListFunctionsRequest();
      if (nextMarker != null) {
        listFunctionsRequest.setMarker(nextMarker);
      }

      ListFunctionsResult listFunctionsResult = lambda.listFunctions(listFunctionsRequest);

      if (listFunctionsResult == null) {
        break;
      }

      lstFunction.addAll(listFunctionsResult.getFunctions());
      nextMarker = listFunctionsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return lstFunction;
  }

  private Void addBaseAttributes(Map<String, Object> functionAttributes, String functionName) {
    GetFunctionResult result =
        getLambdaClient().getFunction(new GetFunctionRequest().withFunctionName(functionName));
    if (result == null) {
      return null;
    }
    Map<String, Object> attr = mapper.convertValue(result.getConfiguration(), Map.class);
    attr.put("account", getCredentials().getName());
    attr.put("region", getRegion());
    attr.put("code", result.getCode());
    attr.put("tags", result.getTags());
    attr.put("concurrency", result.getConcurrency());
    attr.values().removeAll(Collections.singleton(null));
    functionAttributes.putAll(attr);
    return null;
  }

  private Void addRevisionsAttributes(Map<String, Object> functionAttributes, String functionName) {
    Map<String, String> revisions = listFunctionRevisions(functionName);
    functionAttributes.put("revisions", revisions);
    return null;
  }

  private Map<String, String> listFunctionRevisions(String functionName) {
    AWSLambda lambda = getLambdaClient();
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
          lambda.listVersionsByFunction(listVersionsByFunctionRequest);
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
    AWSLambda lambda = getLambdaClient();
    String nextMarker = null;
    List<AliasConfiguration> aliasConfigurations = new ArrayList<>();
    do {
      ListAliasesRequest listAliasesRequest = new ListAliasesRequest();
      listAliasesRequest.setFunctionName(functionName);
      if (nextMarker != null) {
        listAliasesRequest.setMarker(nextMarker);
      }

      ListAliasesResult listAliasesResult = lambda.listAliases(listAliasesRequest);
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
    AWSLambda lambda = getLambdaClient();
    String nextMarker = null;
    do {
      ListEventSourceMappingsRequest listEventSourceMappingsRequest =
          new ListEventSourceMappingsRequest();
      listEventSourceMappingsRequest.setFunctionName(functionName);

      if (nextMarker != null) {
        listEventSourceMappingsRequest.setMarker(nextMarker);
      }

      ListEventSourceMappingsResult listEventSourceMappingsResult =
          lambda.listEventSourceMappings(listEventSourceMappingsRequest);
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

  private static final Predicate<Statement> isLambdaInvokeAction =
      statement ->
          statement.getActions().stream()
              .anyMatch(action -> "lambda:InvokeFunction".equals(action.getActionName()));
  private static final Predicate<Statement> isElbPrincipal =
      statement ->
          statement.getPrincipals().stream()
              .anyMatch(
                  principal -> "elasticloadbalancing.amazonaws.com".equals(principal.getId()));

  private List<String> getTargetGroupNames(String functionName) {
    List<String> targetGroupNames = new ArrayList<>();
    Predicate<Statement> isAllowStatement =
        statement -> statement.getEffect().toString().equals(Statement.Effect.Allow.toString());

    try {
      AWSLambda lambda = getLambdaClient();
      GetPolicyResult result =
          lambda.getPolicy(new GetPolicyRequest().withFunctionName(functionName));
      Policy policy = Policy.fromJson(result.getPolicy());

      targetGroupNames =
          policy.getStatements().stream()
              .filter(isAllowStatement.and(isLambdaInvokeAction).and(isElbPrincipal))
              .flatMap(statement -> statement.getConditions().stream())
              .filter(
                  condition ->
                      "ArnLike".equals(condition.getType())
                          && "AWS:SourceArn".equals(condition.getConditionKey()))
              .flatMap(condition -> condition.getValues().stream())
              .flatMap(value -> ArnUtils.extractTargetGroupName(value).stream())
              .collect(Collectors.toList());

    } catch (NullPointerException | ResourceNotFoundException e) {
      // ignore the exception. Log it
      log.info("Unable to find target group names for {}", functionName);
    }

    return targetGroupNames;
  }
}
