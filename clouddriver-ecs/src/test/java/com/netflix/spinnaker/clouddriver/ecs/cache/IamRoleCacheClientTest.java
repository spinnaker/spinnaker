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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.IAM_ROLE;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.IamRoleCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.IamRole;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamRoleCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import spock.lang.Subject;

public class IamRoleCacheClientTest extends CommonCacheClient {
  @Subject private final IamRoleCacheClient client = new IamRoleCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    // Given
    ObjectMapper mapper = new ObjectMapper();
    String name = "iam-role-name";
    String key = Keys.getIamRoleKey(ACCOUNT, name);

    IamRole iamRole = new IamRole();
    iamRole.setAccountName("account-name");
    iamRole.setId("test-id");
    iamRole.setName(name);
    IamTrustRelationship iamTrustRelationship = new IamTrustRelationship();
    iamTrustRelationship.setType("Service");
    iamTrustRelationship.setValue("ecs-tasks.amazonaws.com");
    iamRole.setTrustRelationships(Collections.singleton(iamTrustRelationship));

    Map<String, Object> attributes = IamRoleCachingAgent.convertIamRoleToAttributes(iamRole);
    attributes.put(
        "trustRelationships",
        Collections.singletonList(mapper.convertValue(iamTrustRelationship, Map.class)));

    when(cacheView.get(IAM_ROLE.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    IamRole returnedIamRole = client.get(key);

    // Then
    assertTrue(
        "Expected the IAM Role to be " + iamRole + " but got " + returnedIamRole,
        iamRole.equals(returnedIamRole));
  }
}
