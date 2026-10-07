/*
 * Copyright 2026 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAlarmDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.UpsertAlarmAtomicOperation
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import software.amazon.awssdk.services.cloudwatch.model.ComparisonOperator
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import software.amazon.awssdk.services.cloudwatch.model.Statistic
import spock.lang.Shared
import spock.lang.Specification

class UpsertAlarmDescriptionAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertAlarmDescriptionAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertAlarmDescriptionAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(NetflixAmazonCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deserializes wire-format enum strings from a real request body without error"() {
    // Deck/Orca send the CloudWatch wire-format names (e.g. "GreaterThanThreshold"), not the AWS
    // SDK v2 enum constant names (e.g. GREATER_THAN_THRESHOLD). If these fields were typed as the
    // raw v2 enums, Jackson's default enum deserializer would reject this input outright.
    setup:
    def input = [
        region            : "us-west-1",
        name              : "myAlarm",
        alarmDescription  : "a test alarm",
        comparisonOperator: "GreaterThanThreshold",
        evaluationPeriods : 1,
        period            : 60,
        threshold         : 10.5,
        namespace         : "AWS/EC2",
        metricName        : "CPUUtilization",
        statistic         : "SampleCount",
        unit              : "Percent",
        credentials       : "test",
    ]

    when:
    UpsertAlarmDescription description = converter.convertDescription(input)

    then:
    description instanceof UpsertAlarmDescription
    description.comparisonOperator == "GreaterThanThreshold"
    description.statistic == "SampleCount"
    description.unit == "Percent"

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAlarmAtomicOperation

    when: "the description builds the actual AWS request"
    def request = description.buildRequest()

    then: "the wire-format strings resolve to the correct typed v2 enum constants"
    request.comparisonOperator() == ComparisonOperator.GREATER_THAN_THRESHOLD
    request.statistic() == Statistic.SAMPLE_COUNT
    request.unit() == StandardUnit.PERCENT
  }
}
