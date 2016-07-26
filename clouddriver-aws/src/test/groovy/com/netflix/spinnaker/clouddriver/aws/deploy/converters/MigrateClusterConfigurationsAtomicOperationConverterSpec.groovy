/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Shared
import spock.lang.Specification

class MigrateClusterConfigurationsAtomicOperationConverterSpec extends Specification {

  @Shared
  MigrateClusterConfigurationsAtomicOperationConverter converter

  def setupSpec() {
    converter = new MigrateClusterConfigurationsAtomicOperationConverter(objectMapper: new ObjectMapper())
  }

  void 'converts regionMappings to maps if input as string:string'() {
    setup:
    def input = [
      regionMapping: [
        'us-east-1': 'us-west-1',
        'us-west-2': ['eu-west-1': ['eu-west-1a']]
      ]
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description.regionMapping['us-east-1'] == ['us-west-1': []]
    description.regionMapping['us-west-2'] == ['eu-west-1': ['eu-west-1a']]
  }
}
