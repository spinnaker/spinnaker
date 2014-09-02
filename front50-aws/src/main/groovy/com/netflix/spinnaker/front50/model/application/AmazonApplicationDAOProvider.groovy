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

package com.netflix.spinnaker.front50.model.application

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.netflix.spinnaker.amos.aws.AmazonCredentials
import com.netflix.spinnaker.front50.config.AmazonConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AmazonApplicationDAOProvider implements ApplicationDAOProvider<AmazonCredentials> {

  @Autowired
  AmazonConfig.AwsConfigurationProperties awsConfigurationProperties

  @Override
  boolean supports(Class<?> credentialsClass) {
    AmazonCredentials.isAssignableFrom(credentialsClass)
  }

  @Override
  ApplicationDAO getForAccount(AmazonCredentials credentials) {
    def simpledb = new AmazonSimpleDBClient(credentials.credentials as AWSCredentials)
    new AmazonApplicationDAO(awsSimpleDBClient: simpledb, domain: awsConfigurationProperties.defaultSimpleDBDomain)
  }
}
