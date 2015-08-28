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
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.kato.titan.deploy.description.ResizeTitanServerGroupDescription
import com.netflix.spinnaker.kato.titan.deploy.ops.ResizeTitanServerGroupAtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
/**
 * @author sthadeshwar
 */
@Component("resizeTitanServerGroupDescription")
class ResizeTitanServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

    private final TitanClientProvider titanClientProvider

    @Autowired
    ResizeTitanServerGroupAtomicOperationConverter(TitanClientProvider titanClientProvider) {
      this.titanClientProvider = titanClientProvider
    }

    @Override
    AtomicOperation convertOperation(Map input) {
      new ResizeTitanServerGroupAtomicOperation(titanClientProvider, convertDescription(input))
    }

    @Override
    ResizeTitanServerGroupDescription convertDescription(Map input) {
      def converted = objectMapper.convertValue(input, ResizeTitanServerGroupDescription)
      converted.credentials = getCredentialsObject(input.credentials as String)
      converted
    }
  }
