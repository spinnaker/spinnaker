/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;

import com.amazonaws.auth.policy.*;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LambdaCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};

  private static final Set<AgentDataType> types =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(LAMBDA_FUNCTIONS.ns));
        }
      };

  private final ObjectMapper objectMapper;

  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final String region;
  private OnDemandMetricsSupport metricsSupport;
  private final Registry registry;

  LambdaCachingAgent(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region) {
    this.objectMapper = objectMapper;

    this.amazonClientProvider = amazonClientProvider;
    this.account = account;
    this.region = region;
    this.registry = new DefaultRegistry();
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            AmazonCloudProvider.ID + ":" + AmazonCloudProvider.ID + ":" + OnDemandType.Function);
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + LambdaCachingAgent.class.getSimpleName();
  }

  @Override
  public String getAccountName() {
    return account.getName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in {}", getAgentType());

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<FunctionConfiguration> lstFunction = new ArrayList<FunctionConfiguration>();

    do {
      ListFunctionsRequest listFunctionsRequest = new ListFunctionsRequest();
      if (nextMarker != null) {
        listFunctionsRequest.setMarker(nextMarker);
      }

      ListFunctionsResult listFunctionsResult = lambda.listFunctions(listFunctionsRequest);

      lstFunction.addAll(listFunctionsResult.getFunctions());
      nextMarker = listFunctionsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);

    Collection<CacheData> data = new LinkedList<>();
    Collection<CacheData> appData = new LinkedList<>();
    Map<String, Collection<String>> appRelationships = new HashMap<String, Collection<String>>();

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
    for (FunctionConfiguration x : lstFunction) {
      Map<String, Object> attributes = objectMapper.convertValue(x, ATTRIBUTES);
      attributes.put("account", account.getName());
      attributes.put("region", region);
      attributes.put("revisions", listFunctionRevisions(x.getFunctionArn()));
      attributes.put("aliasConfiguration", listAliasConfiguration(x.getFunctionArn()));
      attributes.put(
          "eventSourceMappings", listEventSourceMappingConfiguration(x.getFunctionArn()));

      attributes = addConfigAttributes(attributes, x, lambda);
      String functionName = x.getFunctionName();
      attributes.put("targetGroups", getTargetGroupNames(lambda, functionName));
      Names names = Names.parseName(functionName);
      if (null != names.getApp()) {
        String appKey =
            com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(names.getApp());
        Collection<String> functionKeys = appRelationships.get(appKey);
        String functionKey = Keys.getLambdaFunctionKey(account.getName(), region, functionName);

        if (null == functionKeys) {
          functionKeys = new ArrayList<>();
          appRelationships.put(appKey, functionKeys);
        }
        functionKeys.add(functionKey);
      }
      data.add(
          new DefaultCacheData(
              Keys.getLambdaFunctionKey(account.getName(), region, x.getFunctionName()),
              attributes,
              Collections.emptyMap()));
    }
    for (String appKey : appRelationships.keySet()) {
      appData.add(
          new DefaultCacheData(
              appKey,
              Collections.emptyMap(),
              Collections.singletonMap(LAMBDA_FUNCTIONS.ns, appRelationships.get(appKey))));
    }
    cacheResults.put(LAMBDA_FUNCTIONS.ns, data);
    cacheResults.put(
        com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS.ns, appData);
    log.info("Caching {} items in {}", String.valueOf(data.size()), getAgentType());
    return new DefaultCacheResult(cacheResults);
  }

  private Map<String, String> listFunctionRevisions(String functionArn) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    Map<String, String> listRevionIds = new HashMap<String, String>();
    do {
      ListVersionsByFunctionRequest listVersionsByFunctionRequest =
          new ListVersionsByFunctionRequest();
      listVersionsByFunctionRequest.setFunctionName(functionArn);
      if (nextMarker != null) {
        listVersionsByFunctionRequest.setMarker(nextMarker);
      }

      ListVersionsByFunctionResult listVersionsByFunctionResult =
          lambda.listVersionsByFunction(listVersionsByFunctionRequest);
      for (FunctionConfiguration x : listVersionsByFunctionResult.getVersions()) {
        listRevionIds.put(x.getRevisionId(), x.getVersion());
      }
      nextMarker = listVersionsByFunctionResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return listRevionIds;
  }

  private List<AliasConfiguration> listAliasConfiguration(String functionArn) {
    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    List<AliasConfiguration> aliasConfigurations = new ArrayList<>();
    do {
      ListAliasesRequest listAliasesRequest = new ListAliasesRequest();
      listAliasesRequest.setFunctionName(functionArn);
      if (nextMarker != null) {
        listAliasesRequest.setMarker(nextMarker);
      }

      ListAliasesResult listAliasesResult = lambda.listAliases(listAliasesRequest);
      for (AliasConfiguration x : listAliasesResult.getAliases()) {
        aliasConfigurations.add(x);
      }
      nextMarker = listAliasesResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return aliasConfigurations;
  }

  private final List<EventSourceMappingConfiguration> listEventSourceMappingConfiguration(
      String functionArn) {
    List<EventSourceMappingConfiguration> eventSourceMappingConfigurations = new ArrayList<>();

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);
    String nextMarker = null;
    do {
      ListEventSourceMappingsRequest listEventSourceMappingsRequest =
          new ListEventSourceMappingsRequest();
      listEventSourceMappingsRequest.setFunctionName(functionArn);

      if (nextMarker != null) {
        listEventSourceMappingsRequest.setMarker(nextMarker);
      }

      ListEventSourceMappingsResult listEventSourceMappingsResult =
          lambda.listEventSourceMappings(listEventSourceMappingsRequest);

      for (EventSourceMappingConfiguration x :
          listEventSourceMappingsResult.getEventSourceMappings()) {
        eventSourceMappingConfigurations.add(x);
      }
      nextMarker = listEventSourceMappingsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);

    return eventSourceMappingConfigurations;
  }

  private final Map<String, Object> addConfigAttributes(
      Map<String, Object> attributes, FunctionConfiguration x, AWSLambda lambda) {
    GetFunctionRequest getFunctionRequest = new GetFunctionRequest();
    getFunctionRequest.setFunctionName(x.getFunctionArn());
    GetFunctionResult getFunctionResult = lambda.getFunction(getFunctionRequest);
    attributes.put("vpcConfig", getFunctionResult.getConfiguration().getVpcConfig());
    attributes.put("code", getFunctionResult.getCode());
    attributes.put("tags", getFunctionResult.getTags());
    attributes.put("concurrency", getFunctionResult.getConcurrency());
    return attributes;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.Function) && cloudProvider.equals(AmazonCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!validKeys(data)
        || !data.get("account").equals(getAccountName())
        || !data.get("region").equals(region)) {
      return null;
    }

    String appName = (String) data.get("appName");
    String functionName = combineAppDetail(appName, (String) data.get("functionName"));

    String functionKey =
        Keys.getLambdaFunctionKey(
            (String) data.get("credentials"), (String) data.get("region"), functionName);

    String appKey = com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(appName);

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);

    GetFunctionResult functionResult = null;
    try {
      functionResult = lambda.getFunction(new GetFunctionRequest().withFunctionName(functionName));
    } catch (ResourceNotFoundException ex) {
      log.info("Function {} Not exist", functionName);
    }

    Map<String, Object> attributes = new HashMap<String, Object>();
    attributes.put("name", appName);
    Map<String, Collection<String>> evictions = Collections.emptyMap();

    Collection<String> existingFunctionRel = null;

    CacheData application = providerCache.get(APPLICATIONS.ns, appKey);

    if (null != application && null != application.getRelationships()) {
      existingFunctionRel = application.getRelationships().get(LAMBDA_FUNCTIONS.ns);
    }

    Map<String, Collection<String>> relationships = new HashMap<String, Collection<String>>();

    if (null != existingFunctionRel && !existingFunctionRel.isEmpty()) {
      if (null == functionResult && existingFunctionRel.contains(functionKey)) {
        existingFunctionRel.remove(functionKey);
        evictions.put(LAMBDA_FUNCTIONS.ns, Collections.singletonList(functionKey));
      } else {
        existingFunctionRel.add(functionKey);
      }

    } else {
      existingFunctionRel = Collections.singletonList(functionKey);
    }

    relationships.put(LAMBDA_FUNCTIONS.ns, existingFunctionRel);
    DefaultCacheData cacheData = new DefaultCacheData(appKey, attributes, relationships);
    DefaultCacheResult defaultCacheresults =
        new DefaultCacheResult(
            Collections.singletonMap(
                com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS.ns,
                Collections.singletonList(cacheData)));

    return new OnDemandAgent.OnDemandResult(getAgentType(), defaultCacheresults, evictions);
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    return null;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  private Boolean validKeys(Map<String, ? extends Object> data) {
    return (data.containsKey("functionName")
        && data.containsKey("credentials")
        && data.containsKey("region"));
  }

  protected String combineAppDetail(String appName, String functionName) {
    Names functionAppName = Names.parseName(functionName);
    if (null != functionAppName) {
      return functionAppName.getApp().equals(appName)
          ? functionName
          : (appName + "-" + functionName);
    } else {
      throw new IllegalArgumentException(
          String.format("Function name {%s} contains invlaid charachetrs ", functionName));
    }
  }

  private List<String> getTargetGroupNames(AWSLambda lambda, String functionName) {
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
          lambda.getPolicy(new GetPolicyRequest().withFunctionName(functionName));
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
              .filter(value -> ArnUtils.extractTargetGroupName(value).isPresent())
              .map(name -> ArnUtils.extractTargetGroupName(name).get())
              .collect(Collectors.toList());

    } catch (ResourceNotFoundException ex) {
      // ignore the exception.
      log.info("No policies exist for {}", functionName);
    }

    return targetGroupNames;
  }
}
