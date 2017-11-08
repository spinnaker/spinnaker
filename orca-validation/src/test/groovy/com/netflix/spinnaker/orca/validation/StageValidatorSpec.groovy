/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.validation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class StageValidatorSpec extends Specification {
  def objectMapper = new ObjectMapper()

  @Subject
  def stageValidator = new StageValidator(objectMapper)

  @Unroll
  void "should not raise an exception if schema does not exist"() {
    expect:
    stageValidator.loadSchema(new Stage(Execution.newOrchestration("orca"), type, [:])).isPresent() == expectedIsPresent

    where:
    type             || expectedIsPresent
    "bake"           || true
    "does-not-exist" || false
  }

  void "should apply cloudProvider-specific processing to schema"() {
    given:
    def stageValidator = new StageValidator(objectMapper, "/schemas/test/")

    when:
    def awsSchema = stageValidator.loadSchema(
      new Stage(Execution.newOrchestration("orca"), "dummy", [cloudProvider: "aws"])
    ).get()

    def awsSchemaMap = objectMapper.readValue(
      objectMapper
        .writerWithView(StageValidator.Views.Public.class)
        .writeValueAsString(awsSchema),
      Map
    )

    then:
    awsSchema.@properties.keySet().sort() == ["field1", "field2"].sort()
    awsSchema.required.sort() == ["field1"].sort()

    // verify type has been expanded to include 'string' as well as the explicitly specified value
    awsSchemaMap.properties["field1"] == [
      type: "string"
    ]
    awsSchemaMap.properties["field2"] == [
      anyOf: [
        [type: "boolean"],
        [type: "string"]
      ]
    ]

    when:
    def nonAwsSchema = stageValidator.loadSchema(
      new Stage(Execution.newOrchestration("orca"), "dummy", [cloudProvider: "nonAws"])
    ).get()

    then: "all fields should have been filtered out as cloudProvider != 'aws'"
    nonAwsSchema.@properties.isEmpty()
    nonAwsSchema.required.isEmpty()
  }

  void "should validate bake request against schema"() {
    expect:
    stageValidator.isValid(new Stage(Execution.newOrchestration("orca"), "bake", [
      package           : "mypackage",
      cloudProviderType : "aws",
      baseOs            : "ubuntu",
      enhancedNetworking: '${spelExpressionThatEvaluatesToTrue()}',
      vmType            : "pv",
      regions           : ["us-west-1"]
    ]))

    stageValidator.isValid(new Stage(Execution.newOrchestration("orca"), "bake", [
      package          : "mypackage",
      cloudProviderType: "notAws",
      baseOs           : "ubuntu",
      regions          : ["us-west-1"]
    ]))
  }

  @Unroll
  void "should determine cloud provider for Stage"() {
    given:
    def stage = new Stage(Execution.newOrchestration("orca"), "bake", context)

    expect:
    stageValidator.getCloudProvider(stage).orElse(null) == expectedCloudProvider

    where:
    context                    || expectedCloudProvider
    [:]                        || null
    [cloudProvider: "aws"]     || "aws"
    [cloudProviderType: "aws"] || "aws"
  }
}
