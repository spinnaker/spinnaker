/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;

public class NetflixSTSAssumeRoleSessionCredentialsProvider
    extends STSAssumeRoleSessionCredentialsProvider {
  private final String accountId;

  public NetflixSTSAssumeRoleSessionCredentialsProvider(
      AWSCredentialsProvider longLivedCredentialsProvider,
      String roleArn,
      String roleSessionName,
      String accountId) {
    super(longLivedCredentialsProvider, roleArn, roleSessionName);
    this.accountId = accountId;

    /**
     * Need to explicitly set sts region if GovCloud or China as per
     * https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/STSAssumeRoleSessionCredentialsProvider.html
     */
    if (roleArn.contains("aws-us-gov")) {
      setSTSClientEndpoint("sts.us-gov-west-1.amazonaws.com");
    }
    if (roleArn.contains("aws-cn")) {
      setSTSClientEndpoint("sts.cn-north-1.amazonaws.com.cn");
    }
  }

  public String getAccountId() {
    return accountId;
  }
}
