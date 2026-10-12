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

import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.AbstractEcsAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Specification

/**
 * Shared by the ecs-native converters that reuse the original provider's {@code
 * ModifyServiceDescription}/operations verbatim (Destroy/Disable/Enable), mirroring {@code
 * com.netflix.spinnaker.clouddriver.ecs.deploy.converters.ModifyServiceAtomicOperationConverterSpec}
 * for the original {@code ecs} provider's equivalents.
 */
abstract class EcsNativeModifyServiceAtomicOperationConverterSpec<T extends AbstractEcsAtomicOperation> extends Specification {
  def accountCredentialsProvider = Mock(AccountCredentialsProvider)

  abstract AbstractAtomicOperationsCredentialsSupport getConverter()

  def 'should convert'() {
    given:
    def converter = getConverter()
    converter.accountCredentialsProvider = accountCredentialsProvider

    def serverGroupName = "test"
    def input = [serverGroupName: serverGroupName, regions: ["us-west-1"], credentials: "test"]

    accountCredentialsProvider.getCredentials(_) >> TestCredential.named('test')

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof ModifyServiceDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof T
  }
}
