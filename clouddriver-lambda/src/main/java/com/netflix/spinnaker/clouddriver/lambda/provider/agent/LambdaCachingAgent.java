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
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;
import static java.util.stream.Collectors.toSet;

import com.amazonaws.auth.policy.*;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
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
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LambdaCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {
  private static final TypeReference<Map<String, Object>> ATTRIBUTES =
      new TypeReference<Map<String, Object>>() {};

  private static final Set<AgentDataType> types =
      new HashSet<AgentDataType>() {
        {
          add(AUTHORITATIVE.forType(LAMBDA_FUNCTIONS.ns));
          add(INFORMATIVE.forType(APPLICATIONS.ns));
        }
      };

  private final ObjectMapper objectMapper;

  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final String region;
  private OnDemandMetricsSupport metricsSupport;
  private final Registry registry;
  private final Clock clock = Clock.systemDefaultZone();

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

  public String getRegion() {
    return region;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    long loadDataStart = clock.instant().toEpochMilli();
    log.info("Describing items in {}", getAgentType());

    AWSLambda lambda = amazonClientProvider.getAmazonLambda(account, region);

    // Get All Lambda's
    List<FunctionConfiguration> lstFunction = getFreshLambdas(lambda);

    Map<String, CacheData> lambdaCacheData = new HashMap<>();
    Map<String, Collection<String>> appLambdaRelationships = new HashMap<>();
    for (FunctionConfiguration x : lstFunction) {
      String functionKey =
          Keys.getLambdaFunctionKey(getAccountName(), getRegion(), x.getFunctionName());

      // Call AWS with individual lambda and build attributes to obtain LambdaConfiguration.State
      // See notes for ->
      // https://docs.aws.amazon.com/cli/latest/reference/lambda/list-functions.html
      Map<String, Object> lambdaAttributes = buildLambdaAttributes(x.getFunctionName(), lambda);
      if (lambdaAttributes == null) {
        // The lambda was deleted between the list call and now
        continue;
      }

      /* TODO: If the functionName follows frigga by chance (i.e. somename-someothername), it will try to store the
         lambda as a relationship with the app name (somename), even if it wasn't deployed by spinnaker!
      */
      // Add the spinnaker application relationship and store it
      Names names = Names.parseName(x.getFunctionName());
      if (names.getApp() != null) {
        String appKey =
            com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(names.getApp());
        Collection<String> functionKeys =
            appLambdaRelationships.getOrDefault(appKey, new ArrayList<>());
        functionKeys.add(functionKey);
        appLambdaRelationships.put(appKey, functionKeys);
        lambdaCacheData.put(
            functionKey,
            new DefaultCacheData(
                functionKey,
                lambdaAttributes,
                Collections.singletonMap(APPLICATIONS.ns, Collections.singletonList(appKey))));
      } else {
        // TODO: Do we care about non spinnaker deployed lambdas?
        lambdaCacheData.put(
            functionKey,
            new DefaultCacheData(functionKey, lambdaAttributes, Collections.emptyMap()));
      }
    }

    Collection<CacheData> processedOnDemandCache = new ArrayList<>();

    // Process on demand cache
    Collection<CacheData> onDemandCacheData =
        providerCache
            .getAll(
                ON_DEMAND.getNs(),
                providerCache.filterIdentifiers(
                    ON_DEMAND.getNs(),
                    Keys.getLambdaFunctionKey(getAccountName(), getRegion(), "*")))
            .stream()
            .filter(d -> (int) d.getAttributes().get("processedCount") == 0)
            .collect(Collectors.toList());

    for (CacheData onDemandItem : onDemandCacheData) {
      try {
        long cachedAt = (long) onDemandItem.getAttributes().get("cacheTime");
        if (cachedAt > loadDataStart) {
          CacheData currentLambda = lambdaCacheData.get(onDemandItem.getId());
          if (currentLambda != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            LocalDateTime onDemandLastModified =
                LocalDateTime.parse(
                    (String) onDemandItem.getAttributes().get("lastModified"), formatter);
            LocalDateTime currentLambdaLastModified =
                LocalDateTime.parse(
                    (String) currentLambda.getAttributes().get("lastModified"), formatter);
            if (onDemandLastModified.isAfter(currentLambdaLastModified)) {
              lambdaCacheData.put(onDemandItem.getId(), onDemandItem);
              String appKey =
                  onDemandItem.getRelationships().get(APPLICATIONS.ns).stream().findFirst().get();
              Collection<String> functionkeys =
                  appLambdaRelationships.getOrDefault(appKey, new ArrayList<>());
              functionkeys.add(onDemandItem.getId());
              appLambdaRelationships.put(appKey, functionkeys);
            }
          } else {
            lambdaCacheData.put(onDemandItem.getId(), onDemandItem);
            String appKey =
                onDemandItem.getRelationships().get(APPLICATIONS.ns).stream().findFirst().get();
            Collection<String> functionkeys =
                appLambdaRelationships.getOrDefault(appKey, new ArrayList<>());
            functionkeys.add(onDemandItem.getId());
            appLambdaRelationships.put(appKey, functionkeys);
          }
        }
        Map<String, Object> attr = onDemandItem.getAttributes();
        attr.put("processedCount", 1);
        processedOnDemandCache.add(
            new DefaultCacheData(onDemandItem.getId(), attr, Collections.emptyMap()));
      } catch (Exception e) {
        log.warn("Failed to process onDemandCache for Lambda's: " + e.getMessage());
      }
    }

    // Create the INFORMATIVE spinnaker application cache with lambda relationships
    Collection<CacheData> appCacheData = new LinkedList<>();
    for (String appKey : appLambdaRelationships.keySet()) {
      appCacheData.add(
          new DefaultCacheData(
              appKey,
              Collections.emptyMap(),
              Collections.singletonMap(LAMBDA_FUNCTIONS.ns, appLambdaRelationships.get(appKey))));
    }

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();

    cacheResults.put(LAMBDA_FUNCTIONS.ns, lambdaCacheData.values());
    cacheResults.put(APPLICATIONS.ns, appCacheData);
    cacheResults.put(ON_DEMAND.ns, processedOnDemandCache);

    Map<String, Collection<String>> evictions =
        computeEvictableData(lambdaCacheData.values(), providerCache);

    log.info("Caching {} items in {}", String.valueOf(lambdaCacheData.size()), getAgentType());
    return new DefaultCacheResult(cacheResults, evictions);
  }

  List<FunctionConfiguration> getFreshLambdas(AWSLambda lambda) {
    String nextMarker = null;
    List<FunctionConfiguration> lstFunction = new ArrayList<>();
    do {
      ListFunctionsRequest listFunctionsRequest = new ListFunctionsRequest();
      if (nextMarker != null) {
        listFunctionsRequest.setMarker(nextMarker);
      }

      ListFunctionsResult listFunctionsResult = lambda.listFunctions(listFunctionsRequest);

      lstFunction.addAll(listFunctionsResult.getFunctions());
      nextMarker = listFunctionsResult.getNextMarker();

    } while (nextMarker != null && nextMarker.length() != 0);
    return lstFunction;
  }

  @Nullable
  Map<String, Object> buildLambdaAttributes(String functionName, AWSLambda lambda) {
    GetFunctionRequest getFunctionRequest = new GetFunctionRequest().withFunctionName(functionName);
    GetFunctionResult getFunctionResult;
    try {
      getFunctionResult = lambda.getFunction(getFunctionRequest);
    } catch (ResourceNotFoundException ex) {
      log.info("Function {} Not exist", functionName);
      return null;
    }

    Map<String, Object> attr =
        objectMapper.convertValue(getFunctionResult.getConfiguration(), ATTRIBUTES);
    attr.put("account", account.getName());
    attr.put("region", region);
    attr.put("vpcConfig", getFunctionResult.getConfiguration().getVpcConfig());
    attr.put("code", getFunctionResult.getCode());
    attr.put("tags", getFunctionResult.getTags());
    attr.put("concurrency", getFunctionResult.getConcurrency());
    attr.put("state", getFunctionResult.getConfiguration().getState());
    attr.put("stateReason", getFunctionResult.getConfiguration().getStateReason());
    attr.put("stateReasonCode", getFunctionResult.getConfiguration().getStateReasonCode());
    attr.put(
        "revisions", listFunctionRevisions(getFunctionResult.getConfiguration().getFunctionArn()));
    List<AliasConfiguration> allAliases =
        listAliasConfiguration(getFunctionResult.getConfiguration().getFunctionArn());
    attr.put("aliasConfigurations", allAliases);
    List<EventSourceMappingConfiguration> eventSourceMappings =
        listEventSourceMappingConfiguration(getFunctionResult.getConfiguration().getFunctionArn());
    List<EventSourceMappingConfiguration> aliasEvents = new ArrayList<>();
    for (AliasConfiguration currAlias : allAliases) {
      List<EventSourceMappingConfiguration> currAliasEvents =
          listEventSourceMappingConfiguration(currAlias.getAliasArn());
      aliasEvents.addAll(currAliasEvents);
    }
    eventSourceMappings.addAll(aliasEvents);
    attr.put("eventSourceMappings", eventSourceMappings);
    attr.put("targetGroups", getTargetGroupNames(lambda, functionName));
    return attr;
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

    Map<String, Object> lambdaAttributes = buildLambdaAttributes(functionName, lambda);

    DefaultCacheResult defaultCacheResult;
    Map<String, Collection<String>> evictions;

    if (lambdaAttributes != null) {
      lambdaAttributes.put("cacheTime", clock.instant().toEpochMilli());
      lambdaAttributes.put("processedCount", 0);
      DefaultCacheData lambdaCacheData =
          new DefaultCacheData(
              functionKey,
              lambdaAttributes,
              Collections.singletonMap(APPLICATIONS.ns, Collections.singletonList(appKey)));

      defaultCacheResult =
          new DefaultCacheResult(
              Collections.singletonMap(ON_DEMAND.ns, Collections.singletonList(lambdaCacheData)));

      evictions = Collections.emptyMap();
    } else {
      defaultCacheResult =
          new DefaultCacheResult(
              Collections.singletonMap(LAMBDA_FUNCTIONS.ns, Collections.emptyList()));

      evictions =
          Collections.singletonMap(
              LAMBDA_FUNCTIONS.ns,
              providerCache.filterIdentifiers(LAMBDA_FUNCTIONS.ns, functionKey));
    }

    return new OnDemandAgent.OnDemandResult(getAgentType(), defaultCacheResult, evictions);
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> keys =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.getLambdaFunctionKey(account.getName(), getRegion(), "*"));
    return providerCache.getAll(ON_DEMAND.getNs(), keys, RelationshipCacheFilter.none()).stream()
        .map(
            it -> {
              String lambdaId = it.getId();
              Map<String, String> details = Keys.parse(lambdaId);
              Map<String, Object> attributes = it.getAttributes();
              Map<String, Object> resp = new HashMap<>();
              resp.put("id", lambdaId);
              resp.put("details", details);
              resp.put("attributes", it.getAttributes());
              resp.put("cacheTime", attributes.get("cacheTime"));
              resp.put("processedCount", attributes.get("processedCount"));
              resp.put("processedTime", attributes.getOrDefault("processedTime", null));
              return resp;
            })
        .collect(toSet());
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

  /**
   * Provides the key namespace that the caching agent is authoritative of. Currently only supports
   * the caching agent being authoritative over one key namespace. Taken from
   * AbstractEcsCachingAgent
   *
   * @return Key namespace.
   */
  String getAuthoritativeKeyName() {
    Collection<AgentDataType> authoritativeNamespaces =
        getProvidedDataTypes().stream()
            .filter(agentDataType -> agentDataType.getAuthority().equals(AUTHORITATIVE))
            .collect(Collectors.toSet());

    if (authoritativeNamespaces.size() != 1) {
      throw new RuntimeException(
          "LambdaCachingAgent supports only one authoritative key namespace. "
              + authoritativeNamespaces.size()
              + " authoritative key namespace were given.");
    }

    return authoritativeNamespaces.iterator().next().getTypeName();
  }

  Map<String, Collection<String>> computeEvictableData(
      Collection<CacheData> newData, ProviderCache providerCache) {

    // Get all old keys from the cache for the region and account
    String authoritativeKeyName = getAuthoritativeKeyName();
    Set<String> oldKeys =
        providerCache.getIdentifiers(authoritativeKeyName).stream()
            .filter(
                key -> {
                  Map<String, String> keyParts = Keys.parse(key);
                  return keyParts.get("account").equalsIgnoreCase(account.getName())
                      && keyParts.get("region").equalsIgnoreCase(region);
                })
            .collect(Collectors.toSet());

    // New data can only come from the current account and region, no need to filter.
    Set<String> newKeys = newData.stream().map(CacheData::getId).collect(Collectors.toSet());

    Set<String> evictedKeys =
        oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(getAuthoritativeKeyName(), evictedKeys);
    String prettyKeyName =
        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, getAuthoritativeKeyName());

    log.info(
        "Evicting "
            + evictedKeys.size()
            + " "
            + prettyKeyName
            + (evictedKeys.size() > 1 ? "s" : "")
            + " in "
            + getAgentType());

    return evictionsByKey;
  }
}
