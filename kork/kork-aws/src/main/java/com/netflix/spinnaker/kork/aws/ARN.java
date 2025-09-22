/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws;

import software.amazon.awssdk.arns.Arn;

/**
 * Util for easy parsing of an Amazon Resource Names (ARNS)
 * https://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html
 */
public class ARN {

  private String arn;
  private String region;
  private String name;
  private String account;

  public ARN(String arn) {
    this.arn = arn;

    Arn awsArn = Arn.fromString(arn);

    this.region = awsArn.region().orElseThrow();
    this.account = awsArn.accountId().orElseThrow();
    this.name = awsArn.resourceAsString();
  }

  public String getArn() {
    return arn;
  }

  public String getRegion() {
    return region;
  }

  public String getName() {
    return name;
  }

  public String getAccount() {
    return account;
  }

  @Override
  public String toString() {
    return "ARN{" + "arn='" + arn + '\'' + '}';
  }
}
