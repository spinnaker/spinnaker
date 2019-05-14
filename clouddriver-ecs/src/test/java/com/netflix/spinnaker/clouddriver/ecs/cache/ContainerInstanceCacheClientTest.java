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

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.amazonaws.services.ecs.model.ContainerInstance;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ContainerInstanceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ContainerInstanceCachingAgent;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import spock.lang.Subject;

public class ContainerInstanceCacheClientTest extends CommonCacheClient {
  @Subject
  private final ContainerInstanceCacheClient client = new ContainerInstanceCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    // Given
    String containerInstanceArn =
        "arn:aws:ecs:"
            + REGION
            + ":012345678910:container-instance/14e8cce9-0b16-4af4-bfac-a85f7587aa98";
    String key = Keys.getContainerInstanceKey(ACCOUNT, REGION, containerInstanceArn);

    ContainerInstance containerInstance = new ContainerInstance();
    containerInstance.setEc2InstanceId("i-deadbeef");
    containerInstance.setContainerInstanceArn(containerInstanceArn);

    Map<String, Object> attributes =
        ContainerInstanceCachingAgent.convertContainerInstanceToAttributes(containerInstance);
    when(cacheView.get(CONTAINER_INSTANCES.toString(), key))
        .thenReturn(new DefaultCacheData(key, attributes, Collections.emptyMap()));

    // When
    com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance ecsContainerInstance =
        client.get(key);

    // Then
    assertTrue(
        "Expected the EC2 instance ID to be "
            + containerInstance.getEc2InstanceId()
            + " but got "
            + ecsContainerInstance.getEc2InstanceId(),
        containerInstance.getEc2InstanceId().equals(ecsContainerInstance.getEc2InstanceId()));

    assertTrue(
        "Expected the container instance ARN to be "
            + containerInstance.getContainerInstanceArn()
            + " but got "
            + ecsContainerInstance.getArn(),
        containerInstance.getContainerInstanceArn().equals(ecsContainerInstance.getArn()));
  }
}
