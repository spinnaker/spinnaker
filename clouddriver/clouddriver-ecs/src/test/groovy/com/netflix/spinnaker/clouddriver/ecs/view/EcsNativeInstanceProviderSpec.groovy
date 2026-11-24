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

package com.netflix.spinnaker.clouddriver.ecs.view

import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider
import spock.lang.Specification

class EcsNativeInstanceProviderSpec extends Specification {

  def delegate = Mock(EcsInstanceProvider)
  def provider = new EcsNativeInstanceProvider(delegate)

  def 'reports the ecs-native cloud provider id regardless of the delegate'() {
    expect:
    provider.getCloudProvider() == EcsNativeCloudProvider.ID
    provider.getCloudProvider() != delegate.getCloudProvider()
  }

  def 'forwards every read method to the delegate unchanged'() {
    when:
    provider.getInstance('test', 'us-east-1', 'task-id')
    provider.getConsoleOutput('test', 'us-east-1', 'task-id')

    then:
    1 * delegate.getInstance('test', 'us-east-1', 'task-id')
    1 * delegate.getConsoleOutput('test', 'us-east-1', 'task-id')
  }
}
