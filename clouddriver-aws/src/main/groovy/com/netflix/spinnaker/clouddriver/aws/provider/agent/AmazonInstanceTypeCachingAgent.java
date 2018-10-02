/*
 * Copyright 2015 Netflix, Inc.
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.cache.Keys;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

public class AmazonInstanceTypeCachingAgent implements CachingAgent {

  private static final TypeReference<Map<String, Object>> ATTRIBUTES
    = new TypeReference<Map<String, Object>>() {};

  // https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonEC2/current/us-west-2/index.json
  private final String region;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final URI pricingUri;
  private final HttpHost pricingHost;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper =
    new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);


  public AmazonInstanceTypeCachingAgent(String region,
                                        AccountCredentialsRepository accountCredentialsRepository) {
    this(region, accountCredentialsRepository, HttpClients.createDefault());
  }

  //VisibleForTesting
  AmazonInstanceTypeCachingAgent(String region,
                                 AccountCredentialsRepository accountCredentialsRepository,
                                 HttpClient httpClient) {
    this.region = region;
    this.accountCredentialsRepository = accountCredentialsRepository;
    pricingHost = HttpHost.create("https://pricing.us-east-1.amazonaws.com");
    pricingUri = URI.create("/offers/v1.0/aws/AmazonEC2/current/" + region + "/index.json");
    this.httpClient = httpClient;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.unmodifiableList(
      Arrays.asList(
        new AgentDataType(
          Keys.Namespace.INSTANCE_TYPES.getNs(), AgentDataType.Authority.AUTHORITATIVE),
        new AgentDataType(
          getAgentType(), AgentDataType.Authority.AUTHORITATIVE)));
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    try {
      Set<String> matchingAccounts = accountCredentialsRepository.getAll()
        .stream()
        .filter(AmazonCredentials.class::isInstance)
        .map(AmazonCredentials.class::cast)
        .filter(ac -> ac.getRegions().stream().anyMatch(r -> region.equals(r.getName())))
        .map(AccountCredentials::getName)
        .collect(Collectors.toSet());

      if (matchingAccounts.isEmpty()) {
        return new DefaultCacheResult(Collections.emptyMap());
      }

      CacheData metadata = providerCache.get(
        getAgentType(),
        "metadata",
        RelationshipCacheFilter.none());
      MetadataAttributes metadataAttributes = null;
      if (metadata != null) {
        metadataAttributes = objectMapper.convertValue(metadata.getAttributes(), MetadataAttributes.class);
      }

      Set<String> instanceTypes = null;
      if (metadataAttributes != null
        && metadataAttributes.etag != null
        && metadataAttributes.cachedInstanceTypes != null) {

        //we have enough from a previous request to not re-request if the etag is unchanged..
        HttpResponse headResponse = httpClient.execute(pricingHost, new HttpHead(pricingUri));
        EntityUtils.consumeQuietly(headResponse.getEntity());
        if (headResponse.getStatusLine().getStatusCode() != 200) {
          throw new Exception("failed to read instance type metadata for " + region + ": "
            + headResponse.getStatusLine().toString());
        }

        Optional<String> etag = getEtagHeader(headResponse);

        if (etag.filter(metadataAttributes.etag::equals).isPresent()) {
          instanceTypes = metadataAttributes.cachedInstanceTypes;
        }
      }
      if (instanceTypes == null) {
        HttpResponse getResponse = httpClient.execute(pricingHost, new HttpGet(pricingUri));
        if (getResponse.getStatusLine().getStatusCode() != 200) {
          EntityUtils.consumeQuietly(getResponse.getEntity());
          throw new Exception("failed to read instance type data for " + region + ": "
            + getResponse.getStatusLine().toString());
        }
        Optional<String> etag = getEtagHeader(getResponse);
        HttpEntity entity = getResponse.getEntity();
        instanceTypes = fromStream(entity.getContent());
        EntityUtils.consumeQuietly(entity);
        if (etag.isPresent()) {
          metadataAttributes = new MetadataAttributes();
          metadataAttributes.etag = etag.get();
          metadataAttributes.cachedInstanceTypes = new HashSet<>(instanceTypes);
          metadata = new DefaultCacheData(
            "metadata",
            objectMapper.convertValue(metadataAttributes, ATTRIBUTES),
            Collections.emptyMap());
        } else {
          metadata = null;
        }
      }
      Map<String, Collection<String>> evictions = new HashMap<>();
      Map<String, Collection<CacheData>> cacheResults = new HashMap<>();
      List<CacheData> instanceTypeData = new ArrayList<>();
      cacheResults.put(Keys.Namespace.INSTANCE_TYPES.getNs(), instanceTypeData);
      if (metadata != null) {
        cacheResults.put(getAgentType(), Collections.singleton(metadata));
      } else {
        evictions.put(getAgentType(), Collections.singleton("metadata"));
      }

      for (String instanceType : instanceTypes) {
        for (String account : matchingAccounts) {
          Map<String, Object> instanceTypeAttributes = new HashMap<>();
          instanceTypeAttributes.put("account", account);
          instanceTypeAttributes.put("region", region);
          instanceTypeAttributes.put("name", instanceType);
          instanceTypeData.add(
            new DefaultCacheData(
              Keys.getInstanceTypeKey(instanceType, region, account),
              instanceTypeAttributes,
              Collections.emptyMap()));
        }
      }

      return new DefaultCacheResult(cacheResults, evictions);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  Optional<String> getEtagHeader(HttpResponse response) {
    return Optional.ofNullable(response)
      .map(r -> r.getFirstHeader("ETag"))
      .map(Header::getElements)
      .filter(e -> e.length > 0)
      .map(e -> e[0].getName());
  }

  @Override
  public String getAgentType() {
    return getClass().getSimpleName() + "/" + region;
  }

  @Override
  public String getProviderName() {
    return AwsInfrastructureProvider.PROVIDER_NAME;
  }

  static class Offering {
    public String productFamily;
    public ComputeInstanceAttributes attributes;
  }

  static class ComputeInstanceAttributes {
    public String instanceType;

    @Override
    public String toString() {
      return instanceType;
    }
  }

  static class Offerings {
    public Map<String, Offering> products;
  }

  static class MetadataAttributes {
    public String etag;
    public Set<String> cachedInstanceTypes;
  }


  //visible for testing
  Set<String> fromStream(InputStream is) throws IOException {
    Offerings offerings = objectMapper.readValue(is, Offerings.class);
    Set<String> instanceTypes = offerings.products.values()
      .stream()
      .filter(o -> o.productFamily != null && o.productFamily.startsWith("Compute Instance"))
      .map(o -> o.attributes.instanceType)
      .filter(it -> it != null && !it.isEmpty())
      .collect(Collectors.toSet());

    return instanceTypes;
  }
}
