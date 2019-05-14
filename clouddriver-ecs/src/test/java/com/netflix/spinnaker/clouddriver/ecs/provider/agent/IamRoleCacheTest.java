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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
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
import org.junit.Test;
import spock.lang.Subject;

public class IamRoleCacheTest extends CommonCachingAgent {
  @Subject private final IamRoleCacheClient client = new IamRoleCacheClient(providerCache);
  private final AmazonIdentityManagement iam = mock(AmazonIdentityManagement.class);
  private final IamPolicyReader iamPolicyReader = mock(IamPolicyReader.class);

  @Subject
  private final IamRoleCachingAgent agent =
      new IamRoleCachingAgent(
          netflixAmazonCredentials, clientProvider, credentialsProvider, iamPolicyReader);

  @Test
  public void shouldRetrieveFromWrittenCache() {
    // Given
    when(clientProvider.getIam(any(NetflixAmazonCredentials.class), anyString(), anyBoolean()))
        .thenReturn(iam);
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

    Role role = new Role();
    role.setArn(roleArn);
    role.setRoleName(name);
    when(iam.listRoles(any(ListRolesRequest.class)))
        .thenReturn(new ListRolesResult().withRoles(role).withIsTruncated(false));
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

    assertTrue("Expected CacheData to be returned but null is returned", cacheData != null);
    assertTrue("Expected 1 CacheData but returned " + cacheData.size(), cacheData.size() == 1);
    String retrievedKey = cacheData.iterator().next().getId();
    assertTrue(
        "Expected CacheData with ID " + key + " but retrieved ID " + retrievedKey,
        retrievedKey.equals(key));

    assertTrue(
        "Expected the IAM Role to be " + iamRole + " but got " + returnedIamRole,
        iamRole.equals(returnedIamRole));
  }
}
