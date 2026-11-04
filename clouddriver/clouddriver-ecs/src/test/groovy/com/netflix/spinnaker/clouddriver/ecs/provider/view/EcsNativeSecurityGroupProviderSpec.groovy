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
import spock.lang.Specification

class EcsNativeSecurityGroupProviderSpec extends Specification {

  def delegate = Mock(EcsSecurityGroupProvider)
  def provider = new EcsNativeSecurityGroupProvider(delegate)

  def 'reports the ecs-native cloud provider id regardless of the delegate'() {
    expect:
    provider.getCloudProvider() == EcsNativeCloudProvider.ID
    provider.getCloudProvider() != delegate.getCloudProvider()
  }

  def 'forwards every read method to the delegate unchanged'() {
    when:
    provider.getAll(true)
    provider.getAllByRegion(true, 'us-east-1')
    provider.getAllByAccount(true, 'test')
    provider.getAllByAccountAndName(true, 'test', 'myapp-sg')
    provider.getAllByAccountAndRegion(true, 'test', 'us-east-1')
    provider.get('test', 'us-east-1', 'myapp-sg', 'vpc-1')
    provider.getById('test', 'us-east-1', 'sg-1', 'vpc-1')

    then:
    1 * delegate.getAll(true)
    1 * delegate.getAllByRegion(true, 'us-east-1')
    1 * delegate.getAllByAccount(true, 'test')
    1 * delegate.getAllByAccountAndName(true, 'test', 'myapp-sg')
    1 * delegate.getAllByAccountAndRegion(true, 'test', 'us-east-1')
    1 * delegate.get('test', 'us-east-1', 'myapp-sg', 'vpc-1')
    1 * delegate.getById('test', 'us-east-1', 'sg-1', 'vpc-1')
  }
}
