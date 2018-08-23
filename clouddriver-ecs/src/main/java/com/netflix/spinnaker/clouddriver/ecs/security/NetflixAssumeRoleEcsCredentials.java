/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;

public class NetflixAssumeRoleEcsCredentials extends NetflixECSCredentials {
  private final String assumeRole;
  private final String sessionName;
  private final String awsAccount;

  public NetflixAssumeRoleEcsCredentials(NetflixAssumeRoleAmazonCredentials copy, String awsAccount) {
    super(copy);
    this.assumeRole = copy.getAssumeRole();
    this.sessionName = copy.getSessionName();
    this.awsAccount = awsAccount;
  }

  public String getAssumeRole() {
    return assumeRole;
  }

  public String getSessionName() {
    return sessionName;
  }

  public String getAwsAccount() {
    return awsAccount;
  }
}
