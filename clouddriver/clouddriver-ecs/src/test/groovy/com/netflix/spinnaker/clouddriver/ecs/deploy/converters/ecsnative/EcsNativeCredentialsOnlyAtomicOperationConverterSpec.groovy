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

package com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ecsnative

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

/**
 * Shared by the ecs-native converters whose description is still an unimplemented {@code
 * AbstractAmazonCredentialsDescription} stub (Clone/Start/Stop/DeleteScalingPolicy/
 * UpsertScalingPolicy) -- there's nothing beyond {@code credentials} to convert yet, so this only
 * confirms the routing (right description type in, right operation type out) rather than any
 * field mapping.
 */
abstract class EcsNativeCredentialsOnlyAtomicOperationConverterSpec<
    D extends AbstractAmazonCredentialsDescription, O extends AtomicOperation> extends Specification {
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)

  abstract AbstractAtomicOperationsCredentialsSupport getConverter()

  abstract Class<D> getDescriptionType()

  abstract Class<O> getOperationType()

  def 'should convert'() {
    given:
    def converter = getConverter()
    converter.accountCredentialsProvider = accountCredentialsProvider

    def input = [credentials: 'test']

    accountCredentialsProvider.getCredentials(_) >> TestCredential.named('test')

    when:
    def description = converter.convertDescription(input)

    then:
    getDescriptionType().isInstance(description)

    when:
    def operation = converter.convertOperation(input)

    then:
    getOperationType().isInstance(operation)
  }
}
