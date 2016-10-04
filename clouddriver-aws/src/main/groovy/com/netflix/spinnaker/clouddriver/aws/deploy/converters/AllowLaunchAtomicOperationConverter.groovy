/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.AllowLaunchAtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component

@Component("allowLaunchDescription")
class AllowLaunchAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {
  @Autowired
  ApplicationContext applicationContext

  @Override
  AllowLaunchAtomicOperation convertOperation(Map input) {
    def op = new AllowLaunchAtomicOperation(convertDescription(input))
    applicationContext?.autowireCapableBeanFactory?.autowireBean(op)
    op
  }

  @Override
  AllowLaunchDescription convertDescription(Map input) {
    def converted = objectMapper.convertValue(input, AllowLaunchDescription)
    converted.credentials = getCredentialsObject(input.credentials as String)
    converted
  }
}
