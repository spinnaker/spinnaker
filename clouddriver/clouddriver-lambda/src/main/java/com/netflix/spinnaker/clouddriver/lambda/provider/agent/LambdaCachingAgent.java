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
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;
import static java.util.stream.Collectors.toSet;

import com.amazonaws.services.lambda.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import com.netflix.spinnaker.clouddriver.core.limits.ServiceLimitConfiguration;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import com.netflix.spinnaker.clouddriver.lambda.names.LambdaResourceFunction;
import com.netflix.spinnaker.clouddriver.lambda.service.LambdaService;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LambdaCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {
  private static final Set<AgentDataType> types =
      Set.of(
          AUTHORITATIVE.forType(LAMBDA_FUNCTIONS.ns),
          AUTHORITATIVE.forType(LAMBDA_APPLICATIONS.ns));

  private final NetflixAmazonCredentials account;
  private final String region;
  private OnDemandMetricsSupport metricsSupport;
  private final Registry registry;
  private final Clock clock = Clock.systemDefaultZone();
  @Setter @VisibleForTesting private LambdaService lambdaService;
  private final Namer<LambdaResourceFunction> naming;

  LambdaCachingAgent(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      LambdaServiceConfig lambdaServiceConfig,
      ServiceLimitConfiguration serviceLimitConfiguration) {
    this(
        objectMapper,
        amazonClientProvider,
        account,
        region,
        lambdaServiceConfig,
        serviceLimitConfiguration,
        NamerRegistry.lookup()
            .withProvider(AmazonCloudProvider.ID)
            .withAccount(account.getName())
            .withResource(LambdaResourceFunction.class));
  }

  @VisibleForTesting
  LambdaCachingAgent(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      LambdaServiceConfig lambdaServiceConfig,
      ServiceLimitConfiguration serviceLimitConfiguration,
      Namer<LambdaResourceFunction> naming) {
    this.account = account;
    this.region = region;
    this.registry = new DefaultRegistry();
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            AmazonCloudProvider.ID + ":" + AmazonCloudProvider.ID + ":" + OnDemandType.Function);
    this.lambdaService =
        new LambdaService(amazonClientProvider, account, region, objectMapper, lambdaServiceConfig);
    this.naming = naming;
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
    // This is a map to allow easy lookup by function id for onDemand handling.  The values list is
    // the only thing
    // that's actually passed upwards.
    Map<String, CacheData> lambdaCacheData = new ConcurrentHashMap<>();
    Map<String, Collection<String>> appLambdaRelationships = new ConcurrentHashMap<>();

    // Get All Lambda's
    List<Map<String, Object>> allLambdas;
    try {
      allLambdas = lambdaService.getAllFunctions();
    } catch (Exception e) {
      throw new SpinnakerException(
          "Failed to populate the lambda cache for account '"
              + account.getName()
              + "' and region '"
              + region
              + "' because: "
              + e.getMessage());
    }

    buildCacheData(lambdaCacheData, appLambdaRelationships, allLambdas);

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
            .toList();

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
                  onDemandItem.getRelationships().get(LAMBDA_APPLICATIONS.ns).stream()
                      .findFirst()
                      .get();
              Collection<String> functionkeys =
                  appLambdaRelationships.getOrDefault(appKey, new ArrayList<>());
              functionkeys.add(onDemandItem.getId());
              appLambdaRelationships.put(appKey, functionkeys);
            }
          } else {
            lambdaCacheData.put(onDemandItem.getId(), onDemandItem);
            String appKey =
                onDemandItem.getRelationships().get(LAMBDA_APPLICATIONS.ns).stream()
                    .findFirst()
                    .get();
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

    // Create the AUTHORITATIVE lambda application cache with lambda function relationships
    Collection<CacheData> appCacheData = new LinkedList<>();
    for (String appKey : appLambdaRelationships.keySet()) {
      appCacheData.add(
          new DefaultCacheData(
              appKey,
              Map.of(
                  "application", Keys.parse(appKey).get("application"),
                  "account", account.getName(),
                  "region", region),
              Collections.singletonMap(LAMBDA_FUNCTIONS.ns, appLambdaRelationships.get(appKey))));
    }

    Map<String, Collection<CacheData>> cacheResults = new HashMap<>();

    cacheResults.put(LAMBDA_FUNCTIONS.ns, lambdaCacheData.values());
    cacheResults.put(LAMBDA_APPLICATIONS.ns, appCacheData);
    cacheResults.put(ON_DEMAND.ns, processedOnDemandCache);

    Map<String, Collection<String>> evictions =
        computeEvictableData(lambdaCacheData.values(), providerCache);

    log.info("Caching {} items in {}", String.valueOf(lambdaCacheData.size()), getAgentType());
    return new DefaultCacheResult(cacheResults, evictions);
  }

  void buildCacheData(
      Map<String, CacheData> lambdaCacheData,
      Map<String, Collection<String>> appLambdaRelationships,
      List<Map<String, Object>> allLambdas) {
    allLambdas.forEach(
        lf -> {
          String functionName = (String) lf.get("functionName");
          String functionKey =
              Keys.getLambdaFunctionKey(getAccountName(), getRegion(), functionName);
          // Use the naming strategy to derive the moniker from the function name and tags
          Moniker moniker = naming.deriveMoniker(new LambdaResourceFunction(lf));
          // Technically this should NEVER be null, as functionName is implicit & required in the
          // API call.
          // App is derived then from the text before the dash... aka... it's ALWAYS set.
          if (moniker.getApp() != null) {
            String appKey = Keys.getApplicationKey(moniker.getApp());
            appLambdaRelationships.compute(
                appKey,
                (k, v) -> {
                  Collection<String> fKeys = v;
                  if (fKeys == null) fKeys = new ArrayList<>();
                  fKeys.add(functionKey);
                  return fKeys;
                });
            lambdaCacheData.put(
                functionKey,
                new DefaultCacheData(
                    functionKey,
                    lf,
                    Collections.singletonMap(
                        LAMBDA_FUNCTIONS.ns, Collections.singletonList(functionKey))));
          } else {
            // TODO: Do we care about non spinnaker deployed lambdas?
            lambdaCacheData.put(
                functionKey, new DefaultCacheData(functionKey, lf, Collections.emptyMap()));
          }
        });
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

    String appKey = Keys.getApplicationKey(appName);

    Map<String, Object> lambdaAttributes = null;
    try {
      lambdaAttributes = lambdaService.getFunctionByName(functionName);
    } catch (Exception e) {
      if (e instanceof ResourceNotFoundException) {
        // do nothing, the lambda was deleted
      } else {
        throw new SpinnakerException(
            "Failed to populate the onDemandCache for lambda '" + functionName + "'");
      }
    }

    DefaultCacheResult defaultCacheResult;
    Map<String, Collection<String>> evictions;

    if (lambdaAttributes != null && !lambdaAttributes.isEmpty()) {
      lambdaAttributes.put("cacheTime", clock.instant().toEpochMilli());
      lambdaAttributes.put("processedCount", 0);
      DefaultCacheData lambdaCacheData =
          new DefaultCacheData(
              functionKey,
              lambdaAttributes,
              Collections.singletonMap(LAMBDA_APPLICATIONS.ns, Collections.singletonList(appKey)));

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

  /**
   * Provides the key namespaces that the caching agent is authoritative for.
   *
   * @return Collection of key namespaces.
   */
  Collection<String> getAuthoritativeKeyNames() {
    return getProvidedDataTypes().stream()
        .filter(agentDataType -> agentDataType.getAuthority().equals(AUTHORITATIVE))
        .map(AgentDataType::getTypeName)
        .collect(Collectors.toSet());
  }

  Map<String, Collection<String>> computeEvictableData(
      Collection<CacheData> newData, ProviderCache providerCache) {

    // Only compute evictions for LAMBDA_FUNCTIONS namespace
    // LAMBDA_APPLICATIONS namespace is evicted separately by application cleanup agents
    String lambdaFunctionsNamespace = LAMBDA_FUNCTIONS.ns;
    Set<String> oldKeys =
        providerCache.getIdentifiers(lambdaFunctionsNamespace).stream()
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
    evictionsByKey.put(lambdaFunctionsNamespace, evictedKeys);
    String prettyKeyName =
        CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, lambdaFunctionsNamespace);

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
