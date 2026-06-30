/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.IamRoleCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListRolesRequest;
import software.amazon.awssdk.services.iam.model.ListRolesResponse;
import software.amazon.awssdk.services.iam.model.Role;
import spock.lang.Subject;

public class IamRoleCacheTest extends CommonCachingAgent {
  @Subject private final IamRoleCacheClient client = new IamRoleCacheClient(providerCache);
  private final IamClient iam = mock(IamClient.class);
  private final IamPolicyReader iamPolicyReader = mock(IamPolicyReader.class);

  @Subject
  private final IamRoleCachingAgent agent =
      new IamRoleCachingAgent(netflixAmazonCredentials, clientProvider, iamPolicyReader);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    when(clientProvider.getIamV2(any(NetflixAmazonCredentials.class), anyString())).thenReturn(iam);
    ObjectMapper mapper = new ObjectMapper();
    String name = "iam-role-name";
    String roleArn = "iam-role-arn";
    String key = Keys.getIamRoleKey(ACCOUNT, name);

    IamRole iamRole = new IamRole();
    iamRole.setAccountName(ACCOUNT);
    iamRole.setId(roleArn);
    iamRole.setName(name);
    IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
    iamTrustRelationship.setType("Service");
    iamTrustRelationship.setValue("ecs-tasks.amazonaws.com");
    iamRole.setTrustRelationships(Collections.singleton(iamTrustRelationship));

    Role role = Role.builder().arn(roleArn).roleName(name).build();
    when(iam.listRoles(any(ListRolesRequest.class)))
        .thenReturn(ListRolesResponse.builder().roles(role).isTruncated(false).build());
    when(iamPolicyReader.getTrustedEntities(anyString()))
        .thenReturn(Collections.singleton(iamTrustRelationship));

    // When
    CacheResult cacheResult = agent.loadData(providerCache);
    cacheResult
        .getCacheResults()
        .get(IAM_ROLE.toString())
        .iterator()
        .next()
        .getAttributes()
        .put(
            "trustRelationships",
            Collections.singletonList(mapper.convertValue(iamTrustRelationship, Map.class)));
    when(providerCache.get(IAM_ROLE.toString(), key))
        .thenReturn(cacheResult.getCacheResults().get(IAM_ROLE.toString()).iterator().next());

    // Then
    Collection<CacheData> cacheData =
        cacheResult.getCacheResults().get(Keys.Namespace.IAM_ROLE.toString());
    IamRole returnedIamRole = client.get(key);

    assertTrue(cacheData != null, "Expected CacheData to be returned but null is returned");
    assertTrue(cacheData.size() == 1, "Expected 1 CacheData but returned " + cacheData.size());
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        retrievedKey.equals(key),
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey);

    assertTrue(
        iamRole.equals(returnedIamRole),
        "Expected the IAM Role to be " + iamRole + " but got " + returnedIamRole);
  }
}
