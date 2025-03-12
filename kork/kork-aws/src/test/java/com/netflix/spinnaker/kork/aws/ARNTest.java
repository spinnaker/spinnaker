/*
 * Copyright 2021 Salesforce, Inc.
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
package com.netflix.spinnaker.kork.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ARNTest {

  @ParameterizedTest
  @ValueSource(strings = {"aws", "aws-cn", "aws-us-gov", "aws-iso", "aws-iso-b"})
  void awsPartitionARN(String partition) {
    String region = "us-east-2";
    String account = "123456789012";
    String name = "instance/i-1234567890abcdef0";
    String arnString = "arn:" + partition + ":ec2:" + region + ":" + account + ":" + name;
    ARN arn = new ARN(arnString);
    assertEquals(arnString, arn.getArn());
    assertEquals(region, arn.getRegion());
    assertEquals(account, arn.getAccount());
    assertEquals(name, arn.getName());
  }

  @Test
  void invalidPartitionARN() {
    assertThrows(
        IllegalArgumentException.class, () -> new ARN("arnXXX:aws:iam::123456789012:user"));
  }
}
