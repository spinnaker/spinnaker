/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import spock.lang.Specification

class EcsNativeLoadBalancerProviderSpec extends Specification {

  def delegate = Mock(EcsLoadBalancerProvider)
  def provider = new EcsNativeLoadBalancerProvider(delegate)

  def 'reports the ecs-native cloud provider id regardless of the delegate'() {
    expect:
    provider.getCloudProvider() == EcsNativeCloudProvider.ID
    provider.getCloudProvider() != delegate.getCloudProvider()
  }

  def 'forwards provider-scoped lookups to the delegate unchanged'() {
    given:
    def item = Mock(LoadBalancerProvider.Item)
    def details = [Mock(LoadBalancerProvider.Details)]

    when:
    def listed = provider.list()
    def found = provider.get('myapp')
    def byName = provider.byAccountAndRegionAndName('test', 'us-east-1', 'myapp-lb')

    then:
    1 * delegate.list() >> [item]
    1 * delegate.get('myapp') >> item
    1 * delegate.byAccountAndRegionAndName('test', 'us-east-1', 'myapp-lb') >> details
    listed == [item]
    found == item
    byName == details
  }

  def 'contributes nothing to the application-wide load balancer aggregate'() {
    when:
    def result = provider.getApplicationLoadBalancers('myapp')

    then: 'the ecs provider already reports them; LoadBalancerController flattens all providers without dedupe'
    0 * delegate.getApplicationLoadBalancers(_)
    result.isEmpty()
  }
}
