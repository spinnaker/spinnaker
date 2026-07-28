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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.deploy.ops.LambdaClientProvider;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.lambda.paginators.ListFunctionsIterable;

@Log4j2
public class LambdaService extends LambdaClientProvider {

  private final ObjectMapper mapper;
  private final LambdaServiceConfig lambdaServiceConfig;

  public LambdaService(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      ObjectMapper mapper,
      LambdaServiceConfig lambdaServiceConfig) {
    super(region, account);
    super.amazonClientProvider = amazonClientProvider;
    super.operationsConfig = lambdaServiceConfig;
    this.mapper = mapper;
    this.lambdaServiceConfig = lambdaServiceConfig;
  }

  public List<Map<String, Object>> getAllFunctions() {
    List<String> functionNames = listAllFunctionNames();
    List<Map<String, Object>> hydratedFunctionList = new ArrayList<>();
    functionNames.forEach(
        functionName -> {
          Map<String, Object> functionAttributes = getFunctionByName(functionName);
          if (functionAttributes != null && functionAttributes.get("functionName") != null) {
            hydratedFunctionList.add(functionAttributes);
          }
        });
    return hydratedFunctionList;
  }

  public Map<String, Object> getFunctionByName(String functionName) {
    Map<String, Object> functionAttributes = new ConcurrentHashMap<>();
    addBaseAttributes(functionAttributes, functionName);
    if (functionAttributes.isEmpty()) {
      // return quick so we don't make extra api calls for a deleted lambda
      return null;
    }
    addRevisionsAttributes(functionAttributes, functionName);
    addAliasAndEventSourceMappingConfigurationAttributes(functionAttributes, functionName);
    addTargetGroupAttributes(functionAttributes, functionName);
    return functionAttributes;
  }

  private List<String> listAllFunctionNames() {
    LambdaClient lambda = getLambdaClient();
    List<String> functionNames = new ArrayList<>();
    ListFunctionsIterable paginator = lambda.listFunctionsPaginator();
    if (paginator != null) {
      paginator.forEach(
          response ->
              response.functions().forEach(function -> functionNames.add(function.functionName())));
    }
    return functionNames;
  }

  private Void addBaseAttributes(Map<String, Object> functionAttributes, String functionName) {
    GetFunctionResponse result =
        getLambdaClient()
            .getFunction(GetFunctionRequest.builder().functionName(functionName).build());
    if (result == null) {
      return null;
    }
    Map<String, Object> attr = mapper.convertValue(result.configuration(), Map.class);
    attr.put("account", getCredentials().getName());
    attr.put("region", getRegion());
    attr.put("code", result.code());
    attr.put("tags", result.tags());
    attr.put("concurrency", result.concurrency());
    attr.values().removeAll(Collections.singleton(null));
    functionAttributes.putAll(attr);
    return null;
  }

  private void addRevisionsAttributes(Map<String, Object> functionAttributes, String functionName) {
    Map<String, String> revisions = new HashMap<>();
    getLambdaClient()
        .listVersionsByFunctionPaginator(
            ListVersionsByFunctionRequest.builder().functionName(functionName).build())
        .forEach(
            response ->
                response
                    .versions()
                    .forEach(version -> revisions.put(version.revisionId(), version.version())));
    functionAttributes.put("revisions", revisions);
  }

  private Void addAliasAndEventSourceMappingConfigurationAttributes(
      Map<String, Object> functionAttributes, String functionName) {
    List<AliasConfiguration> aliasConfigurationList = listAliasConfiguration(functionName);
    functionAttributes.put(
        "aliasConfigurations", mapper.convertValue(aliasConfigurationList, List.class));

    // TODO: should we also process these concurrently?
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurationsList =
        listEventSourceMappingConfiguration(functionName);
    for (AliasConfiguration currAlias : aliasConfigurationList) {
      List<EventSourceMappingConfiguration> currAliasEvents =
          listEventSourceMappingConfiguration(currAlias.aliasArn());
      eventSourceMappingConfigurationsList.addAll(currAliasEvents);
    }
    functionAttributes.put(
        "eventSourceMappings",
        mapper.convertValue(eventSourceMappingConfigurationsList, List.class));
    return null;
  }

  private List<AliasConfiguration> listAliasConfiguration(String functionName) {
    LambdaClient lambda = getLambdaClient();
    List<AliasConfiguration> aliasConfigurations = new ArrayList<>();
    lambda
        .listAliasesPaginator(ListAliasesRequest.builder().functionName(functionName).build())
        .forEach(response -> aliasConfigurations.addAll(response.aliases()));
    return aliasConfigurations;
  }

  private List<EventSourceMappingConfiguration> listEventSourceMappingConfiguration(
      String functionName) {
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = new ArrayList<>();
    LambdaClient lambda = getLambdaClient();
    lambda
        .listEventSourceMappingsPaginator(
            ListEventSourceMappingsRequest.builder().functionName(functionName).build())
        .forEach(
            response -> eventSourceMappingConfigurations.addAll(response.eventSourceMappings()));

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
      LambdaClient lambda = getLambdaClient();
      GetPolicyResponse result =
          lambda.getPolicy(GetPolicyRequest.builder().functionName(functionName).build());
      Policy policy = Policy.fromJson(result.policy());

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
