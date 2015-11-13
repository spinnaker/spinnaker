/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kato.titan.deploy.converters
import com.netflix.spinnaker.clouddriver.titan.TitanClientProvider
import com.netflix.spinnaker.clouddriver.titan.TitanOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.orchestration.AtomicOperations
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.kato.titan.deploy.description.TitanDeployDescription
import com.netflix.spinnaker.kato.titan.deploy.handlers.TitanDeployHandler
import com.netflix.spinnaker.kato.titan.deploy.ops.CloneTitanServerGroupAtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
/**
 *
 *
 */
@TitanOperation(AtomicOperations.CLONE_SERVER_GROUP)
@Component
class CloneTitanServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  private final TitanClientProvider titanClientProvider
  private final TitanDeployHandler titanDeployHandler

  @Autowired
  CloneTitanServerGroupAtomicOperationConverter(TitanClientProvider titanClientProvider, TitanDeployHandler titanDeployHandler) {
    this.titanClientProvider = titanClientProvider
    this.titanDeployHandler = titanDeployHandler
  }

  @Override
  AtomicOperation convertOperation(Map input) {
    new CloneTitanServerGroupAtomicOperation(titanClientProvider, convertDescription(input), titanDeployHandler)
  }

  @Override
  TitanDeployDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, TitanDeployDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}

