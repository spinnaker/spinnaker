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


package com.netflix.spinnaker.kato.config

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.flow.ActivityWorker
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.deploy.DeploymentActivitiesImpl
import com.netflix.spinnaker.kato.security.NamedAccountCredentials
import com.netflix.spinnaker.kato.security.NamedAccountCredentialsHolder
import com.netflix.spinnaker.kato.security.aws.RefreshingCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.annotation.PostConstruct

@ConditionalOnExpression('${swf.enabled:false}')
@Configuration
class KatoSwfConfig {

  @Autowired
  DeploymentActivitiesImpl deploymentActivitiesImpl

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  NamedAccountCredentialsHolder namedAccountCredentialsHolder

  @Value('${swf.default.account:test}')
  String swfDefaultAccount

  @Value('${swf.default.region:us-east-1}')
  String swfDefaultRegion


  @Value('${swf.domain:asgard}')
  String domain

  @Value('${swf.taskList:lake}')
  String taskList

  @Bean()
  DeploymentActivitiesImpl deploymentActivitiesImpl() {
    new DeploymentActivitiesImpl()
  }

  @PostConstruct
  void activityWorker() {
    def creds = (NamedAccountCredentials<AmazonCredentials>)namedAccountCredentialsHolder.getCredentials(swfDefaultAccount)

    AmazonSimpleWorkflow simpleWorkflow = amazonClientProvider.getAmazonSimpleWorkflow(
      new KatoSwfProviderChain(creds), swfDefaultRegion)

    ActivityWorker activityWorker = new ActivityWorker(simpleWorkflow, domain, taskList)
    activityWorker.addActivitiesImplementations([deploymentActivitiesImpl])
    activityWorker.start()
  }

  static class KatoSwfProviderChain extends AWSCredentialsProviderChain {
    KatoSwfProviderChain(NamedAccountCredentials<AmazonCredentials> account) {
      super([new RefreshingCredentialsProvider(account, 60 * 1000)] as AWSCredentialsProvider[])
    }
  }

}
