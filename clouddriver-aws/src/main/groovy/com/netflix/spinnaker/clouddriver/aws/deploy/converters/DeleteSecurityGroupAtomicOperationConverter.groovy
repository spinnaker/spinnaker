/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.DeserializationFeature
import com.netflix.spinnaker.clouddriver.aws.AmazonOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.aws.deploy.description.DeleteSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.DeleteSecurityGroupAtomicOperation
import org.springframework.stereotype.Component

@AmazonOperation(AtomicOperations.DELETE_SECURITY_GROUP)
@Component("deleteSecurityGroupDescription")
class DeleteSecurityGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

    @Override
    DeleteSecurityGroupAtomicOperation convertOperation(Map input) {
        new DeleteSecurityGroupAtomicOperation(convertDescription(input))
    }

    @Override
    DeleteSecurityGroupDescription convertDescription(Map input) {
        def converted = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(input, DeleteSecurityGroupDescription)
        converted.credentials = getCredentialsObject(input.credentials as String)
        converted
    }
}
