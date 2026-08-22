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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import java.util.*;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListRolesRequest;
import software.amazon.awssdk.services.iam.model.ListRolesResponse;
import software.amazon.awssdk.services.iam.model.Role;
import spock.lang.Subject;

public class IamRoleCachingAgentTest extends CommonCachingAgent {
  private final IamClient iam = mock(IamClient.class);
  private final IamPolicyReader iamPolicyReader = mock(IamPolicyReader.class);

  @Subject
  private final IamRoleCachingAgent agent =
      new IamRoleCachingAgent(netflixAmazonCredentials, clientProvider, iamPolicyReader);

  @Test
  public void shouldGetListOfServices() {
    // Given
    int numberOfRoles = 3;
    IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
    iamTrustRelationship.setType("Service");
    iamTrustRelationship.setValue("ecs-tasks.amazonaws.com");

    List<Role> roles = new ArrayList<>();
    Set<IamRole> iamRoles = new HashSet<>();
    for (int x = 0; x < numberOfRoles; x++) {
      String arn = "iam-role-arn-" + x;
      String name = "iam-role-name-" + x;

      roles.add(Role.builder().arn(arn).roleName(name).build());

      IamRole iamRole = new IamRole();
      iamRole.setAccountName(ACCOUNT);
      iamRole.setId(arn);
      iamRole.setName(name);
      iamRole.setTrustRelationships(Collections.singleton(iamTrustRelationship));
      iamRoles.add(iamRole);
    }

    when(clientProvider.getIamV2(any(NetflixAmazonCredentials.class), anyString())).thenReturn(iam);
    when(iam.listRoles(any(ListRolesRequest.class)))
        .thenReturn(ListRolesResponse.builder().roles(roles).isTruncated(false).build());
    when(iamPolicyReader.getTrustedEntities(any()))
        .thenReturn(Collections.singleton(iamTrustRelationship));

    // When
    Set<IamRole> returnedRoles = agent.fetchIamRoles(iam, ACCOUNT);

    // Then
    assertEquals(
        returnedRoles.size(),
        numberOfRoles,
        "Expected the list to contain "
            + numberOfRoles
            + " ECS IAM roles, but got "
            + returnedRoles.size());
    for (IamRole iamRole : returnedRoles) {
      assertTrue(
          iamRoles.contains(iamRole),
          "Expected the IAM role to be in  "
              + iamRoles
              + " list but it was not. The IAM role is: "
              + iamRole);
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    // Given
    int numberOfRoles = 3;
    IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
    iamTrustRelationship.setType("Service");
    iamTrustRelationship.setValue("ecs-tasks.amazonaws.com");

    Set<String> keys = new HashSet<>();
    Set<IamRole> iamRoles = new HashSet<>();
    for (int x = 0; x < numberOfRoles; x++) {
      String name = "iam-role-name-" + x;

      IamRole iamRole = new IamRole();
      iamRole.setAccountName(ACCOUNT);
      iamRole.setId("iam-role-arn-" + x);
      iamRole.setName(name);
      iamRole.setTrustRelationships(Collections.singleton(iamTrustRelationship));
      iamRoles.add(iamRole);

      keys.add(Keys.getIamRoleKey(ACCOUNT, name));
    }

    // When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(iamRoles);

    // Then
    assertTrue(
        dataMap.keySet().size() == 1,
        "Expected the data map to contain 1 namespace, but it contains "
            + dataMap.keySet().size()
            + " namespaces.");
    assertTrue(
        dataMap.containsKey(IAM_ROLE.toString()),
        "Expected the data map to contain "
            + IAM_ROLE.toString()
            + " namespace, but it contains "
            + dataMap.keySet()
            + " namespaces.");
    assertTrue(
        dataMap.get(IAM_ROLE.toString()).size() == numberOfRoles,
        "Expected there to be "
            + numberOfRoles
            + " CacheData, instead there is  "
            + dataMap.get(IAM_ROLE.toString()).size());

    for (CacheData cacheData : dataMap.get(IAM_ROLE.toString())) {
      assertTrue(
          keys.contains(cacheData.getId()),
          "Expected the key to be one of the following keys: "
              + keys.toString()
              + ". The key is: "
              + cacheData.getId()
              + ".");
    }
  }

  @Test
  public void shouldGetDefaultRegion() {
    // given
    String defaultRegionName = Region.US_WEST_2.id();
    when(netflixAmazonCredentials.getRegions())
        .thenReturn(Collections.singletonList(new AmazonCredentials.AWSRegion("us-west-2", null)));

    // when
    String actualRegionName = agent.getIamRegion();

    // then
    assertEquals(
        defaultRegionName,
        actualRegionName,
        "Expected region to equal " + defaultRegionName + ", but got " + actualRegionName);
  }

  @Test
  public void shouldGetConfiguredIamRegion() {
    // given
    String expectedRegionName = "cn-north-1";
    when(netflixAmazonCredentials.getRegions())
        .thenReturn(
            Collections.singletonList(new AmazonCredentials.AWSRegion(expectedRegionName, null)));

    // when
    String actualRegionName = agent.getIamRegion();

    // then
    assertEquals(
        expectedRegionName,
        actualRegionName,
        "Expected region to equal " + expectedRegionName + ", but got " + actualRegionName);
  }

  @Test
  public void shouldFilterIdentifiersByAccountBeforeComputingEvictions() {
    // given
    ProviderCache providerCache = mock(ProviderCache.class);
    String expectedGlob = Keys.buildGlob(IAM_ROLE, ACCOUNT, null);
    Set<String> oldKeys =
        new HashSet<>(
            Arrays.asList(
                Keys.getIamRoleKey(ACCOUNT, "role-1"), Keys.getIamRoleKey(ACCOUNT, "role-2")));

    IamTrustRelationship trustRelationship = new IamTrustRelationship();
    trustRelationship.setType("Service");
    trustRelationship.setValue("ecs-tasks.amazonaws.com");

    Role role =
        Role.builder()
            .arn("arn:aws:iam::123456789012:role/test-role")
            .roleName("test-role")
            .build();

    when(clientProvider.getIamV2(any(NetflixAmazonCredentials.class), anyString())).thenReturn(iam);
    when(iam.listRoles(any(ListRolesRequest.class)))
        .thenReturn(ListRolesResponse.builder().roles(role).isTruncated(false).build());
    when(iamPolicyReader.getTrustedEntities(any()))
        .thenReturn(Collections.singleton(trustRelationship));
    when(providerCache.filterIdentifiers(IAM_ROLE.toString(), expectedGlob))
        .thenReturn(new ArrayList<>(oldKeys));

    // when
    CacheResult result = agent.loadData(providerCache);

    // then
    // Verify filterIdentifiers was called with the account-scoped glob, not loading all IAM roles
    verify(providerCache, times(1)).filterIdentifiers(IAM_ROLE.toString(), expectedGlob);

    // Verify the evictions contain only the filtered old keys
    assertTrue(
        result.getEvictions().containsKey(IAM_ROLE.toString()),
        "Expected evictions to contain IAM_ROLE namespace");
    assertEquals(
        oldKeys.size(),
        result.getEvictions().get(IAM_ROLE.toString()).size(),
        "Expected evictions to contain the filtered old keys");
  }
}
