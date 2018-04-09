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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SCALABLE_TARGETS

class ScalableTargetCachingAgentSpec extends Specification {
  def autoscaling = Mock(AWSApplicationAutoScaling)
  def clientProvider = Mock(AmazonClientProvider)
  def providerCache = Mock(ProviderCache)
  def credentialsProvider = Mock(AWSCredentialsProvider)
  def objectMapper = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  @Subject
  ScalableTargetsCachingAgent agent = new ScalableTargetsCachingAgent(CommonCachingAgent.netflixAmazonCredentials, 'us-west-1', clientProvider, credentialsProvider, objectMapper)

  def 'should get a list of cloud watch alarms'() {
    given:
    def givenScalableTargets = []
    0.upto(4, {
      givenScalableTargets << new ScalableTarget(
        serviceNamespace: ServiceNamespace.Ecs,
        resourceId: "service:/test-cluster/test-service-v00${it}",
        scalableDimension: 'scalable-dimension',
        minCapacity: 0,
        maxCapacity: 9001,
        roleARN: 'role-arn',
        creationTime: new Date()
      )
    })
    autoscaling.describeScalableTargets(_) >> new DescribeScalableTargetsResult().withScalableTargets(givenScalableTargets)

    when:
    def retrievedScalableTargets = agent.fetchScalableTargets(autoscaling)

    then:
    retrievedScalableTargets.containsAll(givenScalableTargets)
    givenScalableTargets.containsAll(retrievedScalableTargets)
  }

  def 'should generate fresh data'() {
    given:
    Set givenScalableTargets = []
    0.upto(4, {
      givenScalableTargets << new ScalableTarget(
        serviceNamespace: ServiceNamespace.Ecs,
        resourceId: "service:/test-cluster/test-service-v00${it}",
        scalableDimension: 'scalable-dimension',
        minCapacity: 0,
        maxCapacity: 9001,
        roleARN: 'role-arn'
      )
    })

    when:
    def cacheData = agent.generateFreshData(givenScalableTargets)

    then:
    cacheData.size() == 1
    cacheData.get(SCALABLE_TARGETS.ns).size() == givenScalableTargets.size()
    givenScalableTargets*.serviceNamespace.containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().serviceNamespace)
    givenScalableTargets*.resourceId.containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().resourceId)
    givenScalableTargets*.scalableDimension.containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().scalableDimension)
    givenScalableTargets*.minCapacity.containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().minCapacity)
    givenScalableTargets*.maxCapacity.containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().maxCapacity)
    givenScalableTargets*.roleARN.containsAll(cacheData.get(SCALABLE_TARGETS.ns)*.getAttributes().roleARN)
  }
}
