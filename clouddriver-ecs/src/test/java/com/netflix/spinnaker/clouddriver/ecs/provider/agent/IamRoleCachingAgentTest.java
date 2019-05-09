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

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import org.junit.Test;
import spock.lang.Subject;

import java.util.*;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class IamRoleCachingAgentTest extends CommonCachingAgent {
  private final AmazonIdentityManagement iam = mock(AmazonIdentityManagement.class);
  private final IamPolicyReader iamPolicyReader = mock(IamPolicyReader.class);
  @Subject
  private final IamRoleCachingAgent agent = new IamRoleCachingAgent(netflixAmazonCredentials, clientProvider, credentialsProvider, iamPolicyReader);

  @Test
  public void shouldGetListOfServices() {
    //Given
    int numberOfRoles = 3;
    IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
    iamTrustRelationship.setType("Service");
    iamTrustRelationship.setValue("ecs-tasks.amazonaws.com");

    Set<Role> roles = new HashSet<>();
    Set<IamRole> iamRoles = new HashSet<>();
    for (int x = 0; x < numberOfRoles; x++) {
      String arn = "iam-role-arn-" + x;
      String name = "iam-role-name-" + x;

      roles.add(new Role().withArn(arn).withRoleName(name));

      IamRole iamRole = new IamRole();
      iamRole.setAccountName(ACCOUNT);
      iamRole.setId(arn);
      iamRole.setName(name);
      iamRole.setTrustRelationships(Collections.singleton(iamTrustRelationship));
      iamRoles.add(iamRole);
    }

    when(clientProvider.getIam(any(NetflixAmazonCredentials.class), anyString(), anyBoolean())).thenReturn(iam);
    when(iam.listRoles(any(ListRolesRequest.class))).thenReturn(new ListRolesResult().withRoles(roles).withIsTruncated(false));
    when(iamPolicyReader.getTrustedEntities(any())).thenReturn(Collections.singleton(iamTrustRelationship));

    //When
    Set<IamRole> returnedRoles = agent.fetchIamRoles(iam, ACCOUNT);

    //Then
    assertEquals("Expected the list to contain " + numberOfRoles + " ECS IAM roles, but got " + returnedRoles.size(), returnedRoles.size(), numberOfRoles);
    for (IamRole iamRole : returnedRoles) {
      assertTrue("Expected the IAM role to be in  " + iamRoles + " list but it was not. The IAM role is: " + iamRole, iamRoles.contains(iamRole));
    }
  }

  @Test
  public void shouldGenerateFreshData() {
    //Given
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

    //When
    Map<String, Collection<CacheData>> dataMap = agent.generateFreshData(iamRoles);

    //Then
    assertTrue("Expected the data map to contain 1 namespace, but it contains " + dataMap.keySet().size() + " namespaces.", dataMap.keySet().size() == 1);
    assertTrue("Expected the data map to contain " + IAM_ROLE.toString() + " namespace, but it contains " + dataMap.keySet() + " namespaces.", dataMap.containsKey(IAM_ROLE.toString()));
    assertTrue("Expected there to be " + numberOfRoles + " CacheData, instead there is  " + dataMap.get(IAM_ROLE.toString()).size(), dataMap.get(IAM_ROLE.toString()).size() == numberOfRoles);

    for (CacheData cacheData : dataMap.get(IAM_ROLE.toString())) {
      assertTrue("Expected the key to be one of the following keys: " + keys.toString() + ". The key is: " + cacheData.getId() + ".", keys.contains(cacheData.getId()));
    }

  }
}
