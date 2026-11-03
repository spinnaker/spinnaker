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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.StopServiceDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.ops.StopServiceAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport

class EcsNativeStopServiceAtomicOperationConverterSpec
    extends EcsNativeCredentialsOnlyAtomicOperationConverterSpec<StopServiceDescription, StopServiceAtomicOperation> {
  @Override
  AbstractAtomicOperationsCredentialsSupport getConverter() {
    new EcsNativeStopServiceAtomicOperationConverter(objectMapper: new ObjectMapper())
  }

  @Override
  Class<StopServiceDescription> getDescriptionType() {
    StopServiceDescription
  }

  def 'rejects native stop without reporting success'() {
    given:
    def converter = getConverter()
    converter.accountCredentialsProvider = accountCredentialsProvider
    accountCredentialsProvider.getCredentials(_) >> TestCredential.named('test')

    when:
    converter.convertOperation([credentials: 'test']).operate([])

    then:
    def exception = thrown(UnsupportedOperationException)
    exception.message.contains('STOP_SERVER_GROUP')
  }

  @Override
  Class<StopServiceAtomicOperation> getOperationType() {
    StopServiceAtomicOperation
  }
}
