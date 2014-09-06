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

package com.netflix.spinnaker.mort.config

import com.netflix.spinnaker.amos.aws.NetflixAssumeRoleAamzonCredentials
import groovy.transform.CompileStatic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@CompileStatic
@Component
@ConfigurationProperties("aws")
class AwsConfigurationProperties {
  String assumeRole
  // This is the IAM Role that Mort will operate under
  String accountIamRole
  // These are accounts that have been configured with permissions under the above assumeRole for Kato to perform operations
  List<NetflixAssumeRoleAamzonCredentials> accounts
}
