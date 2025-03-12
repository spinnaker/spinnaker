package com.netflix.spinnaker.clouddriver.cloudrun.provider.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.AgentIntervalAware;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.provider.CloudrunProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.security.CloudrunNamedAccountCredentials;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class AbstractCloudrunCachingAgent
    implements CachingAgent, AccountAware, AgentIntervalAware {
  public abstract String getSimpleName();

  private final String accountName;
  private final String providerName = CloudrunProvider.PROVIDER_NAME;
  private final CloudrunCloudProvider cloudrunCloudProvider = new CloudrunCloudProvider();
  private final ObjectMapper objectMapper;
  private final CloudrunNamedAccountCredentials credentials;

  public AbstractCloudrunCachingAgent(
      String accountName, ObjectMapper objectMapper, CloudrunNamedAccountCredentials credentials) {
    this.accountName = accountName;
    this.objectMapper = objectMapper;
    this.credentials = credentials;
  }

  public static void cache(
      Map<String, List<CacheData>> cacheResults,
      String cacheNamespace,
      Map<String, CacheData> cacheDataById) {
    cacheResults
        .get(cacheNamespace)
        .forEach(
            cacheData -> {
              CacheData existingCacheData = cacheDataById.get(cacheData.getId());
              if (existingCacheData == null) {
                cacheDataById.put(cacheData.getId(), cacheData);
              } else {
                existingCacheData.getAttributes().putAll(cacheData.getAttributes());
                cacheData
                    .getRelationships()
                    .forEach(
                        (relationshipName, relationships) -> {
                          existingCacheData.getRelationships().put(relationshipName, relationships);
                        });
              }
            });
  }

  public Long getAgentInterval() {
    if (this.credentials.getCachingIntervalSeconds() == null) {
      return TimeUnit.SECONDS.toMillis(60);
    }

    return TimeUnit.SECONDS.toMillis(this.credentials.getCachingIntervalSeconds());
  }

  public String getAccountName() {
    return accountName;
  }

  public String getProviderName() {
    return providerName;
  }

  static void executeIfRequestsAreQueued(BatchRequest batch) {
    try {
      if (batch.size() > 0) {
        batch.execute();
      }
    } catch (IOException e) {

    }
  }

  public CloudrunCloudProvider getCloudrunCloudProvider() {
    return cloudrunCloudProvider;
  }

  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  public final CloudrunNamedAccountCredentials getCredentials() {
    return credentials;
  }
}
