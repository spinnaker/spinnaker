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
package com.netflix.spinnaker.clouddriver.aws.lifecycle

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import spock.lang.Specification
import spock.lang.Unroll

class ARNSpec extends Specification {

  def mgmtCredentials = Mock(NetflixAmazonCredentials) {
    getAccountId() >> { return "100" }
    getName() >> { return "mgmt" }
  }

  @Unroll
  void "should extract accountId, region and name from SQS or SNS ARN"() {
    when:
    def parsedARN = new ARN(
      [mgmtCredentials],
      arn,
    )

    then:
    parsedARN.account == mgmtCredentials
    parsedARN.region == expectedRegion
    parsedARN.name == expectedName

    when:
    new ARN([mgmtCredentials], "invalid-arn")

    then:
    def e1 = thrown(IllegalArgumentException)
    e1.message == "invalid-arn is not a valid SNS or SQS ARN"

    when:
    new ARN([], arn)

    then:
    def e2 = thrown(IllegalArgumentException)
    e2.message == "No account credentials found for 100"

    where:
    arn                                   || expectedRegion || expectedName
    "arn:aws:sqs:us-west-2:100:queueName" || "us-west-2"    || "queueName"
    "arn:aws:sns:us-west-2:100:topicName" || "us-west-2"    || "topicName"
  }
}
