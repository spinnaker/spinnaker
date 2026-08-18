/*
 * Copyright (c) 2019 Schibsted Media Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.provider.agent;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.STACKS;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;

import com.google.common.collect.ImmutableMap;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.cache.Keys;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.cache.OnDemandType;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeChangeSetResponse;
import software.amazon.awssdk.services.cloudformation.model.DescribeStackEventsRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksRequest;
import software.amazon.awssdk.services.cloudformation.model.DescribeStacksResponse;
import software.amazon.awssdk.services.cloudformation.model.ListChangeSetsRequest;
import software.amazon.awssdk.services.cloudformation.model.ListChangeSetsResponse;
import software.amazon.awssdk.services.cloudformation.model.Output;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackEvent;
import software.amazon.awssdk.services.cloudformation.model.Tag;

@Slf4j
public class AmazonCloudFormationCachingAgent
    implements CachingAgent, OnDemandAgent, AccountAware, AgentIntervalAware {
  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final String region;
  private final OnDemandMetricsSupport metricsSupport;
  protected static final String ON_DEMAND_TYPE = "onDemand";

  static final Set<AgentDataType> types =
      new HashSet<>(Collections.singletonList(AUTHORITATIVE.forType(STACKS.getNs())));

  public AmazonCloudFormationCachingAgent(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      Registry registry) {
    this.amazonClientProvider = amazonClientProvider;
    this.account = account;
    this.region = region;
    this.metricsSupport =
        new OnDemandMetricsSupport(
            registry,
            this,
            String.format("%s:%s", AmazonCloudProvider.ID, OnDemandType.CloudFormation));
  }

  @Override
  public String getProviderName() {
    return AwsInfrastructureProvider.PROVIDER_NAME;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType();
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return this.metricsSupport;
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return OnDemandType.CloudFormation.equals(type) && cloudProvider.equals(AmazonCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (shouldHandle(data)) {
      log.info(
          "Updating CloudFormation cache for account: {} and region: {}",
          account.getName(),
          this.region);

      DescribeStacksRequest describeStacksRequest =
          Optional.ofNullable((String) data.get("stackName"))
              .map(stackName -> DescribeStacksRequest.builder().stackName(stackName).build())
              .orElse(DescribeStacksRequest.builder().build());

      CacheResult result = queryStacks(providerCache, describeStacksRequest, true);
      Collection<String> keys =
          result.getCacheResults().get("stacks").stream()
              .map(cachedata -> cachedata.getId())
              .collect(Collectors.toList());

      keys.forEach(
          key -> {
            CacheData cacheData =
                new DefaultCacheData(
                    key,
                    (int) Duration.ofMinutes(10).getSeconds(),
                    ImmutableMap.of(
                        "cacheTime",
                        System.currentTimeMillis(),
                        "cacheResults",
                        result,
                        "processedCount",
                        0),
                    /* relationShips= */ ImmutableMap.of());
            providerCache.putCacheData(ON_DEMAND.getNs(), cacheData);
          });
      return new OnDemandResult(getOnDemandAgentType(), result, Collections.emptyMap());
    } else {
      return null;
    }
  }

  private boolean shouldHandle(Map<String, ?> data) {
    String credentials = (String) data.get("credentials");
    List<String> region = (List<String>) data.get("region");
    return data.isEmpty()
        || (account.getName().equals(credentials)
            && region != null
            && region.contains(this.region));
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<String> ownedKeys =
        providerCache.filterIdentifiers(
            ON_DEMAND.getNs(), Keys.getCloudFormationKey("*", this.region, this.getAccountName()));
    Collection<Map<String, Object>> onDemandEntriesToReturn =
        providerCache.getAll(ON_DEMAND.getNs(), ownedKeys).stream()
            .map(
                cacheData -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("details", Keys.parse(cacheData.getId()));
                  map.put("moniker", cacheData.getAttributes().get("moniker"));
                  map.put("cacheTime", cacheData.getAttributes().get("cacheTime"));
                  map.put("processedCount", cacheData.getAttributes().get("processedCount"));
                  map.put("processedTime", cacheData.getAttributes().get("processedTime"));
                  return map;
                })
            .collect(toImmutableList());

    return onDemandEntriesToReturn;
  }

  @Override
  public String getAgentType() {
    return String.format(
        "%s/%s/%s",
        account.getName(), region, AmazonCloudFormationCachingAgent.class.getSimpleName());
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
    log.info(getAgentType() + ": agent is starting");

    List<String> keepInOnDemand = new ArrayList<>();
    List<String> evictFromOnDemand = new ArrayList<>();
    Long start = System.currentTimeMillis();

    CacheResult stacks = queryStacks(providerCache, DescribeStacksRequest.builder().build(), false);
    Collection<String> keys =
        stacks.getCacheResults().get("stacks").stream()
            .map(cachedata -> cachedata.getId())
            .collect(Collectors.toList());

    Collection<CacheData> onDemandEntries = providerCache.getAll(ON_DEMAND.getNs(), keys);
    if (!CollectionUtils.isEmpty(onDemandEntries)) {
      onDemandEntries.forEach(
          cacheData -> {
            long cacheTime = (long) cacheData.getAttributes().get("cacheTime");
            if (cacheTime < start && (int) cacheData.getAttributes().get("processedCount") > 0) {
              evictFromOnDemand.add(cacheData.getId());
            } else {
              keepInOnDemand.add(cacheData.getId());
            }
          });
    }
    onDemandEntries = providerCache.getAll(ON_DEMAND.getNs(), keepInOnDemand);
    if (!CollectionUtils.isEmpty(onDemandEntries)) {
      providerCache
          .getAll(ON_DEMAND.getNs(), keepInOnDemand)
          .forEach(
              cacheData -> {
                cacheData.getAttributes().put("processedTime", System.currentTimeMillis());
                int processedCount = (Integer) cacheData.getAttributes().get("processedCount");
                cacheData.getAttributes().put("processedCount", processedCount + 1);
                providerCache.putCacheData(ON_DEMAND.getNs(), cacheData);
              });
    }
    providerCache.evictDeletedItems(ON_DEMAND.getNs(), evictFromOnDemand);

    return stacks;
  }

  public CacheResult queryStacks(
      ProviderCache providerCache,
      DescribeStacksRequest describeStacksRequest,
      boolean isPartialResult) {
    log.info("Describing items in {}, partial result: {}", getAgentType(), isPartialResult);
    CloudFormationClient cloudFormationClient =
        amazonClientProvider.getAmazonCloudFormationV2(account, region);

    ArrayList<CacheData> stackCacheData = new ArrayList<>();

    try {
      String nextToken = null;
      do {
        DescribeStacksRequest requestToSend =
            nextToken != null
                ? describeStacksRequest.toBuilder().nextToken(nextToken).build()
                : describeStacksRequest;

        DescribeStacksResponse describeStacksResponse =
            cloudFormationClient.describeStacks(requestToSend);
        List<Stack> stacks = describeStacksResponse.stacks();

        for (Stack stack : stacks) {
          Map<String, Object> stackAttributes = getStackAttributes(stack, cloudFormationClient);
          String stackCacheKey =
              Keys.getCloudFormationKey(stack.stackId(), region, account.getName());
          Map<String, Collection<String>> relationships = new HashMap<>();
          relationships.put(STACKS.getNs(), Collections.singletonList(stackCacheKey));
          stackCacheData.add(new DefaultCacheData(stackCacheKey, stackAttributes, relationships));
        }

        nextToken = describeStacksResponse.nextToken();
      } while (nextToken != null);
    } catch (CloudFormationException e) {
      log.error("Error retrieving stacks", e);
    }

    log.info("Caching {} items in {}", stackCacheData.size(), getAgentType());
    HashMap<String, Collection<CacheData>> result = new HashMap<>();
    result.put(STACKS.getNs(), stackCacheData);
    return new DefaultCacheResult(result, isPartialResult);
  }

  private Map<String, Object> getStackAttributes(
      Stack stack, CloudFormationClient cloudFormationClient) {
    Map<String, Object> stackAttributes = new HashMap<>();
    stackAttributes.put("stackId", stack.stackId());
    stackAttributes.put(
        "tags", stack.tags().stream().collect(Collectors.toMap(Tag::key, Tag::value)));
    stackAttributes.put(
        "outputs",
        stack.outputs().stream().collect(Collectors.toMap(Output::outputKey, Output::outputValue)));
    stackAttributes.put("stackName", stack.stackName());
    stackAttributes.put("region", region);
    stackAttributes.put("accountName", account.getName());
    stackAttributes.put("accountId", account.getAccountId());
    stackAttributes.put("stackStatus", stack.stackStatusAsString());
    stackAttributes.put(
        "creationTime", stack.creationTime() != null ? Date.from(stack.creationTime()) : null);
    stackAttributes.put("changeSets", getChangeSets(stack, cloudFormationClient));
    getStackStatusReason(stack, cloudFormationClient)
        .map(statusReason -> stackAttributes.put("stackStatusReason", statusReason));
    return stackAttributes;
  }

  private List<Map<String, Object>> getChangeSets(
      Stack stack, CloudFormationClient cloudFormationClient) {
    ListChangeSetsRequest.Builder listRequestBuilder =
        ListChangeSetsRequest.builder().stackName(stack.stackName());

    List<Map<String, Object>> changeSets = new ArrayList<>();
    String nextToken = null;
    do {
      if (nextToken != null) {
        listRequestBuilder.nextToken(nextToken);
      }

      ListChangeSetsResponse listChangeSetsResponse =
          cloudFormationClient.listChangeSets(listRequestBuilder.build());

      changeSets.addAll(
          listChangeSetsResponse.summaries().stream()
              .map(
                  summary -> {
                    Map<String, Object> changeSetAttributes = new HashMap<>();
                    changeSetAttributes.put("name", summary.changeSetName());
                    changeSetAttributes.put("status", summary.statusAsString());
                    changeSetAttributes.put("statusReason", summary.statusReason());
                    DescribeChangeSetRequest describeChangeSetRequest =
                        DescribeChangeSetRequest.builder()
                            .changeSetName(summary.changeSetName())
                            .stackName(stack.stackName())
                            .build();
                    DescribeChangeSetResponse describeChangeSetResponse =
                        cloudFormationClient.describeChangeSet(describeChangeSetRequest);
                    changeSetAttributes.put(
                        "changes",
                        describeChangeSetResponse.changes().stream()
                            .map(
                                change -> {
                                  Map<String, Object> changeMap = new HashMap<>();
                                  changeMap.put("type", change.typeAsString());
                                  if (change.resourceChange() != null) {
                                    Map<String, Object> resourceChange = new HashMap<>();
                                    resourceChange.put(
                                        "action", change.resourceChange().actionAsString());
                                    resourceChange.put(
                                        "logicalResourceId",
                                        change.resourceChange().logicalResourceId());
                                    resourceChange.put(
                                        "physicalResourceId",
                                        change.resourceChange().physicalResourceId());
                                    resourceChange.put(
                                        "resourceType", change.resourceChange().resourceType());
                                    resourceChange.put(
                                        "replacement",
                                        change.resourceChange().replacementAsString());
                                    changeMap.put("resourceChange", resourceChange);
                                  }
                                  return changeMap;
                                })
                            .collect(Collectors.toList()));
                    log.debug(
                        "Adding change set attributes for stack {}: {}",
                        stack.stackName(),
                        changeSetAttributes);
                    return changeSetAttributes;
                  })
              .collect(Collectors.toList()));

      nextToken = listChangeSetsResponse.nextToken();
    } while (nextToken != null);

    return changeSets;
  }

  private Optional<String> getStackStatusReason(
      Stack stack, CloudFormationClient cloudFormationClient) {
    if (stack.stackStatusAsString().endsWith("ROLLBACK_COMPLETE")) {
      DescribeStackEventsRequest request =
          DescribeStackEventsRequest.builder().stackName(stack.stackName()).build();
      return cloudFormationClient.describeStackEvents(request).stackEvents().stream()
          .filter(e -> e.resourceStatusAsString().endsWith("FAILED"))
          .findFirst()
          .map(StackEvent::resourceStatusReason);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public Long getAgentInterval() {
    return 60000L;
  }
}
