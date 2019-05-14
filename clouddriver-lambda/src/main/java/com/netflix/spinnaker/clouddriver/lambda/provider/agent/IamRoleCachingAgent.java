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
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.IAM_ROLE;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.IamRole;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IamRoleCachingAgent implements CachingAgent, CustomScheduledAgent {
  private static final long POLL_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30);
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final Collection<AgentDataType> types =
      Collections.singletonList(AUTHORITATIVE.forType(IAM_ROLE.toString()));

  private final ObjectMapper objectMapper;

  private AmazonClientProvider amazonClientProvider;
  private NetflixAmazonCredentials account;
  private String accountName;

  IamRoleCachingAgent(
      ObjectMapper objectMapper,
      NetflixAmazonCredentials account,
      AmazonClientProvider amazonClientProvider) {
    this.objectMapper = objectMapper;

    this.account = account;
    this.accountName = account.getName();
    this.amazonClientProvider = amazonClientProvider;
  }

  @Override
  public String getAgentType() {
    return accountName + "/" + getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AwsProvider.PROVIDER_NAME;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public long getPollIntervalMillis() {
    return POLL_INTERVAL_MILLIS;
  }

  @Override
  public long getTimeoutMillis() {
    return DEFAULT_TIMEOUT_MILLIS;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonIdentityManagement iam =
        amazonClientProvider.getIam(account, Regions.DEFAULT_REGION.getName(), false);

    Set<IamRole> cacheableRoles = fetchIamRoles(iam, accountName);
    Map<String, Collection<CacheData>> newDataMap = generateFreshData(cacheableRoles);
    Collection<CacheData> newData = newDataMap.get(IAM_ROLE.toString());

    Set<String> oldKeys =
        providerCache.getAll(IAM_ROLE.toString()).stream()
            .map(CacheData::getId)
            .filter(this::keyAccountFilter)
            .collect(Collectors.toSet());
    Map<String, Collection<String>> evictionsByKey = computeEvictableData(newData, oldKeys);

    logUpcomingActions(newDataMap, evictionsByKey);

    return new DefaultCacheResult(newDataMap, evictionsByKey);
  }

  private void logUpcomingActions(
      Map<String, Collection<CacheData>> newDataMap,
      Map<String, Collection<String>> evictionsByKey) {
    log.info(
        String.format(
            "Caching %s IAM roles in %s for account %s",
            newDataMap.get(IAM_ROLE.toString()).size(), getAgentType(), accountName));

    if (evictionsByKey.get(IAM_ROLE.toString()).size() > 0) {
      log.info(
          String.format(
              "Evicting %s IAM roles in %s for account %s",
              evictionsByKey.get(IAM_ROLE.toString()).size(), getAgentType(), accountName));
    }
  }

  private Map<String, Collection<String>> computeEvictableData(
      Collection<CacheData> newData, Collection<String> oldKeys) {

    Set<String> newKeys = newData.stream().map(CacheData::getId).collect(Collectors.toSet());

    Set<String> evictedKeys = new HashSet<>();
    for (String oldKey : oldKeys) {
      if (!newKeys.contains(oldKey)) {
        evictedKeys.add(oldKey);
      }
    }
    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(IAM_ROLE.toString(), evictedKeys);
    return evictionsByKey;
  }

  private Map<String, Collection<CacheData>> generateFreshData(Set<IamRole> cacheableRoles) {
    Collection<CacheData> dataPoints = new HashSet<>();
    Map<String, Collection<CacheData>> newDataMap = new HashMap<>();

    for (IamRole iamRole : cacheableRoles) {
      String key = Keys.getIamRoleKey(accountName, iamRole.getName());
      Map<String, Object> attributes = convertIamRoleToAttributes(iamRole);

      CacheData data = new DefaultCacheData(key, attributes, Collections.emptyMap());
      dataPoints.add(data);
    }

    newDataMap.put(IAM_ROLE.toString(), dataPoints);
    return newDataMap;
  }

  private Set<IamRole> fetchIamRoles(AmazonIdentityManagement iam, String accountName) {
    Set<IamRole> cacheableRoles = new HashSet<>();
    String marker = null;
    do {
      ListRolesRequest request = new ListRolesRequest();
      if (marker != null) {
        request.setMarker(marker);
      }

      ListRolesResult listRolesResult = iam.listRoles(request);
      List<Role> roles = listRolesResult.getRoles();
      for (Role role : roles) {
        cacheableRoles.add(
            new IamRole(
                role.getArn(),
                role.getRoleName(),
                accountName,
                getTrustedEntities(role.getAssumeRolePolicyDocument())));
      }

      if (listRolesResult.isTruncated()) {
        marker = listRolesResult.getMarker();
      } else {
        marker = null;
      }

    } while (marker != null && marker.length() != 0);

    return cacheableRoles;
  }

  private boolean keyAccountFilter(String key) {
    Map<String, String> keyParts = Keys.parse(key);
    return keyParts != null && keyParts.get("account").equals(accountName);
  }

  private Set<IamTrustRelationship> getTrustedEntities(String urlEncodedPolicyDocument) {
    Set<IamTrustRelationship> trustedEntities = new HashSet<>();

    String decodedPolicyDocument = URLDecoder.decode(urlEncodedPolicyDocument);

    Map<String, Object> policyDocument;
    try {
      policyDocument = objectMapper.readValue(decodedPolicyDocument, Map.class);
      List<Map<String, Object>> statementItems =
          (List<Map<String, Object>>) policyDocument.get("Statement");
      for (Map<String, Object> statementItem : statementItems) {
        if ("sts:AssumeRole".equals(statementItem.get("Action"))) {
          Map<String, Object> principal = (Map<String, Object>) statementItem.get("Principal");

          for (Map.Entry<String, Object> principalEntry : principal.entrySet()) {
            if (principalEntry.getValue() instanceof List) {
              ((List) principalEntry.getValue())
                  .stream()
                      .forEach(
                          o ->
                              trustedEntities.add(
                                  new IamTrustRelationship(principalEntry.getKey(), o.toString())));
            } else {
              trustedEntities.add(
                  new IamTrustRelationship(
                      principalEntry.getKey(), principalEntry.getValue().toString()));
            }
          }
        }
      }
    } catch (IOException e) {
      log.error(
          "Unable to extract trusted entities (policyDocument: {})", urlEncodedPolicyDocument, e);
    }

    return trustedEntities;
  }

  private static Map<String, Object> convertIamRoleToAttributes(IamRole iamRole) {
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("name", iamRole.getName());
    attributes.put("accountName", iamRole.getAccountName());
    attributes.put("arn", iamRole.getId());
    attributes.put("trustRelationships", iamRole.getTrustRelationships());
    return attributes;
  }
}
