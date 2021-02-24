/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.*
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper
import spock.lang.Specification

import java.util.stream.Collectors

class EcsCredentialsLifeCyclerHandlerSpec extends Specification {

  EcsProvider ecsProvider
  def objectMapper = new ObjectMapper()
  def registry = new DefaultRegistry()

  def setup() {
    ecsProvider = new EcsProvider()
  }
  def credOne = new NetflixAssumeRoleEcsCredentials(TestCredential.assumeRoleNamed('one'), 'one-aws')
  def ecsAccountMapper = Mock(EcsAccountMapper)


  def 'it should add agents'() {

    given:
    def handler = new EcsCredentialsLifeCycleHandler(ecsProvider, null, null, registry, null, objectMapper, null, ecsAccountMapper)
    Set<Class> expectedClasses = [ ApplicationCachingAgent.class, IamRoleCachingAgent.class, EcsClusterCachingAgent.class, ServiceCachingAgent.class,
                         TaskCachingAgent.class, ContainerInstanceCachingAgent.class, TaskDefinitionCachingAgent.class,
                         TaskHealthCachingAgent.class, EcsCloudMetricAlarmCachingAgent.class, ScalableTargetsCachingAgent.class,
                         SecretCachingAgent.class, ServiceDiscoveryCachingAgent.class, TargetHealthCachingAgent.class,
                         ApplicationCachingAgent.class ]
    Set<Class> actualClasses =[]

    when:
    handler.credentialsAdded(credOne)

    then:
    1 * ecsAccountMapper.addMapEntry({it.getName() == credOne.getName()})
    ecsProvider.getAgents().size() == 24 // 2 * 11 + 1 + 1 ( One IamRoleCachingAgent and ApplicationCachingAgent per account )
    ecsProvider.getHealthAgents().size() == 4
    ecsProvider.getAgents().each({actualClasses.add(it.getClass())})
    (actualClasses - expectedClasses).isEmpty()
  }

  def 'it should remove agents'() {

    given:
    ecsProvider.addAgents(Collections.singletonList(new TargetHealthCachingAgent(credOne, "region", null, null, objectMapper)))
    def handler = new EcsCredentialsLifeCycleHandler(ecsProvider, null, null, registry, null, objectMapper, null, ecsAccountMapper)

    when:
    handler.credentialsDeleted(credOne)

    then:
    ecsProvider.getAgents().isEmpty()
    ecsProvider.getHealthAgents().isEmpty()
  }

  def 'it should update agents'() {
    given:
    ecsProvider.addAgents(Collections.singletonList(new TargetHealthCachingAgent(credOne, "region", null, null, objectMapper)))
    def handler = new EcsCredentialsLifeCycleHandler(ecsProvider, null, null, registry, null, objectMapper, null, ecsAccountMapper)

    when:
    handler.credentialsUpdated(credOne)

    then:
    ecsProvider.getAgents().stream()
      .filter({ agent -> agent instanceof TargetHealthCachingAgent })
      .collect(Collectors.toList())
    .size() == 2
    ecsProvider.getHealthAgents().size() == 4
  }
}
